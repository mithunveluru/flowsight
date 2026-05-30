package com.flowsight.controller;

import com.flowsight.dto.report.GenerateReportRequest;
import com.flowsight.dto.report.ReportJobResponse;
import com.flowsight.entity.User;
import com.flowsight.reports.IntelligenceReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence-reports")
@RequiredArgsConstructor
public class IntelligenceReportController {

    private final IntelligenceReportService reportService;

    /** Creates a new generation job. Returns immediately with PENDING status; client polls for READY. */
    @PostMapping
    public ResponseEntity<ReportJobResponse> create(
        @Valid @RequestBody GenerateReportRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(reportService.createJob(request, user));
    }

    /** Returns current status of a single job (PENDING / GENERATING / READY / FAILED). */
    @GetMapping("/{id}")
    public ResponseEntity<ReportJobResponse> getStatus(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(reportService.getStatus(id, user.getId()));
    }

    /** Lists the user's report history. */
    @GetMapping
    public ResponseEntity<Page<ReportJobResponse>> list(
        @AuthenticationPrincipal User user,
        @PageableDefault(size = 25) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.list(user.getId(), pageable));
    }

    /** Downloads the generated PDF. */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        var handle = reportService.download(id, user.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(handle.filename()).build());
        headers.setContentLength(handle.bytes().length);
        return new ResponseEntity<>(handle.bytes(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User user
    ) {
        reportService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
