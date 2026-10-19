package com.flowsight.ocr;

import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptDecision;
import com.flowsight.exception.OcrException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The production receipt reader: one preprocessed image, up to three Tesseract passes over
 * different renderings of it (adaptive threshold, contrast-stretched gray, Otsu threshold at a
 * second scale), stopping once the total is settled (see {@link #settled}).
 * Passes disagree on different digits, so reconciling across them recovers totals that any single
 * pass misreads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TesseractReceiptPipeline {

    // stop starting new passes after this; each Tesseract run has its own 30 s timeout
    private static final long PASS_BUDGET_MILLIS = 40_000;
    // below these the first pass may be reading a sideways or upside-down receipt
    private static final int MIN_WORDS_UPRIGHT = 12;
    private static final double MIN_CONFIDENCE_UPRIGHT = 0.55;
    private static final double MIN_OSD_CONFIDENCE = 1.5;
    private static final double SECOND_SCALE = 0.8;
    private static final double SINGLE_PASS_ACCEPT = 0.98;

    private final OcrPreprocessor preprocessor;
    private final OcrService ocrService;
    private final ReceiptParserService parser;

    public OcrExtractionResult extract(Path image) {
        BufferedImage prepared = preprocessor.prepare(image);
        if (prepared == null) {
            return parser.parse(ocrService.extractDocument(image));
        }

        List<UnaryOperator<BufferedImage>> renderings = List.of(
            preprocessor::adaptiveBinarize,
            img -> img,
            img -> preprocessor.otsuBinarize(preprocessor.scaled(img, SECOND_SCALE))
        );

        long started = System.currentTimeMillis();
        List<ReceiptTextParser.Parsed> passes = new ArrayList<>();
        OcrException lastError = null;
        for (int i = 0; i < renderings.size(); i++) {
            if (i > 0 && System.currentTimeMillis() - started > PASS_BUDGET_MILLIS) break;
            try {
                OcrDocument doc = ocrService.recognize(renderings.get(i).apply(prepared));
                if (i == 0 && looksMisoriented(doc)) {
                    OcrService.Orientation o = ocrService.detectOrientation(prepared);
                    if (o.rotate() % 360 != 0 && o.confidence() >= MIN_OSD_CONFIDENCE) {
                        log.debug("Rotating receipt by {} degrees (orientation confidence {})", o.rotate(), o.confidence());
                        prepared = preprocessor.rotateQuarter(prepared, o.rotate());
                        doc = ocrService.recognize(renderings.get(i).apply(prepared));
                    }
                }
                passes.add(parser.read(doc));
            } catch (OcrException e) {
                lastError = e;
                continue;
            }
            ReceiptReconciler.TotalAssessment total = parser.reconcile(passes);
            if (settled(total, passes.size())) break;
        }
        if (passes.isEmpty()) {
            throw lastError != null ? lastError : new OcrException("No OCR pass produced text");
        }
        log.debug("Receipt read in {} pass(es), {} ms", passes.size(), System.currentTimeMillis() - started);
        return parser.assemble(passes);
    }

    // One pass is enough only when label, arithmetic and payment line all agree; a misread digit can
    // repeat in the subtotal and total of one pass, so otherwise a second pass must agree.
    static boolean settled(ReceiptReconciler.TotalAssessment total, int passes) {
        if (total.conflict() || total.assessment().decision() != ReceiptDecision.ACCEPT) return false;
        return passes >= 2 || total.assessment().confidence() >= SINGLE_PASS_ACCEPT;
    }

    private static boolean looksMisoriented(OcrDocument doc) {
        long words = doc.getLines().stream().mapToLong(l -> l.getText().split("\\s+").length).sum();
        double mean = doc.getLines().stream().mapToDouble(OcrLine::getConfidence).average().orElse(0.0);
        return words < MIN_WORDS_UPRIGHT || mean < MIN_CONFIDENCE_UPRIGHT;
    }
}
