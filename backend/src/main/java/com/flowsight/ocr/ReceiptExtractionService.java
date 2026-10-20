package com.flowsight.ocr;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptAmountOption;
import com.flowsight.dto.receipt.ReceiptChargeLine;
import com.flowsight.dto.receipt.ReceiptDecision;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import com.flowsight.exception.OcrException;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PredictionContext;
import com.flowsight.ml.SubjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the OCR engine for one receipt image, in this order: ml-service (ML_OCR_ENABLED), then the
 * receipt-ocr vision service (RECEIPT_OCR_URL) checked against Tesseract, then the Tesseract
 * pipeline alone, which is what runs when nothing else is deployed. Every engine tried and why it
 * fell through is kept on the result as engineAttempts. Makes network calls and runs Tesseract:
 * never call it inside a transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptExtractionService {

    public static final String PROVIDER_ML = "ML_SERVICE";
    public static final String PROVIDER_LLM = "RECEIPT_OCR_SERVICE";
    public static final String PROVIDER_TESSERACT = "TESSERACT";

    private static final Set<String> CURRENCIES = Set.of("INR", "SAR", "AED", "USD", "EUR", "GBP");

    private final MlClient mlClient;
    private final ReceiptOcrClientService receiptOcrClient;
    private final ReceiptOcrMapper ocrMapper;
    private final TesseractReceiptPipeline tesseract;

    /** provider and, for ml-service, the model version that produced the result. */
    public record Extraction(String provider, String modelVersion, OcrExtractionResult result) {
    }

    public Extraction extract(Path image, String mimeType, PredictionContext context) {
        List<String> attempts = new ArrayList<>();
        Extraction chosen = choose(image, mimeType, context, attempts);
        chosen.result().setEngineAttempts(List.copyOf(attempts));
        if (attempts.size() > 1) log.info("receipt OCR fell through: {}", String.join(" -> ", attempts));
        return chosen;
    }

    private Extraction choose(Path image, String mimeType, PredictionContext context, List<String> attempts) {
        if (mlClient.isActive(MlCapability.OCR)) {
            Optional<Extraction> ml = fromMlService(image, mimeType, context, attempts);
            if (ml.isPresent()) return ml.get();
        } else {
            attempts.add(PROVIDER_ML + ": not enabled");
        }
        ReceiptOcrClientService.Call call = receiptOcrClient.call(image);
        attempts.add(PROVIDER_LLM + ": " + call.outcome());
        if (call.response().isPresent()) {
            OcrExtractionResult fromLlm = ocrMapper.map(call.response().get());
            OcrExtractionResult fromTesseract = null;
            try {
                fromTesseract = tesseract.extract(image);
            } catch (OcrException e) {
                log.debug("Tesseract cross-check unavailable: {}", e.getMessage());
            }
            return new Extraction(PROVIDER_LLM, null, crossCheck(fromLlm, fromTesseract));
        }
        OcrExtractionResult result = tesseract.extract(image);
        attempts.add(PROVIDER_TESSERACT + ": ok");
        return new Extraction(PROVIDER_TESSERACT, null, result);
    }

    public static PredictionContext context(java.util.UUID userId, java.util.UUID receiptId) {
        return PredictionContext.of(userId, SubjectType.RECEIPT, receiptId);
    }

    private Optional<Extraction> fromMlService(Path image, String mimeType, PredictionContext context,
                                               List<String> attempts) {
        String encoded;
        try {
            encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(image));
        } catch (IOException e) {
            attempts.add(PROVIDER_ML + ": image unreadable");
            return Optional.empty();
        }
        MlPrediction<MlOcrResult> p = mlClient.predict(MlCapability.OCR, new MlOcrRequest(encoded, mimeType),
            MlOcrResult.class, context, () -> null);
        if (!p.fromModel() || p.value() == null) {
            attempts.add(PROVIDER_ML + ": " + (p.outcome() == null ? "shadow only" : p.outcome().name()));
            return Optional.empty();
        }
        OcrExtractionResult mapped = fromMl(p.value());
        if (mapped.getAmount() == null && mapped.getMerchant() == null) {
            attempts.add(PROVIDER_ML + ": no merchant or total");
            return Optional.empty();
        }
        attempts.add(PROVIDER_ML + ": ok");
        return Optional.of(new Extraction(PROVIDER_ML, p.modelVersion(), mapped));
    }

    // ml-service output is validated again here: positive amounts, past dates, known currencies
    static OcrExtractionResult fromMl(MlOcrResult r) {
        BigDecimal amount = positive(r.amount());
        LocalDate date = null;
        try {
            if (r.date() != null) date = LocalDate.parse(r.date());
        } catch (DateTimeException e) {
            date = null;
        }
        if (date != null && date.isAfter(LocalDate.now().plusDays(1))) date = null;
        String currency = r.currency() != null && CURRENCIES.contains(r.currency().toUpperCase(Locale.ROOT))
            ? r.currency().toUpperCase(Locale.ROOT) : null;

        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        fields.put(ReceiptParserService.FIELD_MERCHANT, field(r, ReceiptParserService.FIELD_MERCHANT, r.merchant() != null));
        fields.put(ReceiptParserService.FIELD_AMOUNT, field(r, ReceiptParserService.FIELD_AMOUNT, amount != null));
        fields.put(ReceiptParserService.FIELD_DATE, field(r, ReceiptParserService.FIELD_DATE, date != null));
        fields.put(ReceiptParserService.FIELD_CURRENCY, field(r, ReceiptParserService.FIELD_CURRENCY, currency != null));
        ReceiptDecision decision = worst(fields);

        List<ReceiptLineItem> items = r.lineItems() == null ? List.of() : r.lineItems().stream()
            .filter(i -> i.name() != null && positive(i.price()) != null)
            .map(i -> ReceiptLineItem.builder().itemName(i.name()).itemQuantity(i.quantity()).itemPrice(i.price()).build())
            .toList();
        List<ReceiptAmountOption> options = r.amountCandidates() == null ? List.of() : r.amountCandidates().stream()
            .filter(o -> positive(o.amount()) != null)
            .map(o -> new ReceiptAmountOption(o.amount(), o.label(), o.basis())).toList();

        return OcrExtractionResult.builder()
            .merchant(r.merchant())
            .merchantRaw(r.merchantRaw())
            .amount(amount)
            .date(date)
            .currency(currency)
            .successful(amount != null)
            .rawText(r.text())
            .lineItems(items)
            .confidence(fields.get(ReceiptParserService.FIELD_AMOUNT).confidence())
            .requiresConfirmation(decision == ReceiptDecision.ASK_USER)
            .subtotal(positive(r.subtotal()))
            .taxes(charges(r.taxes()))
            .discounts(charges(r.discounts()))
            .tip(positive(r.tip()))
            .roundOff(r.roundOff())
            .amountCandidates(options)
            .fields(fields)
            .decision(decision)
            .build();
    }

    private static FieldAssessment field(MlOcrResult r, String name, boolean present) {
        if (!present) return FieldAssessment.missing();
        MlOcrResult.Field f = r.fields() == null ? null : r.fields().get(name);
        if (f == null || f.confidence() == null) return FieldAssessment.ask(0.5, "ml-service");
        if ("ASK_USER".equals(f.decision())) return FieldAssessment.ask(f.confidence(), f.basis());
        return FieldAssessment.of(f.confidence(), f.basis());
    }

    /**
     * Combines the vision model's reading with the Tesseract pipeline's. Agreement raises
     * confidence; a total the model reports that Tesseract read nowhere on the receipt stays
     * a suggestion; two well-supported but different totals are offered to the user.
     */
    static OcrExtractionResult crossCheck(OcrExtractionResult llm, OcrExtractionResult tess) {
        if (tess == null) return llm;
        Map<String, FieldAssessment> fields = new LinkedHashMap<>(llm.getFields());
        OcrExtractionResult.OcrExtractionResultBuilder out = llm.toBuilder();

        FieldAssessment tessAmount = tess.getFields().get(ReceiptParserService.FIELD_AMOUNT);
        BigDecimal la = llm.getAmount();
        BigDecimal ta = tess.getAmount();
        List<ReceiptAmountOption> options = new ArrayList<>();
        if (la != null) options.add(new ReceiptAmountOption(la, null, "llm"));
        if (tess.getAmountCandidates() != null) {
            tess.getAmountCandidates().stream().filter(o -> la == null || o.amount().compareTo(la) != 0).forEach(options::add);
        }
        if (la == null) {
            out.amount(ta);
            fields.put(ReceiptParserService.FIELD_AMOUNT, tessAmount);
        } else if (ta != null && la.compareTo(ta) == 0) {
            fields.put(ReceiptParserService.FIELD_AMOUNT, FieldAssessment.of(Math.max(0.92, tessAmount.confidence()), "llm+" + tessAmount.basis()));
        } else if (readByTesseract(la, tess)) {
            fields.put(ReceiptParserService.FIELD_AMOUNT, FieldAssessment.of(0.80, "llm+tesseract-read"));
        } else if (ta != null && tessAmount.decision() == ReceiptDecision.ACCEPT) {
            out.amount(ta);
            fields.put(ReceiptParserService.FIELD_AMOUNT, FieldAssessment.ask(0.45, "conflict:llm-vs-" + tessAmount.basis()));
        } else {
            fields.put(ReceiptParserService.FIELD_AMOUNT, FieldAssessment.of(Math.min(0.60, llm.getConfidence() == null ? 0.6 : llm.getConfidence()), "llm-unverified"));
        }
        out.amountCandidates(options.stream().limit(4).toList());

        if (llm.getMerchant() == null) {
            out.merchant(tess.getMerchant()).merchantRaw(tess.getMerchantRaw());
            fields.put(ReceiptParserService.FIELD_MERCHANT, tess.getFields().get(ReceiptParserService.FIELD_MERCHANT));
        } else if (tess.getMerchant() != null && squash(tess.getMerchant()).equals(squash(llm.getMerchant()))) {
            fields.put(ReceiptParserService.FIELD_MERCHANT, FieldAssessment.of(0.92, "llm+tesseract"));
        }

        if (llm.getDate() == null) {
            out.date(tess.getDate());
            fields.put(ReceiptParserService.FIELD_DATE, tess.getFields().get(ReceiptParserService.FIELD_DATE));
        } else if (llm.getDate().equals(tess.getDate())) {
            fields.put(ReceiptParserService.FIELD_DATE, FieldAssessment.of(0.95, "llm+tesseract"));
        }

        if (tess.getCurrency() != null) {
            out.currency(tess.getCurrency());
            fields.put(ReceiptParserService.FIELD_CURRENCY, tess.getFields().get(ReceiptParserService.FIELD_CURRENCY));
        }
        if (llm.getLineItems() == null || llm.getLineItems().isEmpty()) out.lineItems(tess.getLineItems());
        out.subtotal(tess.getSubtotal()).taxes(tess.getTaxes()).discounts(tess.getDiscounts())
            .tip(tess.getTip()).roundOff(tess.getRoundOff()).itemsReconciled(tess.getItemsReconciled());
        if (tess.getRawText() != null && !tess.getRawText().isBlank()) out.rawText(tess.getRawText());

        ReceiptDecision decision = worst(fields);
        return out.fields(fields)
            .decision(decision)
            .requiresConfirmation(decision == ReceiptDecision.ASK_USER)
            .confidence(fields.get(ReceiptParserService.FIELD_AMOUNT).confidence())
            .successful(out.build().getAmount() != null)
            .build();
    }

    private static boolean readByTesseract(BigDecimal value, OcrExtractionResult tess) {
        String text = tess.getRawText() == null ? "" : tess.getRawText();
        for (String line : text.split("\n")) {
            for (MoneyParser.Amount a : MoneyParser.findAmounts(line)) {
                if (a.value().compareTo(value) == 0) return true;
            }
        }
        return false;
    }

    private static ReceiptDecision worst(Map<String, FieldAssessment> fields) {
        ReceiptDecision d = ReceiptDecision.ACCEPT;
        for (FieldAssessment f : fields.values()) d = d.worst(f.decision());
        return d;
    }

    private static List<ReceiptChargeLine> charges(List<MlOcrResult.Charge> charges) {
        if (charges == null) return List.of();
        return charges.stream().filter(c -> c.amount() != null).map(c -> new ReceiptChargeLine(c.label(), c.amount().abs())).toList();
    }

    private static BigDecimal positive(BigDecimal v) {
        return v != null && v.signum() > 0 ? v.setScale(2, java.math.RoundingMode.HALF_UP) : null;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
