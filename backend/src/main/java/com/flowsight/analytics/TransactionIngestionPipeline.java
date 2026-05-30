package com.flowsight.analytics;

import com.flowsight.dto.transaction.CreateTransactionRequest;
import com.flowsight.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Orchestrates the ingestion pipeline: normalize → categorize → build entity.
 * Keeps service and analytics layers decoupled.
 */
@Component
@RequiredArgsConstructor
public class TransactionIngestionPipeline {

    private static final BigDecimal FULL_CONFIDENCE = BigDecimal.ONE.setScale(4, RoundingMode.UNNECESSARY);

    private final NormalizationService normalization;
    private final CategorizationService categorization;

    public Transaction processManual(CreateTransactionRequest request, User user) {
        String normalizedDesc = normalization.normalize(request.getDescription());
        String merchant = request.getMerchant() != null
            ? request.getMerchant()
            : normalization.extractMerchant(request.getDescription());

        TransactionCategory category = request.getCategory();
        BigDecimal confidence = FULL_CONFIDENCE;

        if (category == null) {
            CategorizationService.CategorizationResult result =
                categorization.categorize(normalizedDesc, merchant, request.getAmount());
            category = result.getCategory();
            confidence = toDecimal(result.getConfidence());
        }

        return Transaction.builder()
            .user(user)
            .amount(request.getAmount())
            .currency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "INR")
            .transactionDate(request.getTransactionDate())
            .description(normalizedDesc)
            .merchant(merchant)
            .category(category)
            .type(request.getType())
            .source(TransactionSource.MANUAL)
            .confidenceScore(confidence)
            .notes(request.getNotes())
            .reviewed(request.getCategory() != null)
            .build();
    }

    public Transaction processCsvRow(CsvParserService.CsvRow row, User user) {
        String normalizedDesc = normalization.normalize(row.getDescription());
        String merchant = row.getMerchant() != null
            ? row.getMerchant()
            : normalization.extractMerchant(row.getDescription());

        CategorizationService.CategorizationResult result =
            categorization.categorize(normalizedDesc, merchant, row.getAmount());

        return Transaction.builder()
            .user(user)
            .amount(row.getAmount())
            .currency("INR")
            .transactionDate(row.getDate() != null ? row.getDate() : LocalDate.now())
            .description(normalizedDesc)
            .merchant(merchant)
            .category(result.getCategory())
            .type(row.isDebit() ? TransactionType.DEBIT : TransactionType.CREDIT)
            .source(TransactionSource.CSV)
            .confidenceScore(toDecimal(result.getConfidence()))
            .rawText(row.getRawText())
            .reviewed(false)
            .build();
    }

    private static BigDecimal toDecimal(double d) {
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP);
    }
}
