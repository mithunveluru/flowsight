package com.flowsight.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.nio.file.*;

/**
 * Prepares receipt images for Tesseract to improve extraction accuracy.
 *
 * Pipeline (applied in order):
 *   1. Grayscale conversion — eliminates colour noise; Tesseract works on intensity, not colour
 *   2. Contrast normalisation — stretches the histogram to [0, 255] for faded/overexposed scans
 *   3. Upscaling — if the shortest dimension is below 800 px, scale ×2 (Tesseract degrades on
 *      small images; 300 DPI equivalent is roughly 800–1000 px for a standard receipt)
 *
 * Result is written to a temporary PNG (lossless) so JPEG re-compression artefacts on fine
 * receipt text do not degrade OCR quality.
 *
 * If ImageIO cannot decode the source format (e.g. WebP, unsupported TIFF variant), or if any
 * step throws, the original path is returned unchanged so OCR can still proceed.
 */
@Component
@Slf4j
public class OcrPreprocessor {

    static {
        // Ensure AWT runs without a display server inside the Docker container
        System.setProperty("java.awt.headless", "true");
    }

    private static final int  MIN_DIMENSION_PX = 800;
    private static final double UPSCALE_FACTOR  = 2.0;

    /**
     * Returns a path to the preprocessed image. If the returned path differs from
     * {@code inputPath} it is a temp file — the caller must delete it after use.
     */
    public Path preprocess(Path inputPath) {
        try {
            BufferedImage original = ImageIO.read(inputPath.toFile());
            if (original == null) {
                log.debug("ImageIO cannot decode {}, skipping preprocessing", inputPath.getFileName());
                return inputPath;
            }

            BufferedImage img = toGrayscale(original);
            img = normalizeContrast(img);
            img = maybeUpscale(img);

            Path tmp = Files.createTempFile("flocr-", ".png");
            ImageIO.write(img, "PNG", tmp.toFile());
            log.debug("Preprocessed {} → temp {}×{}", inputPath.getFileName(), img.getWidth(), img.getHeight());
            return tmp;

        } catch (Exception e) {
            log.warn("Image preprocessing failed for {} ({}), using original", inputPath.getFileName(), e.getMessage());
            return inputPath;
        }
    }

    BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    /**
     * Stretches pixel intensities so the darkest pixel becomes 0 and the lightest 255.
     * Skipped when the dynamic range is already wide (>= 200) or very narrow (< 30),
     * where stretching would either be a no-op or produce extreme artefacts.
     */
    BufferedImage normalizeContrast(BufferedImage src) {
        Raster raster = src.getRaster();
        int width = src.getWidth(), height = src.getHeight();
        int[] px = new int[1];
        int min = 255, max = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, px);
                if (px[0] < min) min = px[0];
                if (px[0] > max) max = px[0];
            }
        }

        int range = max - min;
        if (range < 30 || range >= 200) return src;

        float scale = 255.0f / range;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster out = result.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, px);
                out.setPixel(x, y, new int[]{Math.min(255, Math.round((px[0] - min) * scale))});
            }
        }
        return result;
    }

    BufferedImage maybeUpscale(BufferedImage src) {
        if (src.getWidth() >= MIN_DIMENSION_PX || src.getHeight() >= MIN_DIMENSION_PX) {
            return src;
        }
        int w = (int) (src.getWidth()  * UPSCALE_FACTOR);
        int h = (int) (src.getHeight() * UPSCALE_FACTOR);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        log.debug("Upscaled {}×{} → {}×{}", src.getWidth(), src.getHeight(), w, h);
        return scaled;
    }
}
