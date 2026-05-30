package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

/**
 * One logical text line extracted from a Tesseract TSV output.
 *
 * Words within the same (block_num, par_num, line_num) group are joined into a
 * single text string. Confidence is averaged across those words (Tesseract reports
 * per-word confidence in column 11 of the TSV, scaled 0–100; here it is normalised
 * to [0.0, 1.0]).
 *
 * topPx is the minimum top-y coordinate of any word in this line, giving the
 * physical position of the line from the top of the scanned image.
 */
@Value
@Builder
public class OcrLine {

    String text;
    double confidence;      // 0.0–1.0
    int    topPx;           // y-pixel position from image top
    int    documentHeightPx;

    /** Fraction [0.0, 1.0] indicating how far down the document this line sits. */
    public double relativeTop() {
        return documentHeightPx > 0 ? (double) topPx / documentHeightPx : 0.0;
    }
}
