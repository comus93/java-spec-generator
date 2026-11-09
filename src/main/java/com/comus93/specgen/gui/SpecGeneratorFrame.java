package com.comus93.specgen.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.config.ModuleSelection;
import com.comus93.specgen.config.OutputFormat;
import com.comus93.specgen.config.SourceMode;
import com.comus93.specgen.runner.FileResult;
import com.comus93.specgen.runner.JobRunner;
import com.comus93.specgen.runner.ProgressEvent;
import com.comus93.specgen.runner.RunSummary;

public final class SpecGeneratorFrame extends JFrame {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JobRunner runner;
    private final ModuleTableModel moduleModel = new ModuleTableModel();
    private final List<JComponent> controls = new ArrayList<>();

    private final JRadioButton javaMode = new JRadioButton("Java", true);
    private final JRadioButton jspMode = new JRadioButton("JSP");
    private final JTextField systemField = new JTextField();
    private final JTextField authorField = new JTextField();
    private final JTextField authoringDateField = new JTextField();
    private final JTextField programNameField = new JTextField();
    private final JTextArea overviewArea = new JTextArea(3, 40);
    private final JTextField outputDirectoryField = new JTextField();
    private final JComboBox<OutputFormat> outputFormat = new JComboBox<>(OutputFormat.values());

    private final JTable moduleTable = new JTable(moduleModel);
    private final JButton addFoldersButton = new JButton("+ 폴더 추가");
    private final JButton outputBrowseButton = new JButton("찾아보기...");
    private final JButton generateButton = new JButton("사양서 생성");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea logArea = new JTextArea(8, 80);

    private boolean running;

    public SpecGeneratorFrame() {
        this(new JobRunner());
    }

    public SpecGeneratorFrame(JobRunner runner) {
        super("프로그램 사양서 생성기");
        this.runner = Objects.requireNonNull(runner, "runner");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(buildContent());
        wireEvents();
        setMinimumSize(new Dimension(980, 680));
        setSize(1120, 760);
        setLocationRelativeTo(null);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(buildSettingsPanel(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildModulesPanel(), buildLogPanel());
        split.setResizeWeight(0.62);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        root.add(buildRunPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        JPanel modePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        modePanel.setBorder(BorderFactory.createTitledBorder("분석 유형"));
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(javaMode);
        modeGroup.add(jspMode);
        modePanel.add(javaMode);
        modePanel.add(jspMode);
        panel.add(modePanel, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createTitledBorder("공통 설정"));
        addPair(fields, 0, "시스템", systemField, "작성자", authorField);
        authoringDateField.setToolTipText("선택 입력: yyyy-MM-dd (비우면 원본 파일 수정일 사용)");
        addPair(fields, 1, "작성일 (선택)", authoringDateField, "Excel 형식", outputFormat);
        addWide(fields, 2, "프로그램명", programNameField);
        overviewArea.setLineWrap(true);
        overviewArea.setWrapStyleWord(true);
        JScrollPane overviewScroll = new JScrollPane(overviewArea);
        overviewScroll.setPreferredSize(new Dimension(400, 68));
        addWide(fields, 3, "프로그램 개요", overviewScroll);
        addOutputRow(fields, 4);
        panel.add(fields, BorderLayout.CENTER);

        controls.add(systemField);
        controls.add(authorField);
        controls.add(authoringDateField);
        controls.add(programNameField);
        controls.add(overviewArea);
        controls.add(outputDirectoryField);
        controls.add(outputFormat);
        controls.add(javaMode);
        controls.add(jspMode);
        return panel;
    }

    private static void addPair(JPanel panel, int row, String firstLabel, JComponent firstField,
            String secondLabel, JComponent secondField) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        panel.add(new JLabel(firstLabel), constraints);

        constraints = baseConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.weightx = 0.5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(firstField, constraints);

        constraints = baseConstraints();
        constraints.gridx = 2;
        constraints.gridy = row;
        panel.add(new JLabel(secondLabel), constraints);

        constraints = baseConstraints();
        constraints.gridx = 3;
        constraints.gridy = row;
        constraints.weightx = 0.5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(secondField, constraints);
    }

    private static void addWide(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        panel.add(new JLabel(label), constraints);

        constraints = baseConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, constraints);
    }

    private void addOutputRow(JPanel panel, int row) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        panel.add(new JLabel("출력 폴더"), constraints);

        constraints = baseConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(outputDirectoryField, constraints);

        constraints = baseConstraints();
        constraints.gridx = 3;
        constraints.gridy = row;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(outputBrowseButton, constraints);
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private JPanel buildModulesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("모듈 폴더"));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(new JLabel("Explorer 폴더를 표로 끌어다 놓거나 폴더를 추가하세요."), BorderLayout.WEST);
        header.add(addFoldersButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        moduleTable.setFillsViewportHeight(true);
        moduleTable.setRowHeight(28);
        moduleTable.setAutoCreateRowSorter(false);
        moduleTable.getTableHeader().setReorderingAllowed(false);
        moduleTable.getColumnModel().getColumn(ModuleTableModel.SUBSYSTEM_COLUMN).setPreferredWidth(140);
        moduleTable.getColumnModel().getColumn(ModuleTableModel.MODULE_COLUMN).setPreferredWidth(180);
        moduleTable.getColumnModel().getColumn(ModuleTableModel.SOURCE_FOLDER_COLUMN).setPreferredWidth(500);
        TableColumn removeColumn = moduleTable.getColumnModel().getColumn(ModuleTableModel.REMOVE_COLUMN);
        removeColumn.setMinWidth(48);
        removeColumn.setMaxWidth(64);
        removeColumn.setPreferredWidth(56);
        removeColumn.setCellRenderer(new RemoveButtonRenderer());
        moduleTable.setDropMode(DropMode.ON);

        JScrollPane scrollPane = new JScrollPane(moduleTable);
        moduleTable.setTransferHandler(new FolderDropHandler());
        scrollPane.setTransferHandler(moduleTable.getTransferHandler());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("진행 및 결과"));
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRunPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        progressBar.setStringPainted(true);
        progressBar.setString("0 / 0");
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(generateButton, BorderLayout.EAST);
        return panel;
    }

    private void wireEvents() {
        addFoldersButton.addActionListener(event -> chooseFolders());
        outputBrowseButton.addActionListener(event -> chooseOutputDirectory());
        generateButton.addActionListener(event -> generate());

        systemField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                syncSystem();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                syncSystem();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                syncSystem();
            }
        });

        moduleTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (running || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int row = moduleTable.rowAtPoint(event.getPoint());
                int column = moduleTable.columnAtPoint(event.getPoint());
                if (row >= 0 && column == ModuleTableModel.REMOVE_COLUMN) {
                    moduleModel.removeRow(moduleTable.convertRowIndexToModel(row));
                }
            }
        });
    }

    private void syncSystem() {
        moduleModel.updateSystem(systemField.getText());
    }

    private void chooseFolders() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("소스 폴더 선택");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selected = chooser.getSelectedFiles();
        if (selected.length == 0 && chooser.getSelectedFile() != null) {
            selected = new File[]{chooser.getSelectedFile()};
        }
        addFolders(toPaths(selected));
    }

    private void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("출력 폴더 선택");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            outputDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void addFolders(List<Path> paths) {
        List<Path> folders = new ArrayList<>();
        List<Path> rejected = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                folders.add(path);
            } else {
                rejected.add(path);
            }
        }
        moduleModel.addFolders(folders);
        if (!rejected.isEmpty()) {
            appendLine("폴더가 아니어서 제외했습니다: " + rejected);
        }
    }

    private void generate() {
        if (running) {
            return;
        }
        JobConfig config;
        try {
            config = buildConfig();
        } catch (RuntimeException exception) {
            showError("설정을 확인하세요", concise(exception));
            return;
        }

        running = true;
        setControlsEnabled(false);
        logArea.setText("");
        progressBar.setValue(0);
        progressBar.setString("0 / 0");
        appendLine("모듈 " + config.modules().size() + "개, 출력 폴더: " + config.outputDirectory());

        new SwingWorker<RunSummary, ProgressEvent>() {
            private boolean finished;

            @Override
            protected RunSummary doInBackground() {
                return runner.run(config, this::publish);
            }

            @Override
            protected void process(List<ProgressEvent> events) {
                if (finished) {
                    return;
                }
                for (ProgressEvent event : events) {
                    int percent = event.total() == 0 ? 0
                            : (int) Math.round(event.completed() * 100.0 / event.total());
                    progressBar.setValue(percent);
                    progressBar.setString(event.completed() + " / " + event.total());
                    if (event.status() == FileResult.FileStatus.SUCCESS) {
                        appendLine(statusLabel(event.status()) + " " + event.sourceFile());
                    }
                }
            }

            @Override
            protected void done() {
                finished = true;
                try {
                    RunSummary summary = get();
                    int total = summary.totalCount();
                    progressBar.setValue(total == 0 ? 0 : 100);
                    progressBar.setString(total + " / " + total);
                    appendSummary(summary);
                    if (summary.hasFailures()) {
                        JOptionPane.showMessageDialog(SpecGeneratorFrame.this,
                                "일부 파일을 처리하지 못했습니다. 진행 및 결과를 확인하세요.",
                                "사양서 생성", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    appendLine("작업이 중단되었습니다.");
                } catch (ExecutionException exception) {
                    String message = concise(exception.getCause());
                    appendLine("작업 실패: " + message);
                    showError("사양서 생성 실패", message);
                } finally {
                    running = false;
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    private JobConfig buildConfig() {
        String system = systemField.getText();
        String outputDirectory = outputDirectoryField.getText().trim();
        if (outputDirectory.isEmpty()) {
            throw new IllegalArgumentException("출력 폴더를 선택하세요.");
        }
        List<ModuleSelection> modules = moduleModel.toSelections();
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("분석할 모듈 폴더를 추가하세요.");
        }
        for (ModuleSelection module : modules) {
            if (module.subsystem().isBlank()) {
                throw new IllegalArgumentException("서브 시스템 이름을 입력하세요: " + module.sourceFolder());
            }
            if (module.moduleName().isBlank()) {
                throw new IllegalArgumentException("모듈 이름을 입력하세요: " + module.sourceFolder());
            }
        }

        String dateText = authoringDateField.getText().trim();
        LocalDate authoringDateOverride = null;
        if (!dateText.isEmpty()) {
            try {
                authoringDateOverride = LocalDate.parse(dateText, DATE_FORMAT);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("작성일은 yyyy-MM-dd 형식이어야 합니다: " + dateText);
            }
        }

        return new JobConfig(
                jspMode.isSelected() ? SourceMode.JSP : SourceMode.JAVA,
                system,
                authorField.getText().trim(),
                authoringDateOverride,
                programNameField.getText().trim(),
                overviewArea.getText().trim(),
                Path.of(outputDirectory),
                (OutputFormat) outputFormat.getSelectedItem(),
                modules);
    }

    private void appendSummary(RunSummary summary) {
        appendLine("완료: 총 " + summary.totalCount() + "건, 성공 " + summary.successCount()
                + "건, 경고 " + summary.warningCount() + "건, 실패 " + summary.failureCount() + "건");
        int generated = summary.successCount() + summary.warningCount();
        if (summary.hasFailures() && generated > 0) {
            appendLine("부분 성공: 전체 파일 처리를 완료했으며, 사양서 " + generated
                    + "건은 정상 생성되었습니다. 실패 " + summary.failureCount()
                    + "건은 아래 원본 코드 및 오류 위치를 확인하세요.");
            appendLine("생성 결과 폴더: " + outputDirectoryField.getText().trim());
        }
        for (FileResult result : summary.results()) {
            if (result.status() == FileResult.FileStatus.SUCCESS) {
                continue;
            }
            appendLine(diagnosticLine(result));
        }
        if (summary.logFile() != null) {
            appendLine("상세 로그: " + summary.logFile());
        }
        if (!summary.logError().isEmpty()) {
            appendLine("로그 저장 오류: " + singleLine(summary.logError(), 240));
        }
    }

    private void appendLine(String text) {
        logArea.append(text + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setControlsEnabled(boolean enabled) {
        for (JComponent control : controls) {
            control.setEnabled(enabled);
        }
        moduleTable.setEnabled(enabled);
        addFoldersButton.setEnabled(enabled);
        outputBrowseButton.setEnabled(enabled);
        generateButton.setEnabled(enabled);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private static List<Path> toPaths(File[] files) {
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            if (file != null) {
                paths.add(file.toPath());
            }
        }
        return paths;
    }

    private static final Pattern DIAGNOSTIC_LOCATION = Pattern.compile(
            "(?i)\\bline\\s+(\\d+)\\s*,\\s*column\\s+(\\d+)|\\b(\\d+):(\\d+)\\b");

    static String diagnosticLine(FileResult result) {
        String message = result.message();
        Matcher location = DIAGNOSTIC_LOCATION.matcher(message);
        String position = "";
        if (location.find()) {
            position = " " + (location.group(1) != null
                    ? location.group(1) + ":" + location.group(2)
                    : location.group(3) + ":" + location.group(4));
        }
        Path filename = result.sourceFile().getFileName();
        String name = singleLine(filename == null ? result.sourceFile().toString() : filename.toString(), 48);
        String code = singleLine(result.errorCode(), 32);
        String reason = switch (result.errorCode()) {
            case "PARSE_ERROR" -> result.status() == FileResult.FileStatus.WARNING
                    ? "구문 분석 실패, 부분 복구" : "구문 분석 실패";
            case "READ_ERROR" -> "원본 읽기 실패";
            case "WRITE_ERROR" -> "결과 저장 실패";
            case "DATE_METADATA_ERROR" -> "날짜 정보 오류";
            case "PROCESSING_ERROR" -> "처리 오류";
            default -> result.status() == FileResult.FileStatus.WARNING ? "처리 경고" : "처리 실패";
        };
        return statusLabel(result.status()) + " " + name + position + " [" + code + "] " + reason;
    }

    private static String singleLine(String value, int maxLength) {
        String text = value.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private static String statusLabel(FileResult.FileStatus status) {
        return switch (status) {
            case SUCCESS -> "성공";
            case WARNING -> "경고";
            case FAILED -> "실패";
        };
    }

    private static String concise(Throwable throwable) {
        if (throwable == null) {
            return "알 수 없는 오류";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("\\R+", " ").strip();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240) + "...";
    }

    private final class FolderDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return !running && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (!(data instanceof List<?> files)) {
                    return false;
                }
                List<Path> folders = new ArrayList<>();
                for (Object file : files) {
                    if (file instanceof File candidate && candidate.isDirectory()) {
                        folders.add(candidate.toPath());
                    }
                }
                if (folders.isEmpty()) {
                    appendLine("폴더만 추가할 수 있습니다.");
                    return false;
                }
                moduleModel.addFolders(folders);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                appendLine("폴더를 읽을 수 없습니다: " + concise(exception));
                return false;
            }
        }
    }

    private static final class RemoveButtonRenderer extends JButton implements TableCellRenderer {
        private RemoveButtonRenderer() {
            super("X");
            setMargin(new Insets(1, 4, 1, 4));
            setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            setEnabled(table.isEnabled());
            return this;
        }
    }
}
