package com.flowsight.eval;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the receipt golden set through {@link CurrentReceiptPipeline} and writes one JSON line
 * of predictions per receipt. Skipped unless -Deval.input points at a manifest (JSONL with
 * "id" and "image", image paths relative to the manifest). Driven by eval/run.sh.
 * -Deval.mlServiceUrl (token from env ML_SERVICE_TOKEN) scores ml-service OCR.
 */
class ReceiptEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    @Test
    void writesPredictionsForManifest() throws Exception {
        String input = System.getProperty("eval.input", "");
        assumeTrue(!input.isBlank(), "receipt eval runs only with -Deval.input=<manifest.jsonl>");

        Path manifest = Path.of(input);
        Path output = Path.of(System.getProperty("eval.output", "target/eval/receipt-predictions.jsonl"));
        String receiptOcrUrl = System.getProperty("eval.receiptOcrUrl", "");
        String groqApiKey = System.getenv().getOrDefault("GROQ_API_KEY", "");
        String mlServiceUrl = System.getProperty("eval.mlServiceUrl", "");
        String mlServiceToken = System.getenv().getOrDefault("ML_SERVICE_TOKEN", "");
        boolean llm = !receiptOcrUrl.isBlank();
        // receipt-ocr is rate limited upstream, so LLM mode runs one receipt at a time;
        // ml-service runs a single worker
        int threads = llm ? 1 : !mlServiceUrl.isBlank() ? 2
            : Integer.getInteger("eval.threads", Runtime.getRuntime().availableProcessors());
        long delayMs = Long.getLong("eval.delayMs", 0L);

        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) rows.add(JSON.readTree(line));
        }

        CurrentReceiptPipeline pipeline = new CurrentReceiptPipeline(receiptOcrUrl, groqApiKey, mlServiceUrl, mlServiceToken);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<ObjectNode>> futures = new ArrayList<>();
        for (JsonNode row : rows) {
            futures.add(pool.submit(() -> {
                Path image = manifest.toAbsolutePath().getParent().resolve(row.get("image").asText());
                long start = System.nanoTime();
                ReceiptPrediction prediction = pipeline.extract(image);
                long millis = (System.nanoTime() - start) / 1_000_000;
                if (delayMs > 0) Thread.sleep(delayMs);
                ObjectNode node = JSON.createObjectNode().put("id", row.get("id").asText());
                node.setAll((ObjectNode) JSON.valueToTree(prediction));
                return node.put("millis", millis);
            }));
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        Map<String, Integer> providers = new TreeMap<>();
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (Future<ObjectNode> future : futures) {
                ObjectNode node = future.get();
                providers.merge(node.get("provider").asText(), 1, Integer::sum);
                out.write(JSON.writeValueAsString(node));
                out.newLine();
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.printf("receipt eval: %d receipts, providers %s -> %s%n", rows.size(), providers, output);
    }
}
