package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExifOrientationTest {

    // SOI, APP1 "Exif" with a big-endian TIFF IFD holding one entry: Orientation (0x0112) = value
    static byte[] jpegWithOrientation(int value) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int[] bytes = {
            0xFF, 0xD8, 0xFF, 0xE1, 0x00, 0x22, 'E', 'x', 'i', 'f', 0, 0,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01, 0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, value, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0xFF, 0xD9};
        for (int v : bytes) b.write(v);
        return b.toByteArray();
    }

    @Test
    void readsOrientationTag() {
        assertThat(ExifOrientation.read(jpegWithOrientation(6))).isEqualTo(6);
        assertThat(ExifOrientation.read(jpegWithOrientation(3))).isEqualTo(3);
    }

    @Test
    void missingOrMalformedExifIsUpright() {
        assertThat(ExifOrientation.read(new byte[]{(byte) 0x89, 'P', 'N', 'G'})).isEqualTo(1);
        assertThat(ExifOrientation.read(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F})).isEqualTo(1);
    }

    @Test
    void rotatesQuarterTurnsClockwise() {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setSample(0, 0, 0, 200); // top-left marker
        BufferedImage cw = ExifOrientation.apply(img, 6);
        assertThat(cw.getWidth()).isEqualTo(2);
        assertThat(cw.getHeight()).isEqualTo(4);
        assertThat(cw.getRaster().getSample(1, 0, 0)).isEqualTo(200); // now top-right
        BufferedImage half = ExifOrientation.apply(img, 3);
        assertThat(half.getRaster().getSample(3, 1, 0)).isEqualTo(200); // now bottom-right
    }
}
