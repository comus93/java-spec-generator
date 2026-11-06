package com.comus93.specgen.mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.comus93.specgen.config.JobConfig;
import com.comus93.specgen.excel.RowKind;
import com.comus93.specgen.excel.SheetModel;
import com.comus93.specgen.excel.SheetRow;
import com.comus93.specgen.excel.WorkbookModel;
import com.comus93.specgen.facts.JspFacts;
import com.comus93.specgen.scan.SourceJob;

/** Maps source-local JSP facts to the five specification sheets. */
public final class JspSpecMapper {
    public WorkbookModel map(JspFacts facts, JobConfig config, SourceJob job) {
        String filename = job.sourceFile().getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String id = dot > 0 ? filename.substring(0, dot) : filename;
        List<SheetRow> cover = start("프로그램 사양서", "번호", "항목", "값");
        Object[] values = { config.system(), job.module().subsystem(), job.module().moduleName(), id,
                config.author(), date(config, job), config.programName(), "JSP", "JSP", config.overview() };
        String[] labels = { "시스템", "서브 시스템", "모듈", "프로그램 ID", "작성자", "작성일",
                "프로그램명", "개발 유형", "프로그램 유형", "프로그램 개요" };
        for (int i = 0; i < labels.length; i++) {
            cover.add(row(RowKind.DATA, String.format("%02d", i + 1), labels[i], values[i]));
        }

        List<SheetRow> fields = start("화면 필드 정의", "화면 필드", "Object", "참조 필드", "Edit",
                "Remark (속성 원문)", "항목 유형", "다국어 ID");
        for (var field : facts.screenFields()) {
            fields.add(row(RowKind.DATA, field.fieldName(), field.object(), field.referenceField(), null,
                    attributes(field.rawAttributes()), field.fieldType(), field.i18nId()));
        }
        if (facts.screenFields().isEmpty()) fields.add(row(RowKind.DATA, null, null, null, null, null, null, null));

        List<SheetRow> flows = start("JSP 화면 구성 순서", "순번", "태그 / 파일", "속성");
        List<JspFacts.FlowFact> releaseFlows = releaseFlows(facts);
        for (var flow : releaseFlows) {
            flows.add(row(RowKind.DATA, flow.sequence(), flow.tagOrFile(), flowAttributes(flow)));
        }
        if (releaseFlows.isEmpty()) flows.add(row(RowKind.DATA, null, null, null));
        List<SheetRow> path = start("원본 경로", "상대 경로");
        path.add(row(RowKind.DATA, job.relativePath().toString().replace('\\', '/')));

        List<SheetRow> detail = new ArrayList<>();
        detail.add(row(RowKind.TITLE, "상세 사양  |  " + filename));
        detail.add(row(RowKind.EMPTY));
        detail.add(row(RowKind.SECTION, "상수 및 전역 변수"));
        detail.add(row(RowKind.HEADER, "상수/변수명", "Type", "설명", "비고"));
        for (var constant : facts.constants()) {
            detail.add(row(RowKind.DATA, constant.name(), constant.type(), constant.description(), constant.note()));
        }
        addBlankDataRows(detail, 5 - facts.constants().size(), 5);
        detail.add(row(RowKind.SECTION, "Function 및 Event"));
        detail.add(row(RowKind.HEADER, "Type", "Name", "설명", "Return"));
        for (var event : facts.events()) {
            String description = event.description();
            if ("LINK_CLICK".equals(event.type()) && description != null) description = "href=\"" + description + "\"";
            detail.add(row(RowKind.DATA, event.type(), event.name(), description, event.returnValue()));
        }
        addBlankDataRows(detail, 5 - facts.events().size(), 5);
        detail.add(row(RowKind.SECTION, "명세 변경 사항"));
        detail.add(row(RowKind.HEADER, "NO", "Source명", "Source Code", "개발자", "변경 내용"));
        addBlankDataRows(detail, 8, 5);
        return new WorkbookModel(List.of(new SheetModel("01_표제부", cover),
                new SheetModel("02_화면필드정의", fields), new SheetModel("03_최소흐름", flows),
                new SheetModel("04_원본경로", path), new SheetModel("상세 사양", detail)));
    }

    private static LocalDate date(JobConfig config, SourceJob job) {
        if (config.authoringDateOverride() != null) return config.authoringDateOverride();
        try {
            return Files.getLastModifiedTime(job.sourceFile()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static List<JspFacts.FlowFact> releaseFlows(JspFacts facts) {
        List<JspFacts.FlowFact> result = new ArrayList<>();
        for (JspFacts.FlowFact flow : facts.flows()) {
            if (isScreenConstructionFlow(flow)) {
                result.add(new JspFacts.FlowFact(result.size() + 1, flow.line(), flow.nodeKind(),
                        flow.tagOrFile(), flow.attributes(), flow.depth()));
            }
        }
        return result;
    }

    private static boolean isScreenConstructionFlow(JspFacts.FlowFact flow) {
        if ("DIRECTIVE".equals(flow.nodeKind()) || "EL".equals(flow.nodeKind())) {
            return true;
        }
        String tag = flow.tagOrFile();
        if (tag == null || tag.isBlank()) {
            return false;
        }
        if (tag.contains(":")) {
            return true;
        }
        if ("a".equalsIgnoreCase(tag)) {
            return flow.attributes().containsKey("href");
        }
        return "input".equalsIgnoreCase(tag)
                && "submit".equalsIgnoreCase(flow.attributes().get("type"));
    }

    private static String flowAttributes(JspFacts.FlowFact flow) {
        if ("DIRECTIVE".equals(flow.nodeKind()) || "EL".equals(flow.nodeKind())) {
            return "";
        }
        return attributes(flow.attributes());
    }

    private static void addBlankDataRows(List<SheetRow> rows, int count, int width) {
        for (int i = 0; i < Math.max(0, count); i++) {
            rows.add(row(RowKind.DATA, java.util.Collections.nCopies(width, null).toArray()));
        }
    }

    private static String attributes(Map<String, String> attrs) {
        return attrs.entrySet().stream().map(e -> e.getValue() == null ? e.getKey()
                : e.getKey() + "=\"" + e.getValue() + "\"").collect(Collectors.joining("; "));
    }

    private static List<SheetRow> start(String title, Object... headers) {
        List<SheetRow> rows = new ArrayList<>();
        rows.add(row(RowKind.TITLE, title));
        rows.add(row(RowKind.EMPTY));
        rows.add(row(RowKind.HEADER, headers));
        return rows;
    }

    private static SheetRow row(RowKind kind, Object... values) {
        return new SheetRow(kind, Arrays.asList(values));
    }
}
