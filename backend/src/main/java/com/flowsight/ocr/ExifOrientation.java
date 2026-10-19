package com.flowsight.ocr;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

/**
 * Reads the EXIF orientation tag (0x0112) from a JPEG and applies it, so phone photos taken
 * sideways reach Tesseract upright. ImageIO ignores EXIF, hence the small parser.
 */
final class ExifOrientation {

    private ExifOrientation() {
    }

    /** 1 (normal) when the file is not a JPEG, has no EXIF block or the tag is missing. */
    static int read(byte[] jpeg) {
        try {
            if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) return 1;
            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if ((jpeg[pos] & 0xFF) != 0xFF) return 1;
                int marker = jpeg[pos + 1] & 0xFF;
                int length = u16(jpeg, pos + 2, false);
                if (marker == 0xDA || marker == 0xD9) return 1;
                if (marker == 0xE1 && pos + 10 <= jpeg.length
                    && jpeg[pos + 4] == 'E' && jpeg[pos + 5] == 'x' && jpeg[pos + 6] == 'i' && jpeg[pos + 7] == 'f') {
                    return fromTiff(jpeg, pos + 10, Math.min(jpeg.length, pos + 2 + length));
                }
                pos += 2 + length;
            }
        } catch (RuntimeException e) {
            // a malformed EXIF block is not a reason to fail OCR
        }
        return 1;
    }

    private static int fromTiff(byte[] b, int tiff, int end) {
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        int entries = u16(b, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) break;
            if (u16(b, entry, little) == 0x0112) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    static BufferedImage apply(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = orientation >= 5;
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return src; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_BYTE_GRAY);
        new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(OcrPreprocessor.toGray(src), out);
        return out;
    }

    private static int u16(byte[] b, int off, boolean little) {
        return little ? (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8 : (b[off] & 0xFF) << 8 | (b[off + 1] & 0xFF);
    }

    private static long u32(byte[] b, int off, boolean little) {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int shift = little ? 8 * i : 8 * (3 - i);
            v |= (long) (b[off + i] & 0xFF) << shift;
        }
        return v;
    }
}
