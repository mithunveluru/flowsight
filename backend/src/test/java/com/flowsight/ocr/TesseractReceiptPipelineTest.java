package com.flowsight.ocr;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TesseractReceiptPipelineTest {

    private static ReceiptReconciler.TotalAssessment total(double confidence, boolean conflict) {
        FieldAssessment f = conflict ? FieldAssessment.ask(confidence, "x") : FieldAssessment.of(confidence, "x");
        return new ReceiptReconciler.TotalAssessment(null, f, conflict, List.of(), 0, false);
    }

    @Test
    void onePassSettlesOnlyWithLabelArithmeticAndPayment() {
        assertThat(TesseractReceiptPipeline.settled(total(0.98, false), 1)).isTrue();
        assertThat(TesseractReceiptPipeline.settled(total(0.95, false), 1)).isFalse();
        assertThat(TesseractReceiptPipeline.settled(total(0.95, false), 2)).isTrue();
        assertThat(TesseractReceiptPipeline.settled(total(0.45, true), 3)).isFalse();
        assertThat(TesseractReceiptPipeline.settled(total(0.70, false), 2)).isFalse();
    }

    // Runs where the tesseract binary is installed (the eval image); skipped elsewhere.
    @Test
    void readsARenderedReceiptPhotographedOnATable(@TempDir Path dir) throws Exception {
        assumeTrue(tesseractInstalled(), "tesseract not installed");
        String[] lines = {
            "CHAI POINT", "MG Road, Bengaluru 560001", "Bill No: 4629  Date: 18/11/2024", "",
            "Cappuccino        1   180.00   180.00", "Veg Puff          1    75.00    75.00",
            "Sub Total                    255.00", "CGST @2.5%                     6.38", "SGST @2.5%                     6.38",
            "Round Off                     +0.24", "Net Payable               Rs.268.00", "Paid by Card                 268.00"};
        BufferedImage paper = new BufferedImage(760, 40 + lines.length * 40, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = paper.createGraphics();
        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, 0, paper.getWidth(), paper.getHeight());
        g.setColor(new Color(30, 30, 30));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 26));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 30, 50 + i * 40);
        g.dispose();
        BufferedImage photo = OcrPreprocessorTest.onTable(
            OcrPreprocessor.rotate(paper, Math.toRadians(6), 245), 80, 110);
        Path file = dir.resolve("receipt.png");
        ImageIO.write(photo, "PNG", file.toFile());

        OcrPreprocessor pre = new OcrPreprocessor();
        TesseractReceiptPipeline pipeline = new TesseractReceiptPipeline(pre, new OcrService(pre),
            new ReceiptParserService(new MerchantExtractor(), (text, hints) -> Optional.empty()));
        OcrExtractionResult r = pipeline.extract(file);

        assertThat(r.getAmount()).isEqualByComparingTo("268.00");
        assertThat(r.getCurrency()).isEqualTo("INR");
        assertThat(r.getDate()).hasToString("2024-11-18");
        assertThat(r.getMerchant()).isEqualTo("CHAI POINT");
        assertThat(r.getFields().get("amount").decision()).isEqualTo(ReceiptDecision.ACCEPT);
    }

    private static boolean tesseractInstalled() {
        try {
            return new ProcessBuilder("tesseract", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
