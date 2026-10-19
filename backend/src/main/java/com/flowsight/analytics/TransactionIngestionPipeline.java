package com.flowsight.analytics;

import com.flowsight.dto.transaction.CreateTransactionRequest;
import com.flowsight.entity.*;
import com.flowsight.merchant.MerchantResolution;
import com.flowsight.merchant.MerchantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

// Ingestion pipeline: normalize, resolve the merchant, categorize, build entity.
@Component
@RequiredArgsConstructor
public class TransactionIngestionPipeline {

    private static final BigDecimal FULL_CONFIDENCE = BigDecimal.ONE.setScale(4, RoundingMode.UNNECESSARY);
    // a category from the user's own rule: their earlier choice, but not reviewed for this row
    private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.9500");
    private static final int MERCHANT_RAW_LENGTH = 255;

    private final NormalizationService normalization;
    private final CategorizationService categorization;
    private final MerchantResolver merchantResolver;

    public Transaction processManual(CreateTransactionRequest request, User user) {
        String normalizedDesc = normalization.normalize(request.getDescription());
        boolean typed = request.getMerchant() != null && !request.getMerchant().isBlank();
        String merchantRaw = typed ? request.getMerchant() : request.getDescription();
        MerchantResolution merchant = typed
            ? merchantResolver.resolveTyped(user.getId(), request.getMerchant())
            : merchantResolver.resolve(user.getId(), request.getDescription());

        TransactionCategory category = request.getCategory();
        BigDecimal confidence = FULL_CONFIDENCE;

        if (category == null && merchant.category() != null) {
            category = merchant.category();
            confidence = RULE_CONFIDENCE;
        } else if (category == null) {
            CategorizationService.CategorizationResult result =
                categorization.categorize(normalizedDesc, categorizationMerchant(merchant, merchantRaw), request.getAmount());
            category = result.getCategory();
            confidence = toDecimal(result.getConfidence());
        }

        return Transaction.builder()
            .user(user)
            .amount(request.getAmount())
            .currency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "INR")
            .transactionDate(request.getTransactionDate())
            .description(normalizedDesc)
            .merchant(merchant.name())
            .merchantRaw(truncate(merchantRaw))
            .category(category)
            .type(request.getType())
            .source(TransactionSource.MANUAL)
            .confidenceScore(confidence)
            .notes(request.getNotes())
            .reviewed(request.getCategory() != null)
            .build();
    }

    /** merchants: one resolver session per import, so the user's rules are read once. */
    public Transaction processCsvRow(CsvParserService.CsvRow row, User user, MerchantResolver.Session merchants) {
        String normalizedDesc = normalization.normalize(row.getDescription());
        String merchantRaw = row.getMerchant() != null ? row.getMerchant() : row.getDescription();
        MerchantResolution merchant = merchants.resolve(merchantRaw);

        TransactionCategory category;
        BigDecimal confidence;
        if (merchant.category() != null) {
            category = merchant.category();
            confidence = RULE_CONFIDENCE;
        } else {
            CategorizationService.CategorizationResult result =
                categorization.categorize(normalizedDesc, categorizationMerchant(merchant, merchantRaw), row.getAmount());
            category = result.getCategory();
            confidence = toDecimal(result.getConfidence());
        }

        return Transaction.builder()
            .user(user)
            .amount(row.getAmount())
            .currency("INR")
            .transactionDate(row.getDate() != null ? row.getDate() : LocalDate.now())
            .description(normalizedDesc)
            .merchant(merchant.name())
            .merchantRaw(truncate(merchantRaw))
            .category(category)
            .type(row.isDebit() ? TransactionType.DEBIT : TransactionType.CREDIT)
            .source(TransactionSource.CSV)
            .confidenceScore(confidence)
            .rawText(row.getRawText())
            .reviewed(false)
            .build();
    }

    // LLM naming is left out of imports: one slow call per unknown row would stall the upload.
    public MerchantResolver.Session csvSession(User user) {
        return merchantResolver.session(user.getId(), false);
    }

    // The normalized description loses words such as REFUND and IMPS, so the categorizer gets
    // the original merchant text alongside the resolved name.
    public static String categorizationMerchant(MerchantResolution merchant, String merchantRaw) {
        if (merchant.name() == null) return merchantRaw;
        if (merchantRaw == null || merchantRaw.equalsIgnoreCase(merchant.name())) return merchant.name();
        return merchant.name() + " " + merchantRaw;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= MERCHANT_RAW_LENGTH ? t : t.substring(0, MERCHANT_RAW_LENGTH);
    }

    private static BigDecimal toDecimal(double d) {
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP);
    }
}
