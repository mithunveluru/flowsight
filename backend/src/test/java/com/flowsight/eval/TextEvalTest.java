package com.flowsight.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.CategorizationService;
import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.analytics.NormalizationService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Writes predictions of the current categorization and merchant normalization code for the
 * labeled CSVs under eval/. Skipped unless -Deval.categorization.input / -Deval.merchants.input
 * are set. Driven by eval/run.sh.
 */
class TextEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final NormalizationService normalization = new NormalizationService();
    private final CategorizationService categorization = new CategorizationService();
    private final MerchantNormalizationService merchantNormalization = new MerchantNormalizationService();

    @Test
    void writesCategorizationPredictions() throws Exception {
        String input = System.getProperty("eval.categorization.input", "");
        assumeTrue(!input.isBlank(), "categorization eval runs only with -Deval.categorization.input=<cases.csv>");

        Path output = Path.of(System.getProperty("eval.categorization.output", "target/eval/categorization.jsonl"));
        int n = writeJsonl(Path.of(input), output, row -> {
            // same inputs as TransactionIngestionPipeline.processCsvRow
            String description = row.get("description");
            String merchant = row.get("merchant").isBlank()
                ? normalization.extractMerchant(description)
                : row.get("merchant");
            CategorizationService.CategorizationResult result = categorization.categorize(
                normalization.normalize(description), merchant, new BigDecimal(row.get("amount")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("category", result.getCategory().name());
            out.put("confidence", result.getConfidence());
            out.put("merchant", merchant);
            return out;
        });
        System.out.printf("categorization eval: %d rows -> %s%n", n, output);
    }

    @Test
    void writesMerchantPredictions() throws Exception {
        String input = System.getProperty("eval.merchants.input", "");
        assumeTrue(!input.isBlank(), "merchant eval runs only with -Deval.merchants.input=<cases.csv>");

        Path output = Path.of(System.getProperty("eval.merchants.output", "target/eval/merchants.jsonl"));
        int n = writeJsonl(Path.of(input), output, row -> {
            MerchantNormalizationService.Normalized normalized = merchantNormalization.normalize(row.get("raw"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("canonical", normalized.getCanonicalName());
            out.put("key", normalized.getKey());
            out.put("expected_key", merchantNormalization.normalizeKey(row.get("expected")));
            out.put("extracted", normalization.extractMerchant(row.get("raw")));
            return out;
        });
        System.out.printf("merchant eval: %d rows -> %s%n", n, output);
    }

    private interface RowScorer {
        Map<String, Object> apply(CSVRecord row);
    }

    private static int writeJsonl(Path input, Path output, RowScorer scorer) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        int n = 0;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setCommentMarker('#').build();
        try (Reader in = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (CSVRecord row : format.parse(in)) {
                Map<String, Object> prediction = new LinkedHashMap<>();
                prediction.put("id", row.get("id"));
                prediction.putAll(scorer.apply(row));
                out.write(JSON.writeValueAsString(prediction));
                out.newLine();
                n++;
            }
        }
        return n;
    }
}
