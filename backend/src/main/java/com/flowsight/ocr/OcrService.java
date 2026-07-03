package com.flowsight.ocr;

import com.flowsight.exception.OcrException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Runs Tesseract as a subprocess: TSV mode for per-line confidence/position, txt fallback.
// CLI, not tess4j/JNA, to avoid matching native .so versions across base images.
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private static final int TIMEOUT_SECONDS = 30;

    private final OcrPreprocessor preprocessor;

    // preprocess, run Tesseract TSV (falls back to plain text), return an OcrDocument
    public OcrDocument extractDocument(Path imagePath) {
        Path preprocessed = preprocessor.preprocess(imagePath);
        boolean usingTemp = !preprocessed.equals(imagePath);
        try {
            return doExtractDocument(preprocessed);
        } finally {
            if (usingTemp) {
                try { Files.deleteIfExists(preprocessed); }
                catch (IOException ignored) {}
            }
        }
    }

    // plain text via extractDocument
    public String extractText(Path imagePath) {
        return extractDocument(imagePath).plainText();
    }

    private OcrDocument doExtractDocument(Path imagePath) {
        // TSV: confidence + position per line
        try {
            String tsv = runTesseract(imagePath, "tsv");
            List<OcrLine> lines = parseTsv(tsv);
            if (!lines.isEmpty()) {
                log.debug("TSV extraction yielded {} lines", lines.size());
                return OcrDocument.builder().lines(lines).build();
            }
            log.debug("TSV produced no parseable lines, falling back to txt");
        } catch (OcrException e) {
            log.warn("TSV extraction failed ({}), falling back to txt", e.getMessage());
        }

        // fallback: plain text (no confidence/position)
        String plain = runTesseract(imagePath, "txt");
        return OcrDocument.fromPlainText(plain);
    }

    private String runTesseract(Path imagePath, String outputFormat) {
        String absPath = imagePath.toAbsolutePath().toString();
        log.debug("Tesseract [{}] on {}", outputFormat, absPath);

        ProcessBuilder pb = new ProcessBuilder(
            "tesseract",
            absPath,
            "stdout",
            "-l", "eng",
            "--psm", "3",
            outputFormat
        );

        try {
            Process process = pb.start();

            // read both streams before waitFor to avoid pipe-buffer deadlock
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new OcrException("Tesseract timed out after " + TIMEOUT_SECONDS + "s");
            }

            int exit = process.exitValue();
            if (exit != 0) {
                String err = new String(stderr, StandardCharsets.UTF_8).trim();
                throw new OcrException("Tesseract exited " + exit + ": " + err);
            }

            return new String(stdout, StandardCharsets.UTF_8);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("Failed to execute Tesseract: " + e.getMessage(), e);
        }
    }

    // parse Tesseract TSV into OcrLines sorted by top-y; level-5 word rows joined by block:par:line
    List<OcrLine> parseTsv(String tsv) {
        if (tsv == null || tsv.isBlank()) return List.of();

        String[] rows = tsv.split("\\r?\\n");

        // lineKey → [sumConf, wordCount, minTop]
        Map<String, int[]>      lineStats = new LinkedHashMap<>();
        Map<String, List<String>> lineWords = new LinkedHashMap<>();
        int documentHeight = 0;

        for (String row : rows) {
            String[] cols = row.split("\t", -1);
            if (cols.length < 12) continue;

            int level = safeInt(cols[0]);

            if (level == 1) {
                documentHeight = safeInt(cols[9]); // page height from page-level row
                continue;
            }
            if (level != 5) continue; // only word-level rows carry usable data

            int conf = safeInt(cols[10]);
            if (conf < 0) continue;   // Tesseract uses -1 for non-word rows

            String text = cols[11].trim();
            if (text.isEmpty()) continue;

            String key = cols[2] + ":" + cols[3] + ":" + cols[4]; // block:par:line
            int top = safeInt(cols[7]);

            int[] stats = lineStats.computeIfAbsent(key, k -> new int[]{0, 0, Integer.MAX_VALUE});
            stats[0] += conf;
            stats[1]++;
            if (top < stats[2]) stats[2] = top;

            lineWords.computeIfAbsent(key, k -> new ArrayList<>()).add(text);
        }

        // no page row: infer height from the bottom-most word
        final int docH = documentHeight > 0 ? documentHeight
            : lineStats.values().stream()
                .mapToInt(s -> s[2])
                .max().orElse(1000) + 150;

        return lineStats.entrySet().stream()
            .filter(e -> lineWords.containsKey(e.getKey()))
            .map(e -> {
                int[] s = e.getValue();
                double avgConf = s[1] > 0 ? (double) s[0] / s[1] / 100.0 : 0.0;
                int topPx = s[2] == Integer.MAX_VALUE ? 0 : s[2];
                return OcrLine.builder()
                    .text(String.join(" ", lineWords.get(e.getKey())))
                    .confidence(Math.min(1.0, avgConf))
                    .topPx(topPx)
                    .documentHeightPx(docH)
                    .build();
            })
            .sorted(Comparator.comparingInt(OcrLine::getTopPx))
            .collect(Collectors.toList());
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
