package com.comus93.specgen.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.comus93.specgen.runner.FileResult;
import com.comus93.specgen.runner.FileResult.FileStatus;

class DiagnosticPresentationTest {
    @Test
    void warningUsesBasenameAndLocationWithoutSourceExcerpt() {
        FileResult result = result(FileStatus.WARNING,
                "Parse error at line 12, column 7: invalid tag\nSource excerpt (original JSP):\nsecret source");

        String line = SpecGeneratorFrame.diagnosticLine(result);
        assertEquals("경고 page.jsp 12:7 [PARSE_ERROR] 구문 분석 실패, 부분 복구", line);
        assertFalse(line.contains("invalid tag"));
        assertFalse(line.contains("secret source"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
    }

    @Test
    void failureRecognizesColonLocationWithoutLeakingDiagnostic() {
        String line = SpecGeneratorFrame.diagnosticLine(result(FileStatus.FAILED,
                "page.jsp:23:4 " + "x".repeat(300) + "\r\nsource"));

        assertEquals("실패 page.jsp 23:4 [PARSE_ERROR] 구문 분석 실패", line);
        assertFalse(line.contains("xxx"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
        assertTrue(line.length() < 100);
    }

    @Test
    void missingMessageStillShowsReason() {
        assertEquals("실패 page.jsp [PARSE_ERROR] 구문 분석 실패",
                SpecGeneratorFrame.diagnosticLine(result(FileStatus.FAILED, "")));
    }

    @Test
    void longBasenameIsBoundedAndOtherErrorsHaveFixedReason() {
        FileResult result = new FileResult(Path.of("folder", "a".repeat(100) + ".jsp"),
                Path.of("out.xls"), FileStatus.FAILED, "WRITE_ERROR", "private detail");
        String line = SpecGeneratorFrame.diagnosticLine(result);
        assertTrue(line.contains("... [WRITE_ERROR] 결과 저장 실패"));
        assertFalse(line.contains("private detail"));
        assertTrue(line.length() < 100);
    }

    private static FileResult result(FileStatus status, String message) {
        return new FileResult(Path.of("folder", "page.jsp"), Path.of("out.xls"),
                status, "PARSE_ERROR", message);
    }
}
