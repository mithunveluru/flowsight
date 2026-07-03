package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

// One text line from Tesseract TSV: words in a block:par:line joined, confidence averaged to [0,1].
@Value
@Builder
public class OcrLine {

    String text;
    double confidence;      // 0.0–1.0
    int    topPx;           // y-pixel position from image top
    int    documentHeightPx;

    // how far down the document [0,1]
    public double relativeTop() {
        return documentHeightPx > 0 ? (double) topPx / documentHeightPx : 0.0;
    }
}
