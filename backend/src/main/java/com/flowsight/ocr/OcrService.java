package com.flowsight.ocr;

import com.flowsight.exception.OcrException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Runs Tesseract as a subprocess: TSV mode for per-line confidence/position, txt fallback.
// CLI, not tess4j/JNA, to avoid matching native .so versions across base images.
@Service
@Slf4j
public class OcrService {

    private static final int TIMEOUT_SECONDS = 30;
    // one uniform block of text: keeps a receipt's right-aligned amounts on the same line as their
    // labels, which the default automatic segmentation (psm 3) splits into separate columns
    static final String PAGE_SEGMENTATION = "6";
    private static final Pattern OSD_ROTATE = Pattern.compile("Rotate:\\s*(\\d+)");
    private static final Pattern OSD_CONFIDENCE = Pattern.compile("Orientation confidence:\\s*([\\d.]+)");

    private final OcrPreprocessor preprocessor;
    private final String languages;

    @Autowired
    public OcrService(OcrPreprocessor preprocessor, @Value("${application.ocr.tesseract-languages:eng}") String languages) {
        this.preprocessor = preprocessor;
        this.languages = languages == null || languages.isBlank() ? "eng" : languages.trim();
    }

    public OcrService(OcrPreprocessor preprocessor) {
        this(preprocessor, "eng");
    }

    /** Clockwise rotation (0/90/180/270) Tesseract recommends, and how sure it is. */
    public record Orientation(int rotate, double confidence) {
        static final Orientation UPRIGHT = new Orientation(0, 0.0);
    }

    // single pass on the file as stored (used when ImageIO cannot decode it, e.g. WebP)
    public OcrDocument extractDocument(Path imagePath) {
        return doExtractDocument(imagePath);
    }

    // plain text via extractDocument
    public String extractText(Path imagePath) {
        return extractDocument(imagePath).plainText();
    }

    public OcrDocument recognize(BufferedImage image) {
        Path tmp = null;
        try {
            tmp = preprocessor.writeTemp(image);
            return doExtractDocument(tmp);
        } catch (IOException e) {
            throw new OcrException("Could not write image for Tesseract: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    public Orientation detectOrientation(BufferedImage image) {
        Path tmp = null;
        try {
            tmp = preprocessor.writeTemp(image);
            String out = runTesseract(List.of(tmp.toAbsolutePath().toString(), "stdout", "--psm", "0"));
            Matcher r = OSD_ROTATE.matcher(out);
            Matcher c = OSD_CONFIDENCE.matcher(out);
            if (r.find() && c.find()) return new Orientation(Integer.parseInt(r.group(1)), Double.parseDouble(c.group(1)));
        } catch (IOException | OcrException | NumberFormatException e) {
            log.debug("Orientation detection unavailable: {}", e.getMessage());
        } finally {
            deleteQuietly(tmp);
        }
        return Orientation.UPRIGHT;
    }

    private OcrDocument doExtractDocument(Path imagePath) {
        // TSV: confidence + position per line
        try {
            String tsv = runTesseract(args(imagePath, "tsv"));
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
        String plain = runTesseract(args(imagePath, "txt"));
        return OcrDocument.fromPlainText(plain);
    }

    private List<String> args(Path imagePath, String outputFormat) {
        return List.of(imagePath.toAbsolutePath().toString(), "stdout", "-l", languages,
            "--psm", PAGE_SEGMENTATION, outputFormat);
    }

    private String runTesseract(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add("tesseract");
        command.addAll(args);
        log.debug("Tesseract {}", args.get(args.size() - 1));
        ProcessBuilder pb = new ProcessBuilder(command);

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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("Interrupted while running Tesseract", e);
        } catch (IOException e) {
            throw new OcrException("Failed to execute Tesseract: " + e.getMessage(), e);
        }
    }

    // parse Tesseract TSV into OcrLines sorted by top-y; level-5 word rows joined by block:par:line
    List<OcrLine> parseTsv(String tsv) {
        if (tsv == null || tsv.isBlank()) return List.of();

        String[] rows = tsv.split("\\r?\\n");

        // lineKey → [sumConf, wordCount, minTop, maxHeight]
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

            int conf = (int) Math.round(safeDouble(cols[10]));
            if (conf < 0) continue;   // Tesseract uses -1 for non-word rows

            String text = cols[11].trim();
            if (text.isEmpty()) continue;

            String key = cols[2] + ":" + cols[3] + ":" + cols[4]; // block:par:line
            int top = safeInt(cols[7]);
            int height = safeInt(cols[9]);

            int[] stats = lineStats.computeIfAbsent(key, k -> new int[]{0, 0, Integer.MAX_VALUE, 0});
            stats[0] += conf;
            stats[1]++;
            if (top < stats[2]) stats[2] = top;
            if (height > stats[3]) stats[3] = height;

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
                    .heightPx(s[3])
                    .build();
            })
            .sorted(Comparator.comparingInt(OcrLine::getTopPx))
            .collect(Collectors.toList());
    }

    private static void deleteQuietly(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); }
        catch (IOException ignored) {}
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private double safeDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
