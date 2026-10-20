package com.flowsight.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptImageCompactorTest {

    private static final String GPS_MARKER = "GPS 48.858370N 2.294481E";

    @Test
    void largePhoto_isDownscaledToTheLongSideLimit_keepingTheAspectRatio() throws IOException {
        byte[] jpeg = encode(receipt(3000, 4000, BufferedImage.TYPE_INT_RGB), "jpg");

        BufferedImage out = decode(ReceiptImageCompactor.compact(jpeg, 2000, 0.8f).orElseThrow());

        assertThat(out.getHeight()).isEqualTo(2000);
        assertThat(out.getWidth()).isEqualTo(1500);
    }

    @Test
    void veryLargePng_isSubsampledAndStillWithinBounds() throws IOException {
        byte[] png = encode(receipt(9000, 2500, BufferedImage.TYPE_BYTE_GRAY), "png");

        BufferedImage out = decode(ReceiptImageCompactor.compact(png, 2000, 0.8f).orElseThrow());

        assertThat(Math.max(out.getWidth(), out.getHeight())).isLessThanOrEqualTo(2000);
        assertThat(out.getWidth()).isEqualTo(2000);
        assertThat(out.getHeight()).isBetween(554, 557);
    }

    @Test
    void smallImage_keepsItsSize() throws IOException {
        byte[] png = encode(receipt(600, 900, BufferedImage.TYPE_INT_RGB), "png");

        BufferedImage out = decode(ReceiptImageCompactor.compact(png, 2000, 0.8f).orElseThrow());

        assertThat(out.getWidth()).isEqualTo(600);
        assertThat(out.getHeight()).isEqualTo(900);
    }

    @Test
    void exifAndGps_areStripped() throws IOException {
        byte[] withExif = withExif(encode(receipt(400, 600, BufferedImage.TYPE_INT_RGB), "jpg"), 1);
        assertThat(contains(withExif, "Exif")).isTrue();
        assertThat(contains(withExif, GPS_MARKER)).isTrue();

        byte[] out = ReceiptImageCompactor.compact(withExif, 2000, 0.8f).orElseThrow();

        assertThat(contains(out, "Exif")).isFalse();
        assertThat(contains(out, GPS_MARKER)).isFalse();
        assertThat(hasApp1Segment(out)).isFalse();
        assertThat(out[0] & 0xFF).isEqualTo(0xFF);
        assertThat(out[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void exifOrientation_isAppliedToThePixelsBeforeTheTagIsDropped() throws IOException {
        // landscape sensor data with a red top-left corner, tagged "rotate 90 clockwise"
        BufferedImage sensor = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sensor.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 60, 60);
        g.dispose();

        BufferedImage out = decode(ReceiptImageCompactor.compact(withExif(encode(sensor, "jpg"), 6), 2000, 0.8f).orElseThrow());

        assertThat(out.getWidth()).isEqualTo(200);
        assertThat(out.getHeight()).isEqualTo(300);
        assertThat(isRed(out.getRGB(200 - 20, 20))).isTrue();
        assertThat(isRed(out.getRGB(20, 20))).isFalse();
    }

    @Test
    void transparentPng_becomesWhiteJpeg() throws IOException {
        BufferedImage clear = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage out = decode(ReceiptImageCompactor.compact(encode(clear, "png"), 2000, 0.8f).orElseThrow());

        int rgb = out.getRGB(50, 50);
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(245);
        assertThat(rgb & 0xFF).isGreaterThan(245);
    }

    @Test
    void imagesTheJdkCannotDecode_areNotKept() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 \0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ReceiptImageCompactor.compact(webp, 2000, 0.8f)).isEmpty();
        assertThat(ReceiptImageCompactor.compact(new byte[]{1, 2, 3}, 2000, 0.8f)).isEmpty();
        assertThat(ReceiptImageCompactor.compact(new byte[0], 2000, 0.8f)).isEmpty();
    }

    @Test
    void orient_mapsAllEightExifOrientations() {
        BufferedImage src = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xFF0000);
        int[][] expectedCorner = {
            {0, 0}, {0, 0}, {2, 0}, {2, 1}, {0, 1}, {0, 0}, {1, 0}, {1, 2}, {0, 2}};
        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage out = ReceiptImageCompactor.orient(src, orientation);
            int[] at = expectedCorner[orientation];
            assertThat(out.getRGB(at[0], at[1]) & 0xFFFFFF).as("orientation %d", orientation).isEqualTo(0xFF0000);
            assertThat(out.getWidth()).isEqualTo(orientation >= 5 ? 2 : 3);
        }
    }

    private static BufferedImage receipt(int w, int h, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, w / 40)));
        for (int y = 60; y < h; y += Math.max(20, h / 30)) g.drawString("ITEM 12.50  TOTAL 45.00", 20, y);
        g.dispose();
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // APP1 Exif segment (big-endian TIFF, one IFD: orientation + GPS IFD pointer) inserted after SOI
    private static byte[] withExif(byte[] jpeg, int orientation) {
        byte[] gps = GPS_MARKER.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        writeBytes(tiff, 'M', 'M', 0, 42, 0, 0, 0, 8);
        writeBytes(tiff, 0, 2);
        writeBytes(tiff, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation, 0, 0);
        writeBytes(tiff, 0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 38);
        writeBytes(tiff, 0, 0, 0, 0);
        tiff.writeBytes(gps);
        byte[] payload = tiff.toByteArray();
        int length = 2 + 6 + payload.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        writeBytes(out, 0xFF, 0xE1, length >> 8, length & 0xFF, 'E', 'x', 'i', 'f', 0, 0);
        out.writeBytes(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void writeBytes(ByteArrayOutputStream out, int... values) {
        for (int v : values) out.write(v);
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static boolean hasApp1Segment(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA) return false;
            if (marker == 0xE1) return true;
            pos += 2 + (((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF));
        }
        return false;
    }

    private static boolean isRed(int rgb) {
        return ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 80 && (rgb & 0xFF) < 80;
    }
}
