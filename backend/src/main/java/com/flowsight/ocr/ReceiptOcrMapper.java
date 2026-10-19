package com.flowsight.ocr;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptAmountOption;
import com.flowsight.dto.receipt.ReceiptDecision;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Maps the receipt-ocr response to OcrExtractionResult: validates the total (VAT/subtotal
// correction), strips control chars, rejects non-positive amounts and future dates.
@Component
@Slf4j
public class ReceiptOcrMapper {

    private final ReceiptAmountValidator amountValidator;
    private final MerchantExtractor merchantExtractor;

    @Autowired
    public ReceiptOcrMapper(ReceiptAmountValidator amountValidator, MerchantExtractor merchantExtractor) {
        this.amountValidator = amountValidator;
        this.merchantExtractor = merchantExtractor;
    }

    public ReceiptOcrMapper(ReceiptAmountValidator amountValidator) {
        this(amountValidator, new MerchantExtractor());
    }

    private static final int MAX_MERCHANT_LENGTH = 200;
    private static final int MAX_ADDRESS_LENGTH  = 500;

    // The vision model's accuracy has not been measured (no usable model in development), so its
    // fields are never ACCEPT on its word alone; agreement with the Tesseract pass raises them.
    private static final double LLM_HIGH = 0.80;
    private static final double LLM_MEDIUM = 0.60;
    private static final double LLM_LOW = 0.30;
    private static final double LLM_MERCHANT = 0.75;

    public OcrExtractionResult map(ReceiptOcrResponse response) {
        List<ReceiptLineItem> items =
            response.getLineItems() != null ? response.getLineItems() : List.of();

        BigDecimal rawAmount = sanitizeAmount(response.getTotalAmount());

        // correct VAT/subtotal misidentification against the line items
        AmountCandidate bestAmount = amountValidator.validate(rawAmount, items);

        BigDecimal amount  = bestAmount.getAmount();
        String  rawMerchant = sanitizeText(response.getMerchantName(), MAX_MERCHANT_LENGTH);
        String  merchant    = rawMerchant == null || merchantExtractor.isHeading(rawMerchant) ? null : merchantExtractor.clean(rawMerchant);
        String  address    = sanitizeText(response.getMerchantAddress(), MAX_ADDRESS_LENGTH);
        String  rawText    = buildRawText(response);

        CurrencyDetector.Guess currency = CurrencyDetector.detect(MoneyParser.normalizeDigits(rawText));
        ReceiptDates.Guess date = response.getTransactionDate() == null ? ReceiptDates.Guess.NONE
            : ReceiptDates.find(List.of(response.getTransactionDate()), currency.code());

        double amountConfidence = switch (bestAmount.getConfidence()) {
            case HIGH -> LLM_HIGH;
            case MEDIUM -> LLM_MEDIUM;
            case LOW -> LLM_LOW;
        };
        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        fields.put(ReceiptParserService.FIELD_MERCHANT, merchant == null ? FieldAssessment.missing() : FieldAssessment.of(LLM_MERCHANT, "llm"));
        fields.put(ReceiptParserService.FIELD_AMOUNT, amount == null ? FieldAssessment.missing() : FieldAssessment.of(amountConfidence, "llm"));
        fields.put(ReceiptParserService.FIELD_DATE, date.date() == null ? FieldAssessment.missing()
            : FieldAssessment.of(date.ambiguous() ? 0.65 : LLM_HIGH, date.ambiguous() ? "llm-day-month-order" : "llm"));
        fields.put(ReceiptParserService.FIELD_CURRENCY, currency.code() == null ? FieldAssessment.missing()
            : FieldAssessment.of(currency.confidence(), currency.cue()));
        ReceiptDecision decision = ReceiptDecision.ACCEPT;
        for (FieldAssessment f : fields.values()) decision = decision.worst(f.decision());

        boolean successful = amount != null && amount.compareTo(BigDecimal.ZERO) > 0;

        return OcrExtractionResult.builder()
            .amount(amount)
            .date(date.date())
            .merchant(merchant)
            .merchantRaw(rawMerchant)
            .merchantAddress(address)
            .currency(currency.code())
            .successful(successful)
            .rawText(rawText)
            .lineItems(items)
            .confidence(fields.get(ReceiptParserService.FIELD_AMOUNT).confidence())
            .requiresConfirmation(decision == ReceiptDecision.ASK_USER)
            .amountCandidates(amount == null ? List.of() : List.of(new ReceiptAmountOption(amount, bestAmount.getLabel(), "llm")))
            .fields(fields)
            .decision(decision)
            .build();
    }

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
        LocalDate d = ReceiptDates.find(List.of(raw.strip()), null).date();
        if (d == null) log.debug("receipt-ocr date unparseable");
        return d;
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
