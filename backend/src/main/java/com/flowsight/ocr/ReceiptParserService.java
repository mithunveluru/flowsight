package com.flowsight.ocr;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptChargeLine;
import com.flowsight.dto.receipt.ReceiptDecision;
import com.flowsight.dto.receipt.ReceiptLineItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;


// Turns one or more OCR passes of the same receipt into structured receipt data with a
// confidence and review decision per field. The total comes from ReceiptReconciler; the merchant
// from the heuristic extractor, escalating to the LLM interpreter (redacted text, answer
// verified against the OCR text) when it is low-confidence or ambiguous.
@Service
@Slf4j
public class ReceiptParserService {

    private final MerchantExtractor       merchantExtractor;
    private final AITransactionInterpreter aiInterpreter;
    private final ReceiptTextParser        textParser;
    private final ReceiptReconciler        reconciler;

    @Autowired
    public ReceiptParserService(
        MerchantExtractor merchantExtractor, AITransactionInterpreter aiInterpreter,
        ReceiptTextParser textParser, ReceiptReconciler reconciler
    ) {
        this.merchantExtractor = merchantExtractor;
        this.aiInterpreter = aiInterpreter;
        this.textParser = textParser;
        this.reconciler = reconciler;
    }

    public ReceiptParserService(MerchantExtractor merchantExtractor, AITransactionInterpreter aiInterpreter) {
        this(merchantExtractor, aiInterpreter, new ReceiptTextParser(merchantExtractor), new ReceiptReconciler());
    }

    // pre-compiled: String.matches() won't cross \n
    private static final Pattern REPEATED_CHAR_PATTERN =
        Pattern.compile("([A-Za-z0-9])\\1{4,}");

    public static final String FIELD_MERCHANT = "merchant";
    public static final String FIELD_AMOUNT   = "amount";
    public static final String FIELD_DATE     = "date";
    public static final String FIELD_CURRENCY = "currency";

    public ReceiptTextParser.Parsed read(OcrDocument document) {
        return textParser.read(document);
    }

    // preferred: uses per-line position/confidence
    public OcrExtractionResult parse(OcrDocument document) {
        if (document.plainText().isBlank()) {
            return empty();
        }
        return assemble(List.of(textParser.read(document)));
    }

    // wraps plain text in a synthetic OcrDocument
    public OcrExtractionResult parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return empty();
        }
        return parse(OcrDocument.fromPlainText(ocrText));
    }

    public ReceiptReconciler.TotalAssessment reconcile(List<ReceiptTextParser.Parsed> passes) {
        return reconciler.reconcile(passes);
    }

    /** Merges passes of one receipt: total by reconciliation, the other fields by score and agreement. */
    public OcrExtractionResult assemble(List<ReceiptTextParser.Parsed> passes) {
        if (passes.isEmpty()) return empty();
        ReceiptReconciler.TotalAssessment total = reconciler.reconcile(passes);
        ReceiptTextParser.Parsed primary = passes.get(total.pass() >= 0 ? total.pass() : bestTextPass(passes));

        CurrencyDetector.Guess currency = currency(passes);
        FieldAssessment currencyField = currency.code() == null
            ? FieldAssessment.missing()
            : FieldAssessment.of(currency.confidence(), currency.cue());

        DatePick date = date(passes, currency.code());
        MerchantPick merchant = merchant(passes, primary);

        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        fields.put(FIELD_MERCHANT, merchant.assessment());
        fields.put(FIELD_AMOUNT, total.assessment());
        fields.put(FIELD_DATE, date.assessment());
        fields.put(FIELD_CURRENCY, currencyField);
        ReceiptDecision decision = ReceiptDecision.ACCEPT;
        for (FieldAssessment f : fields.values()) decision = decision.worst(f.decision());

        log.debug("Parse result: passes={} amountBasis={} decision={}", passes.size(), total.assessment().basis(), decision);

        return OcrExtractionResult.builder()
            .amount(total.value())
            .date(date.date())
            .merchant(merchant.name())
            .merchantRaw(merchant.raw())
            .currency(currency.code())
            .successful(total.value() != null)
            .rawText(primary.text())
            .lineItems(lineItems(primary))
            .confidence(total.assessment().confidence())
            .requiresConfirmation(decision == ReceiptDecision.ASK_USER)
            .subtotal(first(primary, ReceiptLabels.Kind.SUBTOTAL))
            .taxes(charges(primary, ReceiptLabels.Kind.TAX, ReceiptLabels.Kind.TAX_TOTAL))
            .discounts(charges(primary, ReceiptLabels.Kind.DISCOUNT))
            .tip(first(primary, ReceiptLabels.Kind.TIP))
            .roundOff(first(primary, ReceiptLabels.Kind.ROUND_OFF))
            .itemsReconciled(total.itemsReconciled())
            .amountCandidates(total.options())
            .fields(fields)
            .decision(decision)
            .build();
    }

    private record MerchantPick(String name, String raw, FieldAssessment assessment) {}

    private record DatePick(LocalDate date, FieldAssessment assessment) {}

    // merchant: best heuristic line across passes, AI on ambiguity/low-confidence/corruption
    private MerchantPick merchant(List<ReceiptTextParser.Parsed> passes, ReceiptTextParser.Parsed primary) {
        Map<String, List<MerchantCandidate>> byName = new LinkedHashMap<>();
        for (ReceiptTextParser.Parsed p : passes) {
            if (p.merchant() != null) {
                byName.computeIfAbsent(p.merchant().getName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p.merchant());
            }
        }
        MerchantCandidate best = null;
        int agreeing = 0;
        double bestScore = -1;
        for (List<MerchantCandidate> group : byName.values()) {
            MerchantCandidate top = group.stream().max(Comparator.comparingDouble(MerchantCandidate::getScore)).orElseThrow();
            double s = top.getScore() + 0.05 * (group.size() - 1);
            if (s > bestScore) {
                bestScore = s;
                best = top;
                agreeing = group.size();
            }
        }

        double confidence = best == null ? 0.0 : merchantConfidence(best, agreeing);
        boolean corrupted = isOcrCorrupted(primary.text());
        if (best == null || confidence < ReceiptDecision.HIGHLIGHT_AT || best.isAmbiguous() || corrupted) {
            Optional<String> ai = askInterpreter(primary, passes);
            if (ai.isPresent()) {
                log.debug("AI merchant accepted after verification against OCR text");
                return new MerchantPick(merchantExtractor.normalize(ai.get()), ai.get(),
                    FieldAssessment.of(Math.max(confidence, 0.70), "llm-verified"));
            }
        }
        if (best == null) return new MerchantPick(null, null, FieldAssessment.missing());
        return new MerchantPick(best.getName(), best.getRawText(),
            FieldAssessment.of(confidence, agreeing > 1 ? "heuristic+" + agreeing + "-passes" : "heuristic"));
    }

    // Line score to probability; bands checked against the synthetic eval set (eval/run.sh).
    static double merchantConfidence(MerchantCandidate c, int agreeingPasses) {
        double s = c.getScore();
        double p;
        if (s >= 0.95) p = 0.93;
        else if (s >= 0.80) p = 0.86;
        else if (s >= 0.60) p = 0.72;
        else if (s >= 0.40) p = 0.50;
        else p = 0.30;
        if (c.isAmbiguous()) p -= 0.10;
        if (agreeingPasses > 1) p += 0.04;
        return Math.max(0.0, Math.min(0.97, p));
    }

    private Optional<String> askInterpreter(ReceiptTextParser.Parsed primary, List<ReceiptTextParser.Parsed> passes) {
        List<String> hints = new ArrayList<>();
        for (ReceiptTextParser.Parsed p : passes) {
            if (p.merchant() != null && !hints.contains(p.merchant().getName())) hints.add(p.merchant().getName());
        }
        String limited = PiiRedactor.redact(GroqAIProvider.limitLines(primary.text()));
        Optional<AIInterpretation> ai = aiInterpreter.interpret(limited, hints.stream().limit(3).toList());
        if (ai.isEmpty() || ai.get().getMerchant() == null) return Optional.empty();
        String answer = ai.get().getMerchant().trim();
        // the interpreter may only pick a name that is actually printed on the receipt
        String needle = squash(answer);
        if (needle.length() < 2) return Optional.empty();
        for (ReceiptTextParser.Parsed p : passes) {
            for (String line : p.lines()) {
                if (squash(line).contains(needle)) return Optional.of(answer);
            }
        }
        return Optional.empty();
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static CurrencyDetector.Guess currency(List<ReceiptTextParser.Parsed> passes) {
        CurrencyDetector.Guess best = null;
        for (ReceiptTextParser.Parsed p : passes) {
            CurrencyDetector.Guess g = p.currency();
            if (g.code() != null && (best == null || g.confidence() > best.confidence())) best = g;
        }
        if (best == null) return new CurrencyDetector.Guess(null, 0.0, null);
        final String code = best.code();
        long disagreeing = passes.stream().filter(p -> p.currency().code() != null && !code.equals(p.currency().code())).count();
        return disagreeing == 0 ? best : new CurrencyDetector.Guess(code, Math.min(best.confidence(), 0.50), "passes-disagree");
    }

    private static DatePick date(List<ReceiptTextParser.Parsed> passes, String currency) {
        Map<LocalDate, List<ReceiptDates.Guess>> byDate = new LinkedHashMap<>();
        for (ReceiptTextParser.Parsed p : passes) {
            // re-read with the merged currency so a pass that missed the cue uses the same day/month order
            ReceiptDates.Guess g = ReceiptDates.find(p.lines(), currency);
            if (g.date() != null) byDate.computeIfAbsent(g.date(), k -> new ArrayList<>()).add(g);
        }
        if (byDate.isEmpty()) return new DatePick(null, FieldAssessment.missing());
        Map.Entry<LocalDate, List<ReceiptDates.Guess>> top = byDate.entrySet().stream()
            .max(Comparator.<Map.Entry<LocalDate, List<ReceiptDates.Guess>>>comparingInt(e -> e.getValue().size())
                .thenComparing(e -> e.getValue().stream().anyMatch(ReceiptDates.Guess::labelled)))
            .orElseThrow();
        boolean labelled = top.getValue().stream().anyMatch(ReceiptDates.Guess::labelled);
        boolean ambiguous = top.getValue().stream().allMatch(ReceiptDates.Guess::ambiguous);
        double c;
        String basis;
        if (ambiguous) {
            c = currency == null ? 0.45 : 0.70;
            basis = currency == null ? "day-month-order-unknown" : "day-month-order-from-" + currency;
        } else {
            c = labelled ? 0.95 : 0.88;
            basis = labelled ? "labelled" : "pattern";
        }
        if (byDate.size() > 1) {
            c = Math.min(c, 0.55);
            basis = "passes-disagree";
        } else if (top.getValue().size() > 1 && c < 0.97) {
            c += 0.02;
        }
        return new DatePick(top.getKey(), FieldAssessment.of(c, basis));
    }

    private static int bestTextPass(List<ReceiptTextParser.Parsed> passes) {
        int best = 0;
        for (int i = 1; i < passes.size(); i++) {
            if (passes.get(i).meanConfidence() > passes.get(best).meanConfidence()) best = i;
        }
        return best;
    }

    private static List<ReceiptLineItem> lineItems(ReceiptTextParser.Parsed p) {
        return p.items().stream()
            .map(i -> ReceiptLineItem.builder()
                .itemName(i.name())
                .itemQuantity(i.quantity() != null ? i.quantity().toString() : null)
                .itemPrice(i.amount())
                .build())
            .toList();
    }

    private static BigDecimal first(ReceiptTextParser.Parsed p, ReceiptLabels.Kind kind) {
        return p.ofKind(kind).stream().map(ReceiptTextParser.AmountLine::value).findFirst().orElse(null);
    }

    private static List<ReceiptChargeLine> charges(ReceiptTextParser.Parsed p, ReceiptLabels.Kind... kinds) {
        List<ReceiptLabels.Kind> wanted = Arrays.asList(kinds);
        return p.amounts().stream()
            .filter(a -> wanted.contains(a.kind()))
            .map(a -> new ReceiptChargeLine(ReceiptReconciler.label(a.text()), a.value().abs()))
            .toList();
    }

    private static OcrExtractionResult empty() {
        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        for (String f : List.of(FIELD_MERCHANT, FIELD_AMOUNT, FIELD_DATE, FIELD_CURRENCY)) fields.put(f, FieldAssessment.missing());
        return OcrExtractionResult.builder()
            .successful(false)
            .rawText("")
            .lineItems(List.of())
            .fields(fields)
            .decision(ReceiptDecision.ASK_USER)
            .requiresConfirmation(true)
            .build();
    }

    // garbled-OCR check: >15% non-printable, a char repeated 5+ times, or mean word length < 2
    static boolean isOcrCorrupted(String text) {
        if (text == null || text.isBlank()) return false;

        long nonPrintable = text.chars()
            .filter(c -> c > 127 || (c < 32 && c != '\n' && c != '\r' && c != '\t'))
            .count();
        if ((double) nonPrintable / text.length() > 0.15) return true;

        if (REPEATED_CHAR_PATTERN.matcher(text).find()) return true;

        String[] words = text.split("\\s+");
        double avgLen = Arrays.stream(words)
            .mapToInt(String::length)
            .average()
            .orElse(5.0);
        return avgLen < 2.0;
    }

    BigDecimal extractTotal(String text) {
        return reconciler.reconcile(List.of(textParser.read(OcrDocument.fromPlainText(text)))).value();
    }

    LocalDate extractDate(String text) {
        List<String> lines = List.of(text.split("\\r?\\n"));
        return ReceiptDates.find(lines, CurrencyDetector.detect(text).code()).date();
    }
}
