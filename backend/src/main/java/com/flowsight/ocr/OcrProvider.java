package com.flowsight.ocr;

import java.nio.file.Path;

/**
 * Abstraction over the underlying OCR engine.
 *
 * The single production implementation is {@link OcrService} (Tesseract CLI).
 * The interface exists so future providers (cloud Vision API, AWS Textract, etc.)
 * can be swapped in without changing any parsing or service layer code.
 */
public interface OcrProvider {
    OcrDocument extract(Path imagePath);
}
