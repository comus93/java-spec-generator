package com.comus93.specgen.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.comus93.specgen.config.OutputFormat;

/**
 * Writes a complete workbook into a sibling temporary file before replacing
 * the destination. All source text is written as STRING, never as FORMULA.
 */
public final class PoiWorkbookWriter {
    private static final int MAX_CELL_TEXT = 32767;
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("windows");

    public void write(WorkbookModel model, Path destination, OutputFormat format) throws IOException {
        write(model, destination, format, null);
    }

    /**
     * Passing the configured outputRoot is recommended for jobs: it protects
     * against path escape even when a directory changes after initial scanning.
     */
    public void write(WorkbookModel model, Path destination, OutputFormat format, Path outputRoot)
            throws IOException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(format, "format");
        Path target = destination.toAbsolutePath().normalize();
        String extension = format == OutputFormat.XLS ? ".xls" : ".xlsx";
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IllegalArgumentException("Output extension does not match " + format + ": " + target);
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output destination has no parent: " + target);
        }

        validateDestination(target, outputRoot);
        validateModel(model, format);
        Files.createDirectories(parent);
        validateDestination(target, outputRoot);

        Path temporary = Files.createTempFile(parent, ".specgen-", ".tmp");
        try {
            try (Workbook workbook = format == OutputFormat.XLS ? new HSSFWorkbook() : new XSSFWorkbook()) {
                Styles styles = new Styles(workbook);
                for (SheetModel sheetModel : model.sheets()) {
                    render(workbook, sheetModel, styles);
                }
                try (OutputStream stream = Files.newOutputStream(temporary)) {
                    workbook.write(stream);
                }
            }
            validateDestination(target, outputRoot);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateModel(WorkbookModel model, OutputFormat format) {
        int maxRows = format == OutputFormat.XLS ? 65536 : 1048576;
        int maxColumns = format == OutputFormat.XLS ? 256 : 16384;
        Set<String> names = new HashSet<>();
        for (SheetModel sheet : model.sheets()) {
            if (!names.add(sheet.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate sheet name: " + sheet.name());
            }
            if (!WorkbookUtilName.isValid(sheet.name())) {
                throw new IllegalArgumentException("Invalid sheet name: " + sheet.name());
            }
            if (sheet.rows().size() < 2 || sheet.rows().get(0).role() != RowKind.TITLE
                    || sheet.rows().get(1).role() != RowKind.EMPTY) {
                throw new IllegalArgumentException("Sheet requires TITLE at row 1 and EMPTY at row 2: "
                        + sheet.name());
            }
            long physicalRows = (long) sheet.rows().size() + (sheet.rows().size() > 2 ? 1 : 0);
            if (physicalRows > maxRows) {
                String advice = format == OutputFormat.XLS
                        ? "Use XLSX or split the output."
                        : "Split the output.";
                throw new IllegalArgumentException(format + " row limit exceeded in sheet '" + sheet.name()
                        + "': " + physicalRows + " rows including Golden spacer row; limit is "
                        + maxRows + ". " + advice);
            }
            for (SheetRow row : sheet.rows()) {
                if (row.values().size() > maxColumns) {
                    throw new IllegalArgumentException(format + " column limit exceeded in " + sheet.name());
                }
                for (Object value : row.values()) {
                    if (value instanceof String text && text.length() > MAX_CELL_TEXT) {
                        throw new IllegalArgumentException("Excel cell text limit exceeded in " + sheet.name());
                    }
                }
            }
        }
    }

    private static void render(Workbook workbook, SheetModel sheetModel, Styles styles) {
        Sheet sheet = workbook.createSheet(sheetModel.name());
        int width = sheetModel.rows().stream().mapToInt(row -> row.values().size()).max().orElse(1);
        width = Math.max(width, sheetModel.name().equals("01_표제부") ? 6 : 1);
        double[] goldenWidths = goldenWidths(sheetModel.name(), width);
        for (int i = 0; i < sheetModel.rows().size(); i++) {
            SheetRow definition = sheetModel.rows().get(i);
            int physicalRow = i >= 2 ? i + 1 : i; // Golden leaves physical row 3 blank.
            Row row = sheet.createRow(physicalRow);
            if (definition.role() == RowKind.EMPTY) {
                row.setHeightInPoints(9);
                for (int j = 0; j < width; j++) {
                    row.createCell(j).setCellStyle(styles.spacer);
                }
                if (width > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(physicalRow, physicalRow, 0, width - 1));
                }
                continue;
            }
            row.setHeightInPoints(estimatedHeight(sheetModel.name(), definition, goldenWidths));
            int cells = width;
            for (int j = 0; j < cells; j++) {
                Cell cell = row.createCell(j);
                CellStyle style = styles.forRole(definition.role());
                if (sheetModel.name().equals("01_표제부") && physicalRow == 3 && j >= 3 && j <= 5) {
                    style = styles.coverBand;
                }
                cell.setCellStyle(style);
                if (j < definition.values().size()) {
                    setValue(cell, definition.values().get(j));
                    if (definition.values().get(j) instanceof LocalDate) {
                        cell.setCellStyle(styles.date);
                    }
                }
            }
            if ((definition.role() == RowKind.TITLE || definition.role() == RowKind.SECTION) && width > 1) {
                sheet.addMergedRegion(new CellRangeAddress(physicalRow, physicalRow, 0, width - 1));
            }
        }
        for (int j = 0; j < width; j++) {
            double desired = goldenWidths == null || j >= goldenWidths.length
                    ? (j == 0 ? 25 : 30) : goldenWidths[j];
            sheet.setColumnWidth(j, (int) Math.round(Math.min(255, desired) * 256));
        }
        sheet.setDisplayGridlines(false);
    }

    /**
     * POI does not auto-size row heights for wrapped cells. Estimate needed
     * lines using the Golden column width, preserving long pseudo-code blocks.
     * Excel's maximum row height is 409 points.
     */
    private static float estimatedHeight(String sheetName, SheetRow row, double[] widths) {
        if (row.role() == RowKind.TITLE) {
            return sheetName.equals("01_표제부") ? 48f : 42f;
        }
        if (row.role() == RowKind.HEADER || row.role() == RowKind.SECTION) {
            return 30f;
        }
        float minimum = sheetName.equals("01_표제부") ? 30f : 29.1f;
        int maxLines = 1;
        for (int col = 0; col < row.values().size(); col++) {
            Object value = row.values().get(col);
            if (!(value instanceof String text) || text.isEmpty()) {
                continue;
            }
            double width = widths != null && col < widths.length ? widths[col] : (col == 0 ? 25 : 30);
            int charactersPerLine = Math.max(6, (int) Math.floor(width * 0.88));
            int lines = 0;
            for (String physicalLine : text.split("\\R", -1)) {
                lines += Math.max(1, (physicalLine.length() + charactersPerLine - 1) / charactersPerLine);
            }
            maxLines = Math.max(maxLines, lines);
        }
        return Math.min(409f, Math.max(minimum, 10f + maxLines * 15f));
    }

    private static double[] goldenWidths(String sheetName, int columns) {
        return switch (sheetName) {
            case "01_표제부" -> new double[] {10, 23, 65, 5, 10, 10};
            case "02_OBJ정의" -> new double[] {16, 21, 24, 20, 15, 68, 39};
            case "03_메서드정의" -> new double[] {27, 60, 42, 20};
            case "04_처리단계" -> new double[] {56, 10, 20, 30, 68, 68};
            case "05_호출관계" -> new double[] {61, 27, 68};
            case "06_정적초기화" -> new double[] {25, 30, 68};
            case "06_타입참조", "07_타입참조" -> new double[] {20, 26, 68};
            case "07_Import", "08_Import" -> new double[] {28, 68};
            case "08_원본경로", "09_원본경로" -> new double[] {58.625};
            case "02_화면필드정의" -> new double[] {25, 18, 23, 12, 68, 24, 19};
            case "03_최소흐름" -> new double[] {12, 42, 68};
            case "04_원본경로" -> new double[] {42.625};
            case "상세 사양" -> columns == 7
                    ? new double[] {27, 15, 12, 29, 78, 50, 18}
                    : new double[] {27, 27, 62, 27, 39};
            default -> null;
        };
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof String text) {
            cell.setCellValue(text);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
        } else if (value instanceof Integer integer) {
            cell.setCellValue(integer.doubleValue());
        } else if (value instanceof Long number) {
            cell.setCellValue(number.doubleValue());
        } else {
            throw new IllegalArgumentException("Unsupported Excel cell value type");
        }
    }

    private static void validateDestination(Path target, Path outputRoot) throws IOException {
        rejectReparsePoints(target);
        Path canonicalParent = canonicalAllowMissing(target.getParent());
        if (outputRoot != null) {
            Path canonicalRoot = canonicalAllowMissing(outputRoot);
            if (!isWithin(canonicalParent, canonicalRoot)) {
                throw new IllegalArgumentException("Output path escapes configured output directory: " + target);
            }
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Output destination is not a regular file: " + target);
        }
    }

    private static void rejectReparsePoints(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attr = Files.readAttributes(cursor, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (attr.isSymbolicLink() || attr.isOther()) {
                    throw new IllegalArgumentException("Output path contains a link or reparse point: " + cursor);
                }
            }
        }
    }

    private static Path canonicalAllowMissing(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute;
        Deque<Path> missing = new ArrayDeque<>();
        while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(cursor.getFileName());
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            return absolute;
        }
        Path resolved = cursor.toRealPath();
        for (Path part : missing) {
            resolved = resolved.resolve(part);
        }
        return resolved.normalize();
    }

    private static boolean isWithin(Path candidate, Path root) {
        Path cursor = candidate;
        while (cursor != null) {
            if (cursor.equals(root) || (WINDOWS && cursor.toString().equalsIgnoreCase(root.toString()))) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    // Do not auto-fix invalid names; a mapper bug must be visible to callers.
    private static final class WorkbookUtilName {
        static boolean isValid(String name) {
            return name.length() <= 31 && !name.matches(".*[\\\\/\\?\\*\\[\\]:].*")
                    && !name.startsWith("'") && !name.endsWith("'");
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle data;
        final CellStyle coverBand;
        final CellStyle date;
        final CellStyle spacer;

        Styles(Workbook workbook) {
            title = make(workbook, IndexedColors.GREY_80_PERCENT, IndexedColors.WHITE, true, 18);
            section = make(workbook, IndexedColors.GREY_50_PERCENT, IndexedColors.WHITE, true, 10);
            header = make(workbook, IndexedColors.GREY_50_PERCENT, IndexedColors.WHITE, true, 10);
            data = make(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, 11);
            coverBand = make(workbook, IndexedColors.GREY_50_PERCENT, IndexedColors.WHITE, true, 10);
            spacer = make(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, 9);
            spacer.setBorderTop(BorderStyle.NONE);
            spacer.setBorderBottom(BorderStyle.NONE);
            spacer.setBorderLeft(BorderStyle.NONE);
            spacer.setBorderRight(BorderStyle.NONE);
            date = workbook.createCellStyle();
            date.cloneStyleFrom(data);
            date.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        }

        private static CellStyle make(Workbook workbook, IndexedColors fill, IndexedColors ink,
                boolean bold, int fontSize) {
            CellStyle style = workbook.createCellStyle();
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFillForegroundColor(fill.getIndex());
            style.setWrapText(true);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
            Font font = workbook.createFont();
            font.setFontName("Carlito");
            font.setFontHeightInPoints((short) fontSize);
            font.setBold(bold);
            font.setColor(ink.getIndex());
            style.setFont(font);
            return style;
        }

        CellStyle forRole(RowKind role) {
            return switch (role) {
                case TITLE -> title;
                case SECTION -> section;
                case HEADER -> header;
                case DATA -> data;
                case EMPTY -> throw new IllegalArgumentException("EMPTY must not be rendered");
            };
        }
    }
}
