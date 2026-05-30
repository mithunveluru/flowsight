package com.flowsight.reports;

import com.flowsight.dto.report.GenerateReportRequest;
import com.flowsight.dto.report.ReportJobResponse;
import com.flowsight.entity.ReportJob;
import com.flowsight.entity.ReportJobStatus;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.ReportJobRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Orchestrates intelligence-report generation: resolves the date range, persists
 * a job, kicks off async generation, and serves the resulting PDF.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntelligenceReportService {

    @Value("${application.storage.upload-dir:/tmp/flowsight-receipts}")
    private String uploadDirRoot;

    private Path reportsRoot;

    private final ReportJobRepository        reportJobRepository;
    private final ReportAnalyticsAggregator  analyticsAggregator;
    private final ReportInsightGenerator     insightGenerator;
    private final PdfGenerationService       pdfGenerationService;

    @PostConstruct
    public void init() {
        this.reportsRoot = Paths.get(uploadDirRoot, "..", "flowsight-reports")
            .toAbsolutePath().normalize();
        try {
            Files.createDirectories(reportsRoot);
            log.info("Intelligence-report storage initialized at: {}", reportsRoot);
        } catch (IOException e) {
            log.warn("Could not create reports directory {}: {}", reportsRoot, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Request handling
    // -------------------------------------------------------------------------

    @Transactional
    public ReportJobResponse createJob(GenerateReportRequest request, User user) {
        DateRange range = resolveRange(request);
        ReportJob job = ReportJob.builder()
            .user(user)
            .periodStart(range.from)
            .periodEnd(range.to)
            .periodLabel(range.label)
            .status(ReportJobStatus.PENDING)
            .build();
        ReportJob saved = reportJobRepository.save(job);

        // Kick off async generation (self-injection through Spring proxy by re-fetching)
        generateAsync(saved.getId());

        return toResponse(saved);
    }

    public ReportJobResponse getStatus(UUID jobId, UUID userId) {
        ReportJob job = reportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        return toResponse(job);
    }

    public Page<ReportJobResponse> list(UUID userId, Pageable pageable) {
        return reportJobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    /** Reads the PDF bytes for download. Increments the download counter. */
    @Transactional
    public DownloadHandle download(UUID jobId, UUID userId) {
        ReportJob job = reportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        if (job.getStatus() != ReportJobStatus.READY || job.getPdfPath() == null) {
            throw new IllegalStateException("Report is not ready for download");
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(job.getPdfPath()));
            job.setDownloadCount(job.getDownloadCount() + 1);
            reportJobRepository.save(job);
            String filename = String.format("flowsight-report-%s-to-%s.pdf",
                job.getPeriodStart(), job.getPeriodEnd());
            return new DownloadHandle(bytes, filename);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read report file: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void delete(UUID jobId, UUID userId) {
        ReportJob job = reportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        if (job.getPdfPath() != null) {
            try { Files.deleteIfExists(Paths.get(job.getPdfPath())); }
            catch (IOException e) { log.warn("Failed to delete report file: {}", e.getMessage()); }
        }
        reportJobRepository.delete(job);
    }

    // -------------------------------------------------------------------------
    // Background generation
    // -------------------------------------------------------------------------

    @Async
    public void generateAsync(UUID jobId) {
        try {
            generateInTransaction(jobId);
        } catch (Exception e) {
            log.warn("Report generation failed for job {}: {}", jobId, e.getMessage(), e);
            markFailed(jobId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateInTransaction(UUID jobId) throws IOException {
        ReportJob job = reportJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        job.setStatus(ReportJobStatus.GENERATING);
        reportJobRepository.save(job);

        UUID userId = job.getUser().getId();
        ReportData data = analyticsAggregator.aggregate(userId, job.getPeriodStart(), job.getPeriodEnd());
        ReportInsightGenerator.ReportNarrative narrative = insightGenerator.generate(data);
        byte[] pdf = pdfGenerationService.generate(data, narrative);

        // Persist PDF to disk
        Path userDir = reportsRoot.resolve(userId.toString());
        Files.createDirectories(userDir);
        Path pdfPath = userDir.resolve(jobId + ".pdf");
        Files.write(pdfPath, pdf);

        job.setStatus(ReportJobStatus.READY);
        job.setPdfPath(pdfPath.toString());
        job.setPdfSizeBytes((long) pdf.length);
        job.setCompletedAt(Instant.now());
        reportJobRepository.save(job);

        log.info("Generated intelligence report {} ({} bytes) for user {}",
            jobId, pdf.length, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String error) {
        reportJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ReportJobStatus.FAILED);
            job.setErrorMessage(error != null && error.length() > 500
                ? error.substring(0, 500) : error);
            job.setCompletedAt(Instant.now());
            reportJobRepository.save(job);
        });
    }

    // -------------------------------------------------------------------------
    // Date range resolution
    // -------------------------------------------------------------------------

    private DateRange resolveRange(GenerateReportRequest request) {
        LocalDate today = LocalDate.now();
        return switch (request.getPreset()) {
            case LAST_7_DAYS   -> new DateRange(today.minusDays(6),  today, "Last 7 days");
            case LAST_30_DAYS  -> new DateRange(today.minusDays(29), today, "Last 30 days");
            case LAST_90_DAYS  -> new DateRange(today.minusDays(89), today, "Last 90 days");
            case THIS_MONTH    -> new DateRange(today.withDayOfMonth(1), today, "This month");
            case LAST_MONTH    -> {
                LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
                LocalDate lastMonthStart = lastMonthEnd.withDayOfMonth(1);
                yield new DateRange(lastMonthStart, lastMonthEnd, "Last month");
            }
            case THIS_YEAR     -> new DateRange(today.with(TemporalAdjusters.firstDayOfYear()), today, "This year");
            case CUSTOM        -> {
                if (request.getFrom() == null || request.getTo() == null) {
                    throw new IllegalArgumentException("CUSTOM range requires both from and to dates");
                }
                if (request.getFrom().isAfter(request.getTo())) {
                    throw new IllegalArgumentException("from must be on or before to");
                }
                yield new DateRange(request.getFrom(), request.getTo(),
                    request.getFrom() + " to " + request.getTo());
            }
        };
    }

    private ReportJobResponse toResponse(ReportJob job) {
        return ReportJobResponse.builder()
            .id(job.getId())
            .periodStart(job.getPeriodStart())
            .periodEnd(job.getPeriodEnd())
            .periodLabel(job.getPeriodLabel())
            .status(job.getStatus().name())
            .pdfSizeBytes(job.getPdfSizeBytes())
            .errorMessage(job.getErrorMessage())
            .downloadCount(job.getDownloadCount())
            .createdAt(job.getCreatedAt())
            .completedAt(job.getCompletedAt())
            .build();
    }

    private record DateRange(LocalDate from, LocalDate to, String label) {}

    public record DownloadHandle(byte[] bytes, String filename) {}
}
