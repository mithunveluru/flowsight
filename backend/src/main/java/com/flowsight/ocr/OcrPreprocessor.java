package com.flowsight.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;

// Prepares receipt images for Tesseract in plain Java2D: EXIF orientation, grayscale, removal of
// the darker table around a photographed receipt, deskew, contrast stretch and resolution
// normalization. Binarized variants (adaptive and Otsu) feed the extra OCR passes.
@Component
@Slf4j
public class OcrPreprocessor {

    static {
        // headless: no display server in Docker
        System.setProperty("java.awt.headless", "true");
    }

    // receipt text reads best when the paper is roughly this wide (about 35 px per mono character)
    static final int TARGET_PAPER_WIDTH = 1500;
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 3.0;
    // working copy for background and skew analysis
    private static final int ANALYSIS_SIZE = 1000;
    private static final double MAX_SKEW_DEGREES = 15.0;
    // Tesseract copes with small skew; rotating below this only blurs the glyphs
    private static final double MIN_SKEW_DEGREES = 1.5;
    private static final double BRADLEY_T = 0.15;

    /** Decoded, upright, cropped, deskewed and rescaled grayscale image; null if ImageIO cannot decode it. */
    public BufferedImage prepare(Path inputPath) {
        try {
            byte[] bytes = Files.readAllBytes(inputPath);
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                log.debug("ImageIO cannot decode {}, skipping preprocessing", inputPath.getFileName());
                return null;
            }
            return prepare(ExifOrientation.apply(original, ExifOrientation.read(bytes)));
        } catch (IOException | RuntimeException e) {
            log.warn("Image preprocessing failed for {} ({}), using original", inputPath.getFileName(), e.getClass().getSimpleName());
            return null;
        }
    }

    BufferedImage prepare(BufferedImage src) {
        BufferedImage img = toGray(src);
        img = removeBackground(img);
        img = deskew(img);
        img = stretchContrast(img);
        return rescale(img);
    }

    /** Writes the image as a lossless temp PNG; the caller deletes it. */
    public Path writeTemp(BufferedImage img) throws IOException {
        Path tmp = Files.createTempFile("flocr-", ".png");
        ImageIO.write(img, "PNG", tmp.toFile());
        return tmp;
    }

    // A fresh, tightly packed TYPE_BYTE_GRAY copy so pixels() can index it directly. Luma is
    // computed by hand: Graphics2D would convert through linear gray and darken the mid-tones.
    static BufferedImage toGray(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = pixels(gray);
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                src.getRaster().getSamples(0, y, w, 1, 0, row);
                for (int x = 0; x < w; x++) dst[y * w + x] = (byte) row[x];
            }
            return gray;
        }
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int a = argb >>> 24;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int luma = (299 * r + 587 * g + 114 * b) / 1000;
                // transparent pixels (PNG screenshots) count as white paper
                dst[y * w + x] = (byte) ((luma * a + 255 * (255 - a)) / 255);
            }
        }
        return gray;
    }

    // Paper is brighter than the table under it: flood-fill the dark pixels connected to the image
    // border, paint them paper-coloured and crop to what is left. Text never touches the border
    // because receipts have a printed margin, so it is not filled.
    BufferedImage removeBackground(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        double f = Math.min(1.0, (double) ANALYSIS_SIZE / Math.max(w, h));
        BufferedImage small = f < 1.0 ? resize(gray, Math.max(1, (int) (w * f)), Math.max(1, (int) (h * f))) : gray;
        int sw = small.getWidth();
        int sh = small.getHeight();
        byte[] px = pixels(small);
        int threshold = otsu(px);

        boolean[] background = new boolean[sw * sh];
        int[] stack = new int[sw * sh];
        int top = 0;
        for (int x = 0; x < sw; x++) {
            top = seed(px, background, stack, top, x, threshold);
            top = seed(px, background, stack, top, (sh - 1) * sw + x, threshold);
        }
        for (int y = 0; y < sh; y++) {
            top = seed(px, background, stack, top, y * sw, threshold);
            top = seed(px, background, stack, top, y * sw + sw - 1, threshold);
        }
        int filled = 0;
        while (top > 0) {
            int i = stack[--top];
            filled++;
            int x = i % sw;
            int y = i / sw;
            if (x > 0) top = seed(px, background, stack, top, i - 1, threshold);
            if (x < sw - 1) top = seed(px, background, stack, top, i + 1, threshold);
            if (y > 0) top = seed(px, background, stack, top, i - sw, threshold);
            if (y < sh - 1) top = seed(px, background, stack, top, i + sw, threshold);
        }
        // nothing (or almost everything) dark touches the border: a scan or a crop, leave it alone
        if (filled < 0.02 * sw * sh || filled > 0.9 * sw * sh) return gray;

        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        long paperSum = 0;
        int paperCount = 0;
        for (int i = 0; i < px.length; i++) {
            if (background[i]) continue;
            int x = i % sw;
            int y = i / sw;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int v = px[i] & 0xFF;
            if (v > threshold) {
                paperSum += v;
                paperCount++;
            }
        }
        if (maxX < minX || paperCount == 0) return gray;
        int paper = (int) (paperSum / paperCount);

        int x0 = (int) (minX / f);
        int y0 = (int) (minY / f);
        int x1 = Math.min(w, (int) Math.ceil((maxX + 1) / f));
        int y1 = Math.min(h, (int) Math.ceil((maxY + 1) / f));
        BufferedImage out = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_BYTE_GRAY);
        byte[] src = pixels(gray);
        byte[] dst = pixels(out);
        int ow = out.getWidth();
        for (int y = y0; y < y1; y++) {
            int sy = Math.min(sh - 1, (int) (y * f));
            for (int x = x0; x < x1; x++) {
                int sx = Math.min(sw - 1, (int) (x * f));
                dst[(y - y0) * ow + (x - x0)] = background[sy * sw + sx] ? (byte) paper : src[y * w + x];
            }
        }
        return out;
    }

    private static int seed(byte[] px, boolean[] background, int[] stack, int top, int i, int threshold) {
        if (!background[i] && (px[i] & 0xFF) <= threshold) {
            background[i] = true;
            stack[top++] = i;
        }
        return top;
    }

    // Projection-profile deskew: the rotation that makes text rows sharpest wins.
    BufferedImage deskew(BufferedImage gray) {
        double angle = skewAngle(gray);
        if (Math.abs(angle) < MIN_SKEW_DEGREES) return gray;
        log.debug("Deskewing by {} degrees", String.format("%.1f", angle));
        return rotate(gray, Math.toRadians(angle), paperTone(gray));
    }

    double skewAngle(BufferedImage gray) {
        double f = Math.min(1.0, 600.0 / Math.max(gray.getWidth(), gray.getHeight()));
        BufferedImage small = f < 1.0 ? resize(gray, (int) (gray.getWidth() * f), (int) (gray.getHeight() * f)) : gray;
        byte[] px = pixels(small);
        int threshold = otsu(px);
        int w = small.getWidth();
        int n = 0;
        for (byte b : px) if ((b & 0xFF) <= threshold) n++;
        if (n < 50) return 0.0;
        int[] xs = new int[n];
        int[] ys = new int[n];
        int k = 0;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] & 0xFF) <= threshold) {
                xs[k] = i % w;
                ys[k++] = i / w;
            }
        }
        double best = 0.0;
        double bestScore = -1;
        for (double a = -MAX_SKEW_DEGREES; a <= MAX_SKEW_DEGREES + 1e-9; a += 1.0) {
            double s = profileSharpness(xs, ys, a, small.getWidth() + small.getHeight());
            if (s > bestScore) { bestScore = s; best = a; }
        }
        double coarse = best;
        for (double a = coarse - 1.0; a <= coarse + 1.0 + 1e-9; a += 0.2) {
            double s = profileSharpness(xs, ys, a, small.getWidth() + small.getHeight());
            if (s > bestScore) { bestScore = s; best = a; }
        }
        return best;
    }

    // rows of the image rotated by `degrees`: y' = x sin + y cos (Java2D rotation, y down)
    private static double profileSharpness(int[] xs, int[] ys, double degrees, int span) {
        double r = Math.toRadians(degrees);
        double sin = Math.sin(r);
        double cos = Math.cos(r);
        int[] bins = new int[2 * span + 1];
        for (int i = 0; i < xs.length; i++) {
            int y = (int) Math.round(xs[i] * sin + ys[i] * cos) + span;
            if (y >= 0 && y < bins.length) bins[y]++;
        }
        double s = 0;
        for (int i = 1; i < bins.length; i++) {
            double d = bins[i] - bins[i - 1];
            s += d * d;
        }
        return s;
    }

    static BufferedImage rotate(BufferedImage gray, double radians, int fill) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        AffineTransform rot = AffineTransform.getRotateInstance(radians, w / 2.0, h / 2.0);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point2D p : new Point2D[]{new Point2D.Double(0, 0), new Point2D.Double(w, 0), new Point2D.Double(0, h), new Point2D.Double(w, h)}) {
            Point2D q = rot.transform(p, null);
            minX = Math.min(minX, q.getX());
            minY = Math.min(minY, q.getY());
            maxX = Math.max(maxX, q.getX());
            maxY = Math.max(maxY, q.getY());
        }
        int nw = (int) Math.ceil(maxX - minX);
        int nh = (int) Math.ceil(maxY - minY);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        // fill through the raster: a Graphics2D colour would be converted to linear gray
        java.util.Arrays.fill(pixels(out), (byte) fill);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        AffineTransform t = AffineTransform.getTranslateInstance(-minX, -minY);
        t.concatenate(rot);
        g.drawImage(gray, t, null);
        g.dispose();
        return out;
    }

    /** Quarter turns clockwise (90, 180, 270), as reported by Tesseract's orientation detection. */
    public BufferedImage rotateQuarter(BufferedImage gray, int degreesClockwise) {
        int turns = ((degreesClockwise / 90) % 4 + 4) % 4;
        if (turns == 0) return gray;
        return ExifOrientation.apply(gray, turns == 1 ? 6 : turns == 2 ? 3 : 8);
    }

    // 1st..99th percentile to 0..255; leaves an already full-range image as it is
    BufferedImage stretchContrast(BufferedImage gray) {
        byte[] px = pixels(gray);
        int[] hist = histogram(px);
        int lo = percentile(hist, px.length, 0.01);
        int hi = percentile(hist, px.length, 0.99);
        if (hi - lo < 30 || (lo <= 5 && hi >= 250)) return gray;
        BufferedImage out = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = pixels(out);
        double scale = 255.0 / (hi - lo);
        for (int i = 0; i < px.length; i++) {
            int v = (int) Math.round(((px[i] & 0xFF) - lo) * scale);
            dst[i] = (byte) Math.max(0, Math.min(255, v));
        }
        return out;
    }

    BufferedImage rescale(BufferedImage gray) {
        double s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, (double) TARGET_PAPER_WIDTH / gray.getWidth()));
        if (Math.abs(s - 1.0) < 0.05) return gray;
        return resize(gray, (int) Math.round(gray.getWidth() * s), (int) Math.round(gray.getHeight() * s));
    }

    /** Bradley local-mean threshold: copes with shadows and glare that defeat a global threshold. */
    public BufferedImage adaptiveBinarize(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        byte[] px = pixels(gray);
        long[] integral = new long[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            long row = 0;
            for (int x = 0; x < w; x++) {
                row += px[y * w + x] & 0xFF;
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row;
            }
        }
        int r = Math.max(7, w / 32);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = pixels(out);
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - r);
            int y1 = Math.min(h, y + r + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - r);
                int x1 = Math.min(w, x + r + 1);
                long sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1]
                    - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0];
                long count = (long) (y1 - y0) * (x1 - x0);
                dst[y * w + x] = (px[y * w + x] & 0xFF) * count > sum * (1.0 - BRADLEY_T) ? (byte) 255 : 0;
            }
        }
        return out;
    }

    public BufferedImage otsuBinarize(BufferedImage gray) {
        byte[] px = pixels(gray);
        int threshold = otsu(px);
        BufferedImage out = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] dst = pixels(out);
        for (int i = 0; i < px.length; i++) dst[i] = (px[i] & 0xFF) > threshold ? (byte) 255 : 0;
        return out;
    }

    public BufferedImage scaled(BufferedImage gray, double factor) {
        return resize(gray, (int) Math.round(gray.getWidth() * factor), (int) Math.round(gray.getHeight() * factor));
    }

    static int otsu(byte[] px) {
        int[] hist = histogram(px);
        long total = px.length;
        double sumAll = 0;
        for (int t = 0; t < 256; t++) sumAll += (double) t * hist[t];
        double sumB = 0;
        long wB = 0;
        double best = -1;
        int threshold = 127;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            long wF = total - wB;
            if (wF == 0) break;
            sumB += (double) t * hist[t];
            double mB = sumB / wB;
            double mF = (sumAll - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) {
                best = between;
                threshold = t;
            }
        }
        return threshold;
    }

    private static int paperTone(BufferedImage gray) {
        byte[] px = pixels(gray);
        return percentile(histogram(px), px.length, 0.90);
    }

    private static int[] histogram(byte[] px) {
        int[] hist = new int[256];
        for (byte b : px) hist[b & 0xFF]++;
        return hist;
    }

    private static int percentile(int[] hist, int n, double q) {
        long target = (long) Math.ceil(q * n);
        long acc = 0;
        for (int v = 0; v < 256; v++) {
            acc += hist[v];
            if (acc >= target) return v;
        }
        return 255;
    }

    static byte[] pixels(BufferedImage gray) {
        return ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
    }

    static BufferedImage resize(BufferedImage gray, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(gray, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }
}
