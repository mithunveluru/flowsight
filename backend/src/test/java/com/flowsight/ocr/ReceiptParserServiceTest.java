package com.flowsight.ocr;

import com.flowsight.dto.receipt.OcrExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParserServiceTest {

    // Stub AITransactionInterpreter that never calls out — safe default for most tests
    private static final AITransactionInterpreter NO_AI = (text, candidates) -> java.util.Optional.empty();

    // Stub that always returns a fixed merchant — used to verify AI override logic
    private static AITransactionInterpreter stubbedAI(String merchant) {
        return (text, candidates) -> java.util.Optional.of(
            AIInterpretation.builder().merchant(merchant).confidence(0.90).build()
        );
    }

    private ReceiptParserService parser;

    @BeforeEach
    void setUp() {
        parser = new ReceiptParserService(new MerchantExtractor(), NO_AI);
    }

    // Amount extraction

    @Test
    void extractTotal_grandTotalLabel() {
        String text = "SUBTOTAL   45.00\nGRAND TOTAL  51.75\n";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("51.75");
    }

    @Test
    void extractTotal_netPayableLabel() {
        String text = "Items 3\nNet Payable: 123.40";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("123.40");
    }

    @Test
    void extractTotal_totalLabel() {
        String text = "Milk 30.00\nBread 20.00\nTotal: 50.00";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("50.00");
    }

    @Test
    void extractTotal_fallbackToLargestAmount() {
        String text = "Small item 5.00\nLarge item 95.50\nAnother 12.00";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("95.50");
    }

    @Test
    void extractTotal_currencySymbol() {
        String text = "Coffee ₹120.00\nPastry ₹80.00\nTotal ₹200.00";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("200.00");
    }

    @Test
    void extractTotal_commaSeparatedAmount() {
        String text = "TOTAL: 1,250.00";
        assertThat(parser.extractTotal(text)).isEqualByComparingTo("1250.00");
    }

    @Test
    void extractTotal_returnsNullWhenNoAmount() {
        String text = "Welcome to our store\nThank you for your visit";
        assertThat(parser.extractTotal(text)).isNull();
    }

    // Date extraction

    @Test
    void extractDate_slashFormat_ddMMyyyy() {
        String text = "Date: 25/12/2024\nTotal 50.00";
        assertThat(parser.extractDate(text)).isEqualTo(LocalDate.of(2024, 12, 25));
    }

    @Test
    void extractDate_isoFormat() {
        String text = "Transaction Date: 2024-03-15\nAmount 75.00";
        assertThat(parser.extractDate(text)).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void extractDate_monthNameFormat() {
        String text = "Receipt Date: 5 Jan 2024\nGrand Total 200.00";
        assertThat(parser.extractDate(text)).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    @Test
    void extractDate_rejectsDateInFuture() {
        String text = "Date: 01/01/2099\nTotal 50.00";
        // future date should be rejected
        assertThat(parser.extractDate(text)).isNull();
    }

    @Test
    void extractDate_returnsNullWhenAbsent() {
        String text = "WALMART\n123 Main St\nTotal 45.00";
        assertThat(parser.extractDate(text)).isNull();
    }

    // Full parse — OcrDocument overload

    @Test
    void parse_document_successfulWhenAmountPresent() {
        OcrDocument doc = OcrDocument.fromPlainText(
            "AMAZON\n" +
            "Order #123456\n" +
            "USB Cable       12.99\n" +
            "TOTAL           12.99"
        );
        OcrExtractionResult result = parser.parse(doc);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("12.99");
    }

    @Test
    void parse_document_notSuccessfulWhenNoAmount() {
        OcrDocument doc = OcrDocument.fromPlainText(
            "SOME STORE\nWelcome to our shop\nThank you for visiting"
        );
        OcrExtractionResult result = parser.parse(doc);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getAmount()).isNull();
    }

    @Test
    void parse_string_backwardCompatible() {
        String text = "ZOMATO\n15/03/2024\nBiryani 180.00\nGrand Total 180.00";
        OcrExtractionResult result = parser.parse(text);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("180.00");
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    void parse_null_returnsUnsuccessful() {
        assertThat(parser.parse((String) null).isSuccessful()).isFalse();
        assertThat(parser.parse("").isSuccessful()).isFalse();
    }

    // Merchant extracted through structured document

    @Test
    void parse_structuredDoc_extractsMerchantFromTopLine() {
        // Simulate structured OcrDocument with position data
        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            OcrLine.builder().text("BIGBASKET").confidence(0.94).topPx(5).documentHeightPx(800).build(),
            OcrLine.builder().text("123 MG Road").confidence(0.88).topPx(30).documentHeightPx(800).build(),
            OcrLine.builder().text("Tel: 9876543210").confidence(0.85).topPx(55).documentHeightPx(800).build(),
            OcrLine.builder().text("Organic Milk   85.00").confidence(0.90).topPx(200).documentHeightPx(800).build(),
            OcrLine.builder().text("TOTAL   85.00").confidence(0.96).topPx(600).documentHeightPx(800).build()
        )).build();

        OcrExtractionResult result = parser.parse(doc);
        assertThat(result.getMerchant()).isEqualTo("BIGBASKET");
        assertThat(result.isSuccessful()).isTrue();
    }

    // OcrService TSV parser (via package-access)

    @Test
    void ocrService_parseTsv_extractsLinesFromSampleOutput() {
        OcrService ocrService = new OcrService(new OcrPreprocessor());

        // Minimal Tesseract TSV sample (header + page row + two word rows)
        String tsv = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
            + "1\t1\t0\t0\t0\t0\t0\t0\t1080\t1920\t-1\t\n"
            + "5\t1\t1\t1\t1\t1\t7\t10\t200\t30\t95\tWALMART\n"
            + "5\t1\t1\t1\t2\t1\t7\t45\t350\t25\t88\t123\n"
            + "5\t1\t1\t1\t2\t2\t120\t45\t150\t25\t87\tMAIN\n"
            + "5\t1\t1\t1\t2\t3\t275\t45\t120\t25\t86\tSTREET\n";

        var lines = ocrService.parseTsv(tsv);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getText()).isEqualTo("WALMART");
        assertThat(lines.get(0).getConfidence()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
        assertThat(lines.get(0).getTopPx()).isEqualTo(10);
        assertThat(lines.get(0).getDocumentHeightPx()).isEqualTo(1920);

        assertThat(lines.get(1).getText()).isEqualTo("123 MAIN STREET");
        assertThat(lines.get(1).getTopPx()).isEqualTo(45);
    }

    @Test
    void ocrService_parseTsv_emptyInputReturnsEmptyList() {
        OcrService ocrService = new OcrService(new OcrPreprocessor());
        assertThat(ocrService.parseTsv("")).isEmpty();
        assertThat(ocrService.parseTsv(null)).isEmpty();
    }

    // AI fallback integration

    @Test
    void aiOverride_invokedWhenHeuristicIsLowConfidence() {
        // Build a document where heuristic confidence will be low:
        // only line is mid-document, mixed-case, short — scores < 0.40
        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            OcrLine.builder().text("cafe").confidence(0.40).topPx(500).documentHeightPx(1000).build(),
            OcrLine.builder().text("TOTAL 12.00").confidence(0.95).topPx(900).documentHeightPx(1000).build()
        )).build();

        ReceiptParserService aiParser = new ReceiptParserService(new MerchantExtractor(), stubbedAI("BLUE BOTTLE COFFEE"));
        OcrExtractionResult result = aiParser.parse(doc);

        assertThat(result.getMerchant()).isEqualTo("BLUE BOTTLE COFFEE");
    }

    @Test
    void aiNotInvoked_whenHeuristicIsHighConfidence() {
        // High-confidence candidate: ALL CAPS at top of doc, high OCR confidence
        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            OcrLine.builder().text("AMAZON").confidence(0.96).topPx(5).documentHeightPx(800).build(),
            OcrLine.builder().text("Order 123").confidence(0.88).topPx(40).documentHeightPx(800).build(),
            OcrLine.builder().text("TOTAL 25.00").confidence(0.97).topPx(700).documentHeightPx(800).build()
        )).build();

        // AI stub would set "WRONG MERCHANT" — but should NOT be called
        ReceiptParserService aiParser = new ReceiptParserService(new MerchantExtractor(), stubbedAI("WRONG MERCHANT"));
        OcrExtractionResult result = aiParser.parse(doc);

        // Heuristic should win: AMAZON has a high score, does not needsAI()
        assertThat(result.getMerchant()).isEqualTo("AMAZON");
    }

    @Test
    void aiNotInvoked_whenAiReturnsEmpty() {
        OcrDocument doc = OcrDocument.fromPlainText("ZOMATO\nFood delivery\nTOTAL 180.00");
        // NO_AI always returns empty — heuristic result used
        OcrExtractionResult result = parser.parse(doc);
        assertThat(result.getMerchant()).isEqualTo("ZOMATO");
    }

    // OCR corruption detection

    @Test
    void isOcrCorrupted_detectsHighNonAsciiRatio() {
        String garbled = "WALMART\nÿþýüû".repeat(10);
        assertThat(ReceiptParserService.isOcrCorrupted(garbled)).isTrue();
    }

    @Test
    void isOcrCorrupted_detectsRepeatedCharRuns() {
        String noisy = "WALLLLLLMART\nTOTAL 50.00";
        assertThat(ReceiptParserService.isOcrCorrupted(noisy)).isTrue();
    }

    @Test
    void isOcrCorrupted_detectsFragmentedWords() {
        // Average word length 1 — completely fragmented OCR
        String fragmented = "W A L M A R T\nT O T A L\n5 0 . 0 0";
        assertThat(ReceiptParserService.isOcrCorrupted(fragmented)).isTrue();
    }

    @Test
    void isOcrCorrupted_returnsFalseForCleanText() {
        String clean = "WALMART SUPERCENTER\n123 Main St\nTOTAL $45.00";
        assertThat(ReceiptParserService.isOcrCorrupted(clean)).isFalse();
    }

    @Test
    void corrupted_triggersFallbackToAI() {
        // Corrupted OCR text should invoke AI even if heuristic found something
        String corruptedText = "AAAAAAAAA BBBBBBB\nCCCCC DDDDD\nTotal 99.00";
        OcrDocument doc = OcrDocument.fromPlainText(corruptedText);

        ReceiptParserService aiParser = new ReceiptParserService(new MerchantExtractor(), stubbedAI("BESTBUY"));
        OcrExtractionResult result = aiParser.parse(doc);
        assertThat(result.getMerchant()).isEqualTo("BESTBUY");
    }

    // MerchantExtractor — extractWithScore + findCandidates

    @Test
    void extractWithScore_returnsScoreAndAmbiguity() {
        MerchantExtractor me = new MerchantExtractor();

        // Two closely-scored candidates → ambiguous
        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            OcrLine.builder().text("ZOMATO").confidence(0.90).topPx(5).documentHeightPx(800).build(),
            OcrLine.builder().text("SWIGGY").confidence(0.89).topPx(15).documentHeightPx(800).build(),
            OcrLine.builder().text("TOTAL 50.00").confidence(0.97).topPx(700).documentHeightPx(800).build()
        )).build();

        MerchantCandidate candidate = me.extractWithScore(doc);
        assertThat(candidate).isNotNull();
        assertThat(candidate.getScore()).isGreaterThan(0.0);
        assertThat(candidate.isAmbiguous()).isTrue();  // ZOMATO vs SWIGGY are close in score
    }

    @Test
    void extractWithScore_notAmbiguousWhenClearWinner() {
        MerchantExtractor me = new MerchantExtractor();

        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            // Clear winner: top of doc, ALL CAPS, high confidence
            OcrLine.builder().text("WALMART").confidence(0.97).topPx(5).documentHeightPx(800).build(),
            // Weak second: middle of doc, low confidence
            OcrLine.builder().text("store rewards").confidence(0.50).topPx(500).documentHeightPx(800).build()
        )).build();

        MerchantCandidate candidate = me.extractWithScore(doc);
        assertThat(candidate).isNotNull();
        assertThat(candidate.isAmbiguous()).isFalse();
    }

    @Test
    void findCandidates_returnsRankedNormalizedNames() {
        MerchantExtractor me = new MerchantExtractor();

        OcrDocument doc = OcrDocument.builder().lines(java.util.List.of(
            OcrLine.builder().text("WAL-MART").confidence(0.95).topPx(5).documentHeightPx(600).build(),
            OcrLine.builder().text("STARBUCKS COFFEE").confidence(0.90).topPx(40).documentHeightPx(600).build(),
            OcrLine.builder().text("TOTAL 30.00").confidence(0.99).topPx(500).documentHeightPx(600).build()
        )).build();

        java.util.List<String> candidates = me.findCandidates(doc, 3);
        assertThat(candidates).containsExactly("WALMART", "STARBUCKS");
    }
}
