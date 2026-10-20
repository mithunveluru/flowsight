package com.flowsight.storage;

import com.flowsight.entity.Receipt;
import com.flowsight.repository.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptImageServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final ReceiptImageStore store = mock(ReceiptImageStore.class);
    private final ReceiptImageService service = new ReceiptImageService(store,
        new ReceiptImageProperties("local", 2000, 0.8f, Duration.ofDays(7), Duration.ofHours(24)),
        mock(ReceiptRepository.class));

    @Test
    void draftsExpireAfterTheDraftWindow() {
        assertThat(service.draftExpiry(NOW)).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void confirmKeepsTheImageForTheReviewedWindow_butNeverPastTheDraftLimit() {
        Instant draftExpiry = NOW.plus(Duration.ofDays(7));
        assertThat(service.reviewedExpiry(draftExpiry, NOW)).isEqualTo(NOW.plus(Duration.ofHours(24)));

        Instant almostExpired = NOW.plus(Duration.ofHours(2));
        assertThat(service.reviewedExpiry(almostExpired, NOW)).isEqualTo(almostExpired);

        Instant alreadyExpired = NOW.minus(Duration.ofHours(1));
        assertThat(service.reviewedExpiry(alreadyExpired, NOW)).isEqualTo(alreadyExpired);
    }

    @Test
    void availability_followsKeyExpiryAndDeletion() {
        Receipt receipt = Receipt.builder().filePath("u/r.jpg").imageExpiresAt(NOW.plusSeconds(1)).build();
        assertThat(ReceiptImageService.isAvailable(receipt, NOW)).isTrue();

        receipt.setImageExpiresAt(NOW);
        assertThat(ReceiptImageService.isAvailable(receipt, NOW)).isFalse();

        receipt.setImageExpiresAt(NOW.plusSeconds(60));
        receipt.setImageDeletedAt(NOW);
        assertThat(ReceiptImageService.isAvailable(receipt, NOW)).isFalse();

        assertThat(ReceiptImageService.isAvailable(Receipt.builder().imageExpiresAt(NOW.plusSeconds(60)).build(), NOW)).isFalse();
        assertThat(ReceiptImageService.isAvailable(Receipt.builder().filePath("u/r.jpg").build(), NOW)).isFalse();
    }

    @Test
    void keep_storesACompactJpegUnderAServerChosenKey() throws IOException {
        UUID user = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();

        assertThat(service.keep(png(), user, receipt)).contains(user + "/" + receipt + ".jpg");

        verify(store).put(eq(user + "/" + receipt + ".jpg"), any());
    }

    @Test
    void keep_storageFailure_meansNoImage_notAFailedUpload() throws IOException {
        doThrow(new IOException("disk full")).when(store).put(any(), any());

        assertThat(service.keep(png(), UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void stage_writesTheOriginalToATemporaryFile_withAWhitelistedExtension() throws IOException {
        byte[] original = png();
        Path staged = service.stage(new MockMultipartFile("file", "../../evil.sh", "image/png", original));
        try {
            assertThat(staged.getFileName().toString()).endsWith(".jpg");
            assertThat(Files.readAllBytes(staged)).isEqualTo(original);
        } finally {
            service.unstage(staged);
        }
        assertThat(staged).doesNotExist();

        Path png = service.stage(new MockMultipartFile("file", "receipt.PNG", "image/png", original));
        service.unstage(png);
        assertThat(png.getFileName().toString()).endsWith(".png");
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
