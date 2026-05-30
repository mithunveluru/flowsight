package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Structured result of a single Tesseract OCR run.
 *
 * Contains the full list of text lines (sorted by top-y position) together with
 * per-line confidence and positional data. The {@link #plainText()} convenience
 * method reconstructs the flat text that legacy callers expect.
 *
 * {@link #fromPlainText(String)} creates a synthetic document when only stored
 * plain text is available (e.g. for re-parsing receipts whose original image is
 * gone). Positions are synthesised from line order; confidence is set to 0.80.
 */
@Value
@Builder
public class OcrDocument {

    List<OcrLine> lines;

    public String plainText() {
        return lines.stream()
            .map(OcrLine::getText)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.joining("\n"));
    }

    public static OcrDocument fromPlainText(String text) {
        if (text == null || text.isBlank()) {
            return OcrDocument.builder().lines(List.of()).build();
        }
        String[] raw = text.split("\\r?\\n");
        int docHeight = raw.length * 25; // 25 synthetic px per line
        List<OcrLine> lines = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].isBlank()) continue;
            lines.add(OcrLine.builder()
                .text(raw[i])
                .confidence(0.80)
                .topPx(i * 25)
                .documentHeightPx(docHeight)
                .build());
        }
        return OcrDocument.builder().lines(lines).build();
    }
}
