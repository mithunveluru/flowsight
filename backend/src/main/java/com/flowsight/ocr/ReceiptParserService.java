package com.flowsight.ocr;

import com.flowsight.dto.receipt.OcrExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Parses an OcrDocument into structured receipt data (amount, date, merchant).
// Merchant uses the heuristic extractor, escalating to AI on low-confidence/ambiguous/corrupt OCR.
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptParserService {

    private final MerchantExtractor       merchantExtractor;
    private final AITransactionInterpreter aiInterpreter;

    // pre-compiled: String.matches() won't cross \n
    private static final Pattern REPEATED_CHAR_PATTERN =
        Pattern.compile("([A-Za-z0-9])\\1{4,}");

    // total-amount patterns, priority-ordered
    private static final List<Pattern> TOTAL_PATTERNS = List.of(
        Pattern.compile(
            "(?:grand\\s*total|net\\s*payable|amount\\s*payable|total\\s*payable|bill\\s*total|final\\s*total)" +
            "[^\\d₹$]*[₹$]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "total\\s*[:\\-]?\\s*[₹$]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "[₹$]\\s*([0-9,]+\\.[0-9]{2})\\s*$",
            Pattern.MULTILINE)
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("d MMMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd MMMM yyyy")
    );

    private static final List<Pattern> DATE_PATTERNS = List.of(
        Pattern.compile("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4})\\b"),
        Pattern.compile("\\b(\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})\\b"),
        Pattern.compile(
            "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\w*\\s+\\d{4})\\b",
            Pattern.CASE_INSENSITIVE)
    );

    // preferred: uses per-line position/confidence
    public OcrExtractionResult parse(OcrDocument document) {
        String text = document.plainText();
        if (text.isBlank()) {
            return OcrExtractionResult.builder().successful(false).rawText("").build();
        }

        BigDecimal amount = extractTotal(text);
        LocalDate  date   = extractDate(text);
        String merchant   = resolveMerchant(document, text);

        log.debug("Parse result: merchant='{}' amount={} date={}", merchant, amount, date);

        return OcrExtractionResult.builder()
            .amount(amount)
            .date(date != null ? date : LocalDate.now())
            .merchant(merchant)
            .currency("INR")
            .successful(amount != null)
            .rawText(text)
            .build();
    }

    // wraps plain text in a synthetic OcrDocument
    public OcrExtractionResult parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return OcrExtractionResult.builder().successful(false).rawText("").build();
        }
        return parse(OcrDocument.fromPlainText(ocrText));
    }

    // merchant: heuristic first, AI on ambiguity/low-confidence/corruption
    private String resolveMerchant(OcrDocument document, String text) {
        MerchantCandidate candidate = merchantExtractor.extractWithScore(document);
        String merchant = candidate != null ? candidate.getName() : null;

        boolean corrupted = isOcrCorrupted(text);
        boolean shouldAsk = corrupted || (candidate != null && candidate.needsAI());

        if (shouldAsk) {
            List<String> hints = merchantExtractor.findCandidates(document, 3);
            String limited = GroqAIProvider.limitLines(text);
            Optional<AIInterpretation> ai = aiInterpreter.interpret(limited, hints);
            if (ai.isPresent()) {
                log.debug("AI overriding heuristic '{}' → '{}'", merchant, ai.get().getMerchant());
                merchant = ai.get().getMerchant();
            }
        }

        return merchant;
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
        for (Pattern pattern : TOTAL_PATTERNS) {
            Matcher m = pattern.matcher(text);
            String lastMatch = null;
            while (m.find()) lastMatch = m.group(1);
            if (lastMatch != null) {
                BigDecimal parsed = parseMoney(lastMatch);
                if (parsed != null) return parsed;
            }
        }
        return findLargestAmount(text);
    }

    private BigDecimal findLargestAmount(String text) {
        Pattern anyAmount = Pattern.compile("[₹$]?\\s*([0-9,]+\\.[0-9]{2})");
        Matcher m = anyAmount.matcher(text);
        BigDecimal largest = null;
        while (m.find()) {
            BigDecimal val = parseMoney(m.group(1));
            if (val != null && (largest == null || val.compareTo(largest) > 0)) largest = val;
        }
        return largest;
    }

    LocalDate extractDate(String text) {
        for (Pattern datePattern : DATE_PATTERNS) {
            Matcher m = datePattern.matcher(text);
            if (m.find()) {
                LocalDate date = tryParseDate(m.group(1));
                if (date != null && !date.isAfter(LocalDate.now())) return date;
            }
        }
        return null;
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.replaceAll(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDate tryParseDate(String raw) {
        String cleaned = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(cleaned, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
