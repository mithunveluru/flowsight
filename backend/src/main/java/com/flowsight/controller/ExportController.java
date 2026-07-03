package com.flowsight.controller;

import com.flowsight.dto.report.MonthlyReportResponse;
import com.flowsight.dto.report.TaxSummaryResponse;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import com.flowsight.service.ExportService;
import com.flowsight.service.ReportService;
import com.flowsight.service.TaxDeductionDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService        exportService;
    private final ReportService        reportService;
    private final TaxDeductionDetector taxDeductionDetector;

    // CSV of transactions by date range + optional category; default current month
    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TransactionCategory category,
        @AuthenticationPrincipal User user
    ) {
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);

        String csv = exportService.exportTransactionsCsv(
            user.getId(), effectiveFrom, effectiveTo, category);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
            .filename(exportService.csvFilename(effectiveFrom, effectiveTo))
            .build());
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, 200);
    }

    // monthly report for the printable view
    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReportResponse> monthlyReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @AuthenticationPrincipal User user
    ) {
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);

        return ResponseEntity.ok(
            reportService.buildMonthlyReport(user.getId(), effectiveFrom, effectiveTo));
    }

    // tax summary for the current financial year
    @GetMapping("/tax-summary")
    public ResponseEntity<TaxSummaryResponse> taxSummary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            taxDeductionDetector.detectForFinancialYear(user.getId(), LocalDate.now()));
    }
}
