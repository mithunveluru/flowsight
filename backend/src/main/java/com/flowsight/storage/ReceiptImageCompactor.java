package com.flowsight.storage;

import com.flowsight.ocr.ExifOrientation;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * The copy of a receipt kept for the review screen: upright, long side capped, re-encoded as a
 * baseline JPEG. Nothing from the original file but its pixels survives, so EXIF (GPS, device,
 * timestamps), XMP and ICC blocks are dropped.
 */
public final class ReceiptImageCompactor {

    // far beyond any phone camera; a larger image is more likely a decompression bomb
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;

    private ReceiptImageCompactor() {
    }

    /** Empty when the JDK cannot decode the image (e.g. WebP, CMYK JPEG) or it is implausibly large. */
    public static Optional<byte[]> compact(byte[] original, int maxLongSide, float jpegQuality) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) return Optional.empty();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if ((long) w * h > MAX_SOURCE_PIXELS) return Optional.empty();
                // decode at no less than twice the target size, then downscale smoothly
                int step = Math.max(1, Math.max(w, h) / (2 * maxLongSide));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = downscale(toRgb(reader.read(0, param)), maxLongSide);
                return Optional.of(writeJpeg(orient(image, ExifOrientation.read(original)), jpegQuality));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    // transparent areas become white, as on paper
    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    // halving steps keep thin print legible where one large bilinear step would alias it
    private static BufferedImage downscale(BufferedImage src, int maxLongSide) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        if (longSide <= maxLongSide) return src;
        double scale = (double) maxLongSide / longSide;
        int targetW = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage current = src;
        int w = src.getWidth();
        int h = src.getHeight();
        while (w != targetW || h != targetH) {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = resize(current, w, h);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // the orientation tag is dropped with the rest of EXIF, so it is applied to the pixels
    static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int outW = swap ? h : w;
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int tx;
                int ty;
                switch (orientation) {
                    case 2 -> { tx = w - 1 - x; ty = y; }
                    case 3 -> { tx = w - 1 - x; ty = h - 1 - y; }
                    case 4 -> { tx = x; ty = h - 1 - y; }
                    case 5 -> { tx = y; ty = x; }
                    case 6 -> { tx = h - 1 - y; ty = x; }
                    case 7 -> { tx = h - 1 - y; ty = w - 1 - x; }
                    default -> { tx = y; ty = w - 1 - x; }
                }
                out[ty * outW + tx] = in[y * w + x];
            }
        }
        BufferedImage result = new BufferedImage(outW, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, outW, swap ? w : h, out, 0, outW);
        return result;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
