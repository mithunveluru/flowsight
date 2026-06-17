package com.flowsight.ocr;

import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Maps the external receipt-ocr API response to the internal {@link OcrExtractionResult}.
 *
 * <p>After basic field mapping and sanitization, the extracted {@code total_amount} is
 * passed through {@link ReceiptAmountValidator} to detect and correct common LLM errors
 * (VAT/CGST/SGST/subtotal returned as the final total).
 *
 * <p>Security contract:
 * <ul>
 *   <li>Merchant and address strings are stripped of control characters and truncated.</li>
 *   <li>Amounts are rejected if null, zero, or negative.</li>
 *   <li>Dates in the future are rejected.</li>
 *   <li>Confidence is clamped to [0.0, 1.0].</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptOcrMapper {

    private final ReceiptAmountValidator amountValidator;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    );

    private static final int MAX_MERCHANT_LENGTH = 200;
    private static final int MAX_ADDRESS_LENGTH  = 500;

    public OcrExtractionResult map(ReceiptOcrResponse response) {
        List<ReceiptLineItem> items =
            response.getLineItems() != null ? response.getLineItems() : List.of();

        // Raw sanitized amount before post-processing validation
        BigDecimal rawAmount = sanitizeAmount(response.getTotalAmount());

        // Post-process: validate total against line items to catch VAT/subtotal misidentification
        AmountCandidate bestAmount = amountValidator.validate(rawAmount, items);

        if (bestAmount.getLabel() != null && !bestAmount.getLabel().isEmpty()) {
            log.debug("Amount corrected: total_amount={} -> '{}' {} (score={})",
                rawAmount, bestAmount.getLabel(), bestAmount.getAmount(),
                String.format("%.2f", bestAmount.getScore()));
        }

        BigDecimal amount  = bestAmount.getAmount();
        LocalDate  date    = parseDate(response.getTransactionDate());
        String  merchant   = sanitizeText(response.getMerchantName(), MAX_MERCHANT_LENGTH);
        String  address    = sanitizeText(response.getMerchantAddress(), MAX_ADDRESS_LENGTH);
        double  confidence = bestAmount.getConfidence().getNumericValue();

        boolean successful            = amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
        boolean requiresConfirmation  = bestAmount.getConfidence() == AmountConfidence.LOW;

        return OcrExtractionResult.builder()
            .amount(amount)
            .date(date)
            .merchant(merchant)
            .merchantAddress(address)
            .currency("INR")
            .successful(successful)
            .rawText(buildRawText(response))
            .lineItems(items)
            .confidence(confidence)
            .requiresConfirmation(requiresConfirmation)
            .build();
    }

    // Sanitizers — package-visible for testing

    BigDecimal sanitizeAmount(BigDecimal raw) {
        if (raw == null) return null;
        if (raw.compareTo(BigDecimal.ZERO) <= 0) return null;
        try {
            return raw.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    String sanitizeText(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.strip().replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "").trim();
        if (cleaned.isBlank()) return null;
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.strip();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate d = LocalDate.parse(cleaned, fmt);
                return d.isAfter(LocalDate.now()) ? null : d;
            } catch (DateTimeParseException ignored) {}
        }
        log.debug("receipt-ocr date unparseable: '{}'", raw);
        return null;
    }

    Double clampConfidence(Double raw) {
        if (raw == null) return null;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    private String buildRawText(ReceiptOcrResponse r) {
        StringBuilder sb = new StringBuilder();
        if (r.getMerchantName()    != null) sb.append(r.getMerchantName()).append('\n');
        if (r.getMerchantAddress() != null) sb.append(r.getMerchantAddress()).append('\n');
        if (r.getTransactionDate() != null) sb.append("Date: ").append(r.getTransactionDate()).append('\n');
        if (r.getTotalAmount()     != null) sb.append("Total: ").append(r.getTotalAmount()).append('\n');
        if (r.getLineItems() != null) {
            for (ReceiptLineItem item : r.getLineItems()) {
                if (item.getItemName() != null) {
                    sb.append(item.getItemName());
                    if (item.getItemPrice() != null) sb.append(' ').append(item.getItemPrice());
                    sb.append('\n');
                }
            }
        }
        return sb.toString().trim();
    }
}
