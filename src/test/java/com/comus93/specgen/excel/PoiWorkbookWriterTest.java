package com.comus93.specgen.excel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comus93.specgen.config.OutputFormat;

class PoiWorkbookWriterTest {
    @TempDir Path temp;

    private WorkbookModel sample() {
        return new WorkbookModel(List.of(
            new SheetModel("01_표제부", List.of(
                new SheetRow(RowKind.TITLE, List.of("프로그램 사양서")),
                new SheetRow(RowKind.EMPTY, List.of()),
                new SheetRow(RowKind.HEADER, List.of("번호", "항목", "값")),
                new SheetRow(RowKind.DATA, List.of("01", "시스템", "SYS")),
                new SheetRow(RowKind.DATA, List.of("02", "작성일", LocalDate.of(2026, 11, 5)))
            )),
            new SheetModel("상세 사양", List.of(
                new SheetRow(RowKind.TITLE, List.of("상세 사양")),
                new SheetRow(RowKind.EMPTY, List.of()),
                new SheetRow(RowKind.SECTION, List.of("Function 및 Event")),
                new SheetRow(RowKind.HEADER, List.of("NAME", "VISIBILITY", "LEVEL", "DETAIL")),
                new SheetRow(RowKind.DATA, List.of("run", "", 3, "=SUM(1,1)"))
            ))
        ));
    }

    @Test
    void writesBothExcelFormatsWithCorrectCellTypesAndMonochromeStyling() throws Exception {
        PoiWorkbookWriter writer = new PoiWorkbookWriter();
        for (OutputFormat format : OutputFormat.values()) {
            String suffix = format == OutputFormat.XLS ? ".xls" : ".xlsx";
            Path output = temp.resolve(format.name()).resolve("result" + suffix);
            writer.write(sample(), output, format);
            try (Workbook wb = WorkbookFactory.create(output.toFile())) {
                assertEquals(2, wb.getNumberOfSheets());
                Sheet cover = wb.getSheetAt(0);
                assertEquals("프로그램 사양서", cover.getRow(0).getCell(0).getStringCellValue());
                assertEquals(10 * 256, cover.getColumnWidth(0));
                assertEquals(23 * 256, cover.getColumnWidth(1));
                assertEquals(65 * 256, cover.getColumnWidth(2));
                assertEquals(48.0f, cover.getRow(0).getHeightInPoints());
                assertEquals(9.0f, cover.getRow(1).getHeightInPoints());
                assertEquals(CellType.BLANK, cover.getRow(1).getCell(0).getCellType());
                assertNull(cover.getRow(2), "Golden row 3 is a visual spacer");
                assertEquals("번호", cover.getRow(3).getCell(0).getStringCellValue());
                assertEquals(30.0f, cover.getRow(4).getHeightInPoints());
                assertEquals(2026, cover.getRow(5).getCell(2).getLocalDateTimeCellValue().getYear());
                assertEquals(CellType.NUMERIC, cover.getRow(5).getCell(2).getCellType());
                for (int col = 3; col <= 5; col++) {
                    Cell cell = cover.getRow(3).getCell(col);
                    assertNotNull(cell, "D:F row 4 cover band: " + col);
                    assertTrue(cell.getCellStyle().getFillPattern() != FillPatternType.NO_FILL);
                    assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
                }
                Sheet details = wb.getSheetAt(1);
                assertEquals("Function 및 Event", details.getRow(3).getCell(0).getStringCellValue());
                assertEquals(CellType.NUMERIC, details.getRow(5).getCell(2).getCellType());
                assertEquals(3, (int) details.getRow(5).getCell(2).getNumericCellValue());
                Cell formulaLikeText = details.getRow(5).getCell(3);
                assertEquals(CellType.STRING, formulaLikeText.getCellType());
                assertEquals("=SUM(1,1)", formulaLikeText.getStringCellValue());
                for (Sheet sheet : wb) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            assertTrue(cell.getCellStyle().getWrapText());
                            if (row.getRowNum() >= 3) {
                                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderTop());
                                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderBottom());
                                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderLeft());
                                assertEquals(BorderStyle.THIN, cell.getCellStyle().getBorderRight());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void overwritesTargetWithoutDeletingUnrelatedFiles() throws Exception {
        Path output = temp.resolve("nested/result.xls");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "previous file");
        Path keep = output.getParent().resolve("keep.txt");
        Files.writeString(keep, "keep");
        new PoiWorkbookWriter().write(sample(), output, OutputFormat.XLS);
        try (Workbook wb = WorkbookFactory.create(output.toFile())) {
            assertEquals(2, wb.getNumberOfSheets());
        }
        assertEquals("keep", Files.readString(keep));
        try (var paths = Files.list(output.getParent())) {
            assertEquals(2, paths.count(), "no leftover temporary files");
        }
    }

    @Test
    void expandsLongDetailRowsSoWrappedSourceTextRemainsReadable() throws Exception {
        WorkbookModel longDetail = new WorkbookModel(List.of(new SheetModel("상세 사양", List.of(
            new SheetRow(RowKind.TITLE, List.of("상세 사양")),
            new SheetRow(RowKind.EMPTY, List.of()),
            new SheetRow(RowKind.HEADER, List.of("Type", "Name", "Detail", "Remark", "Return")),
            new SheetRow(RowKind.DATA, List.of("METHOD", "run", "CALL foo();\\n".repeat(55), "", ""))
        ))));
        Path output = temp.resolve("long-detail.xlsx");
        new PoiWorkbookWriter().write(longDetail, output, OutputFormat.XLSX);
        try (Workbook wb = WorkbookFactory.create(output.toFile())) {
            assertTrue(wb.getSheetAt(0).getRow(4).getHeightInPoints() >= 120,
                    "A long pseudo-code detail must not be clipped at the default row height");
        }
    }

    @Test
    void rejectsHssfLogicalRowsAtThePhysicalSpacerBoundary() {
        List<SheetRow> rows = new ArrayList<>();
        rows.add(new SheetRow(RowKind.TITLE, List.of("Title")));
        rows.add(new SheetRow(RowKind.EMPTY, List.of()));
        for (int i = 0; i < 65534; i++) {
            rows.add(new SheetRow(RowKind.DATA, List.of()));
        }
        WorkbookModel boundary = new WorkbookModel(List.of(new SheetModel("Data", rows)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new PoiWorkbookWriter().write(boundary, temp.resolve("boundary.xls"), OutputFormat.XLS));
        assertTrue(error.getMessage().contains("row limit"),
                "Check the additional physical spacer row before POI throws");
    }

    @Test
    void rejectsOversizedCellWithoutCorruptingExistingFile() throws Exception {
        Path output = temp.resolve("existing.xls");
        Files.writeString(output, "old");
        WorkbookModel tooLong = new WorkbookModel(List.of(new SheetModel("Data", List.of(
            new SheetRow(RowKind.TITLE, List.of("Title")),
            new SheetRow(RowKind.EMPTY, List.of()),
            new SheetRow(RowKind.DATA, List.of("x".repeat(32768)))
        ))));
        assertThrows(IllegalArgumentException.class,
            () -> new PoiWorkbookWriter().write(tooLong, output, OutputFormat.XLS));
        assertEquals("old", Files.readString(output));
        try (var paths = Files.list(temp)) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void rejectsHssfRowOverflowWithoutSilentlyTruncating() {
        List<SheetRow> rows = new ArrayList<>();
        rows.add(new SheetRow(RowKind.TITLE, List.of("Title")));
        rows.add(new SheetRow(RowKind.EMPTY, List.of()));
        for (int i = 0; i < 65535; i++) {
            rows.add(new SheetRow(RowKind.DATA, List.of()));
        }
        WorkbookModel tooTall = new WorkbookModel(List.of(new SheetModel("Data", rows)));
        assertThrows(IllegalArgumentException.class,
            () -> new PoiWorkbookWriter().write(tooTall, temp.resolve("too-tall.xls"), OutputFormat.XLS));
    }

    @Test
    void rejectsDestinationExtensionMismatchAndPathOutsideExplicitRoot() throws Exception {
        PoiWorkbookWriter writer = new PoiWorkbookWriter();
        assertThrows(IllegalArgumentException.class,
            () -> writer.write(sample(), temp.resolve("wrong.xlsx"), OutputFormat.XLS));
        Path safeRoot = temp.resolve("root");
        assertThrows(IllegalArgumentException.class,
            () -> writer.write(sample(), temp.resolve("escaped.xls"), OutputFormat.XLS, safeRoot));
    }
}
