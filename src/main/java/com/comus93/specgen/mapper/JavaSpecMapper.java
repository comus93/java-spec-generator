package com.comus93.specgen.mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.excel.RowKind;
import com.comus93.specgen.excel.SheetModel;
import com.comus93.specgen.excel.SheetRow;
import com.comus93.specgen.excel.WorkbookModel;
import com.comus93.specgen.facts.JavaFacts;
import com.comus93.specgen.facts.JavaFacts.MethodFact;
import com.comus93.specgen.facts.JavaFacts.StepFact;
import com.comus93.specgen.scan.SourceJob;

/** Converts extracted source facts to the approved Java specification sheets. */
public final class JavaSpecMapper {
    public WorkbookModel map(JavaFacts facts, JobConfig config, SourceJob job) {
        List<SheetModel> sheets = new ArrayList<>();
        String fileName = job.sourceFile().getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String programId = extension < 0 ? fileName : fileName.substring(0, extension);
        sheets.add(sheet("01_표제부", "프로그램 사양서", cells("번호", "항목", "값"), List.of(
                cells("01", "시스템", config.system()),
                cells("02", "서브 시스템", job.module().subsystem()),
                cells("03", "모듈", job.module().moduleName()),
                cells("04", "프로그램 ID", programId),
                cells("05", "작성자", config.author()),
                cells("06", "작성일", authoringDate(config, job.sourceFile())),
                cells("07", "프로그램명", config.programName()),
                cells("08", "개발 유형", "Java"),
                cells("09", "프로그램 유형", facts.typeKind()),
                cells("10", "프로그램 개요", config.overview()))));

        Set<String> overloadedMethods = facts.methods().stream()
                .collect(Collectors.groupingBy(MethodFact::name, Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(entry -> entry.getKey()).collect(Collectors.toCollection(HashSet::new));
        sheets.add(sheet("02_OBJ정의", "OBJ 선언 · 정의",
                cells("선언 종류", "OBJ명", "선언 타입", "수식어", "수명", "초기화 원문", "소속"),
                facts.variables().stream().map(v -> cells(v.kind(), v.name(), v.type(), v.modifiers(),
                        v.lifetime(), v.initializer(), ownerName(v.owner(), overloadedMethods))).toList()));

        // Golden lists methods by name; stable sorting retains source order for overloads.
        List<MethodFact> methods = facts.methods().stream()
                .sorted(Comparator.comparing(MethodFact::name)).toList();
        sheets.add(sheet("03_메서드정의", "메서드 정의",
                cells("메서드명", "메서드 ID(인자)", "파라미터 타입", "반환 타입"),
                methods.stream().map(m -> cells(m.name(), m.signature(),
                        m.parameters().stream().map(p -> p.type()).collect(Collectors.joining(",")),
                        m.returnType())).toList()));

        List<List<Object>> steps = new ArrayList<>();
        for (MethodFact method : methods) {
            for (StepFact step : method.steps()) {
                steps.add(cells(method.signature(), step.sequence(), step.kind(), step.branchPath(),
                        step.sourceSnippet(), step.targetId()));
            }
        }
        sheets.add(sheet("04_처리단계", "메서드 처리 단계",
                cells("소속 메서드", "순번", "단계 유형", "분기 경로", "구현 예시", "호출 대상 ID"), steps));
        sheets.add(sheet("05_호출관계", "호출관계", cells("호출자 메서드", "호출 메서드", "대상 ID"),
                facts.calls().stream().map(c -> cells(c.callerName(), c.calledName(), c.targetId())).toList()));

        int number = 6;
        if (!facts.staticInitializations().isEmpty()) {
            sheets.add(sheet("06_정적초기화", "정적 초기화", cells("대상 필드", "소스 구분", "초기화 표현"),
                    facts.staticInitializations().stream()
                            .map(s -> cells(s.targetField(), s.sourceKind(), s.initializer())).toList()));
            number++;
        }
        sheets.add(sheet("0" + number++ + "_타입참조", "타입참조", cells("참조 종류", "타입명", "resolvedClassId"),
                facts.typeRefs().stream().map(t -> cells(t.kind(), t.name(), t.resolvedClassId())).toList()));
        sheets.add(sheet("0" + number++ + "_Import", "Import 선언", cells("타입 단축명", "Import 경로"),
                facts.imports().stream().map(i -> cells(i.shortName(), i.importPath())).toList()));
        sheets.add(sheet("0" + number + "_원본경로", "원본 경로", cells("상대 경로"),
                List.of(cells(job.relativePath().toString().replace('\\', '/')))));
        sheets.add(sheet("상세 사양", "상세 사양  |  " + fileName,
                cells("NAME", "VISIBILITY", "LEVEL", "DESCRIPTION", "DETAIL (PSEUDO CODE)",
                        "Input Parameter", "Return Type"),
                methods.stream().map(m -> cells(m.name(), m.visibility(), null, m.javadoc(), detail(m),
                        m.parameters().stream().map(p -> p.type() + " " + p.name())
                                .collect(Collectors.joining(", ")), m.returnType())).toList()));
        return new WorkbookModel(sheets);
    }

    private static String ownerName(String owner, Set<String> overloadedMethods) {
        if (owner == null) {
            return null;
        }
        int parameters = owner.indexOf('(');
        if (parameters < 0) {
            return owner;
        }
        String baseName = owner.substring(0, parameters);
        return overloadedMethods.contains(baseName) ? owner : baseName;
    }

    private static LocalDate authoringDate(JobConfig config, Path source) {
        if (config.authoringDateOverride() != null) return config.authoringDateOverride();
        try {
            return Files.getLastModifiedTime(source).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException | SecurityException exception) {
            // Task 10 collects the corresponding file metadata warning in JobRunner.
            return null;
        }
    }

    private static String detail(MethodFact method) {
        return method.steps().stream().map(step -> {
            String token = switch (step.kind().toUpperCase(Locale.ROOT)) {
                case "INSTANTIATE" -> "CREATE";
                case "ASSIGNMENT" -> "SET";
                case "BRANCH" -> "IF";
                default -> step.kind().toUpperCase(Locale.ROOT);
            };
            String branch = step.branchPath().isEmpty() ? "" : " [" + step.branchPath() + "]";
            // Source snippets are untouched, including strings that resemble location markers.
            return (branch.isEmpty() ? "" : "  ") + token + branch + "  " + step.sourceSnippet();
        }).collect(Collectors.joining("\n"));
    }

    private static SheetModel sheet(String name, String title, List<Object> headers, List<List<Object>> data) {
        List<SheetRow> rows = new ArrayList<>();
        rows.add(new SheetRow(RowKind.TITLE, cells(title)));
        rows.add(new SheetRow(RowKind.EMPTY, List.of()));
        rows.add(new SheetRow(RowKind.HEADER, headers));
        if (data.isEmpty()) {
            rows.add(new SheetRow(RowKind.DATA, Collections.nCopies(headers.size(), null)));
        } else {
            data.forEach(values -> addDataRows(rows, values));
        }
        return new SheetModel(name, rows);
    }

    private static void addDataRows(List<SheetRow> rows, List<Object> values) {
        final int limit = 32767;
        List<List<String>> fragments = null;
        int rowCount = 1;
        int markerColumn = -1;
        for (int column = 0; column < values.size(); column++) {
            Object value = values.get(column);
            if (value instanceof String text && text.length() > limit) {
                if (fragments == null) {
                    fragments = new ArrayList<>(values.size());
                    fragments.addAll(Collections.nCopies(column, null));
                }
                List<String> parts = new ArrayList<>();
                int start = 0;
                while (start < text.length()) {
                    int end = Math.min(start + limit, text.length());
                    if (end < text.length()) {
                        int newline = text.lastIndexOf('\n', end - 1);
                        if (newline >= start) {
                            end = newline + 1;
                        } else if (Character.isHighSurrogate(text.charAt(end - 1))
                                && Character.isLowSurrogate(text.charAt(end))) {
                            end--;
                        }
                    }
                    parts.add(text.substring(start, end));
                    start = end;
                }
                fragments.add(parts);
                rowCount = Math.max(rowCount, parts.size());
            } else {
                if (fragments != null) fragments.add(null);
                if (markerColumn < 0) markerColumn = column;
            }
        }
        if (rowCount == 1) {
            rows.add(new SheetRow(RowKind.DATA, values));
            return;
        }
        for (int row = 0; row < rowCount; row++) {
            List<Object> cells = new ArrayList<>(Collections.nCopies(values.size(), null));
            for (int column = 0; column < values.size(); column++) {
                List<String> parts = fragments.get(column);
                if (parts != null && row < parts.size()) {
                    cells.set(column, parts.get(row));
                } else if (parts == null && row == 0) {
                    cells.set(column, values.get(column));
                }
            }
            if (row > 0) {
                if (markerColumn < 0) cells.add("↳ 계속");
                else cells.set(markerColumn, "↳ 계속");
            }
            rows.add(new SheetRow(RowKind.DATA, cells));
        }
    }

    private static List<Object> cells(Object... values) {
        return Arrays.asList(values);
    }
}
