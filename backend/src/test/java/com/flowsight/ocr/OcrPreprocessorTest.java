package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OcrPreprocessorTest {

    private final OcrPreprocessor pre = new OcrPreprocessor();

    // white "paper" with dark horizontal text bars, like printed receipt lines
    static BufferedImage paper(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] px = OcrPreprocessor.pixels(img);
        Arrays.fill(px, (byte) 245);
        for (int y = 40; y < h - 40; y += 30) {
            for (int yy = y; yy < y + 12; yy++) {
                for (int x = 40; x < w - 40; x++) {
                    if ((x / 9) % 3 != 2) px[yy * w + x] = (byte) 30;
                }
            }
        }
        return img;
    }

    static BufferedImage onTable(BufferedImage paper, int pad, int tone) {
        int w = paper.getWidth() + 2 * pad;
        int h = paper.getHeight() + 2 * pad;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        byte[] px = OcrPreprocessor.pixels(img);
        Arrays.fill(px, (byte) tone);
        byte[] src = OcrPreprocessor.pixels(paper);
        for (int y = 0; y < paper.getHeight(); y++) {
            System.arraycopy(src, y * paper.getWidth(), px, (y + pad) * w + pad, paper.getWidth());
        }
        return img;
    }

    @Test
    void cropsTheDarkTableAroundAReceipt() {
        BufferedImage photo = onTable(paper(400, 900), 120, 100);

        BufferedImage cropped = pre.removeBackground(photo);

        assertThat(cropped.getWidth()).isBetween(395, 410);
        assertThat(cropped.getHeight()).isBetween(895, 910);
    }

    @Test
    void leavesAScanWithoutBackgroundAlone() {
        BufferedImage scan = paper(400, 900);
        assertThat(pre.removeBackground(scan)).isSameAs(scan);
    }

    @Test
    void detectsAndRemovesSkew() {
        BufferedImage skewed = OcrPreprocessor.rotate(paper(500, 700), Math.toRadians(8), 245);

        assertThat(Math.abs(pre.skewAngle(skewed))).isCloseTo(8.0, within(0.6));
        assertThat(pre.skewAngle(pre.deskew(skewed))).isCloseTo(0.0, within(0.6));
    }

    @Test
    void normalizesPaperWidth() {
        assertThat(pre.rescale(paper(500, 700)).getWidth()).isEqualTo(OcrPreprocessor.TARGET_PAPER_WIDTH);
        assertThat(pre.rescale(paper(6000, 200)).getWidth()).isEqualTo(3000);
    }

    @Test
    void binarizationsProduceOnlyBlackAndWhite() {
        BufferedImage img = pre.stretchContrast(paper(300, 300));
        for (BufferedImage b : new BufferedImage[]{pre.adaptiveBinarize(img), pre.otsuBinarize(img)}) {
            for (byte v : OcrPreprocessor.pixels(b)) assertThat(v == 0 || v == (byte) 255).isTrue();
        }
    }

    @Test
    void grayConversionKeepsSrgbLevels() {
        BufferedImage rgb = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, 0x808080);
        rgb.setRGB(1, 0, 0xF5F5F5);
        BufferedImage gray = OcrPreprocessor.toGray(rgb);
        assertThat(gray.getRaster().getSample(0, 0, 0)).isEqualTo(128);
        assertThat(gray.getRaster().getSample(1, 0, 0)).isEqualTo(245);
    }
}
