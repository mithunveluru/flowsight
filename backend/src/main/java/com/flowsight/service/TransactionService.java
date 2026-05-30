package com.flowsight.service;

import com.flowsight.analytics.CsvParserService;
import com.flowsight.analytics.TransactionIngestionPipeline;
import com.flowsight.dto.transaction.*;
import com.flowsight.entity.*;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionIngestionPipeline ingestionPipeline;
    private final CsvParserService csvParserService;
    private final UserService userService;
    private final AuditLogService    auditLogService;
    private final com.flowsight.security.RateLimiter rateLimiter;

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request, UUID userId) {
        User user = userService.findById(userId);
        Transaction tx = ingestionPipeline.processManual(request, user);
        return toResponse(transactionRepository.save(tx));
    }

    public Page<TransactionResponse> list(
        UUID userId,
        TransactionCategory category,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        return transactionRepository
            .findWithFilters(userId, category, startDate, endDate, pageable)
            .map(this::toResponse);
    }

    public TransactionResponse getById(UUID id, UUID userId) {
        return toResponse(findOwnedOrThrow(id, userId));
    }

    @Transactional
    public TransactionResponse update(UUID id, UUID userId, UpdateTransactionRequest request) {
        Transaction tx = findOwnedOrThrow(id, userId);

        if (request.getAmount() != null)          tx.setAmount(request.getAmount());
        if (request.getCurrency() != null)         tx.setCurrency(request.getCurrency().toUpperCase());
        if (request.getTransactionDate() != null)  tx.setTransactionDate(request.getTransactionDate());
        if (request.getDescription() != null)      tx.setDescription(request.getDescription());
        if (request.getMerchant() != null)          tx.setMerchant(request.getMerchant());
        if (request.getType() != null)              tx.setType(request.getType());
        if (request.getNotes() != null)             tx.setNotes(request.getNotes());
        if (request.getReviewed() != null)          tx.setReviewed(request.getReviewed());

        // Category update always marks as reviewed (user is explicitly overriding)
        if (request.getCategory() != null) {
            tx.setCategory(request.getCategory());
            tx.setConfidenceScore(BigDecimal.ONE);
            tx.setReviewed(true);
        }

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Transaction tx = findOwnedOrThrow(id, userId);
        transactionRepository.delete(tx);
    }

    @Transactional
    public BulkImportResult importCsv(MultipartFile file, UUID userId) throws IOException {
        rateLimiter.checkUploadAttempt(userId.toString());

        User user = userService.findById(userId);
        CsvParserService.ParseResult parsed = csvParserService.parse(file);
        entitlementService.checkCsvImportSize(user.getSubscriptionTier(), parsed.getRows().size());

        List<Transaction> toSave = new ArrayList<>();
        List<String> errors = new ArrayList<>(parsed.getErrors());
        int rowNum = 0;

        for (CsvParserService.CsvRow row : parsed.getRows()) {
            rowNum++;
            try {
                Transaction tx = ingestionPipeline.processCsvRow(row, user);
                toSave.add(tx);
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                log.debug("Failed to process CSV row {}: {}", rowNum, e.getMessage());
            }
        }

        List<Transaction> saved = transactionRepository.saveAll(toSave);

        // Compute the imported date range and total amount.
        // The UI uses these to tell the user exactly when imported transactions land
        // and to link directly to the right analytics view — avoids the common confusion
        // of importing last month's bank statement and not seeing it on "this month"'s dashboard.
        LocalDate firstDate = null;
        LocalDate lastDate  = null;
        BigDecimal totalImported = BigDecimal.ZERO;
        for (Transaction tx : saved) {
            LocalDate d = tx.getTransactionDate();
            if (firstDate == null || d.isBefore(firstDate)) firstDate = d;
            if (lastDate  == null || d.isAfter(lastDate))    lastDate  = d;
            if (tx.getAmount() != null) totalImported = totalImported.add(tx.getAmount());
        }

        log.info(
            "CSV import for user {}: {} imported, {} errors, range {}..{}, total {}",
            userId, saved.size(), errors.size(), firstDate, lastDate, totalImported
        );
        auditLogService.log(user, AuditLogService.ACTION_CSV_IMPORTED, "Transactions", null,
            java.util.Map.of("imported", saved.size(), "skipped", errors.size()));

        return BulkImportResult.builder()
            .totalRows(parsed.getRows().size() + parsed.getErrors().size())
            .imported(saved.size())
            .skipped(errors.size())
            .errors(errors)
            .firstTransactionDate(firstDate)
            .lastTransactionDate(lastDate)
            .totalAmountImported(totalImported)
            .build();
    }

    private Transaction findOwnedOrThrow(UUID id, UUID userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
            .id(tx.getId())
            .amount(tx.getAmount())
            .currency(tx.getCurrency())
            .transactionDate(tx.getTransactionDate())
            .description(tx.getDescription())
            .merchant(tx.getMerchant())
            .category(tx.getCategory())
            .categoryDisplayName(tx.getCategory() != null ? tx.getCategory().getDisplayName() : null)
            .type(tx.getType())
            .source(tx.getSource())
            .confidenceScore(tx.getConfidenceScore())
            .notes(tx.getNotes())
            .reviewed(tx.isReviewed())
            .createdAt(tx.getCreatedAt())
            .build();
    }
}
