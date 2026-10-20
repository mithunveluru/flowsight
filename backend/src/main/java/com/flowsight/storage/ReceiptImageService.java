package com.flowsight.storage;

import com.flowsight.entity.Receipt;
import com.flowsight.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Receipt image lifecycle. OCR reads the upload from a temporary file that is removed right after;
 * the store only ever holds the compacted copy, until the draft is reviewed or expires.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ReceiptImageProperties.class)
@Slf4j
public class ReceiptImageService {

    private static final List<String> OCR_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp", "tiff", "bmp");
    private static final int PURGE_BATCH = 500;

    private final ReceiptImageStore      store;
    private final ReceiptImageProperties properties;
    private final ReceiptRepository      receiptRepository;

    /** The original upload in a temporary file for OCR; pass it to {@link #unstage} when done. */
    public Path stage(MultipartFile file) throws IOException {
        Path staged = Files.createTempFile("flowsight-receipt-", "." + extension(file.getOriginalFilename()));
        try {
            Files.write(staged, file.getBytes());
        } catch (IOException | RuntimeException e) {
            unstage(staged);
            throw e;
        }
        return staged;
    }

    public void unstage(Path staged) {
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            log.warn("Could not remove a staged receipt upload: {}", e.getMessage());
        }
    }

    /**
     * Stores the review copy and returns its key; empty if the image cannot be compacted or stored,
     * which only means the review screen shows no image.
     */
    public Optional<String> keep(byte[] original, UUID userId, UUID receiptId) {
        Optional<byte[]> compact = ReceiptImageCompactor.compact(original, properties.maxLongSide(), properties.jpegQuality());
        if (compact.isEmpty()) {
            log.debug("Receipt {} image not kept: format cannot be re-encoded", receiptId);
            return Optional.empty();
        }
        String key = userId + "/" + receiptId + ".jpg";
        try {
            store.put(key, compact.get());
            return Optional.of(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Receipt {} image not kept: {}", receiptId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void delete(String key) {
        if (key == null) return;
        try {
            store.delete(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete a receipt image: {}", e.getClass().getSimpleName());
        }
    }

    public Instant draftExpiry(Instant now) {
        return now.plus(properties.draftRetention());
    }

    // confirming shortens the window; it never revives an image whose draft time is already up
    public Instant reviewedExpiry(Instant current, Instant now) {
        Instant reviewed = now.plus(properties.reviewedRetention());
        return current == null || reviewed.isBefore(current) ? reviewed : current;
    }

    public static boolean isAvailable(Receipt receipt, Instant now) {
        return receipt.getFilePath() != null && receipt.getImageDeletedAt() == null
            && receipt.getImageExpiresAt() != null && receipt.getImageExpiresAt().isAfter(now);
    }

    @Scheduled(fixedDelayString = "${application.storage.receipt-images.cleanup-interval:PT1H}", initialDelayString = "PT5M")
    public void purgeExpired() {
        int purged = purgeExpired(Instant.now());
        if (purged > 0) log.info("Deleted {} expired receipt image(s)", purged);
    }

    // The row is claimed before the file goes, so a confirm racing the job cannot keep a key to a
    // deleted file. Only keys from receipt rows are touched; the store refuses anything outside its root.
    int purgeExpired(Instant now) {
        int purged = 0;
        for (Receipt receipt : receiptRepository.findExpiredImages(now, PageRequest.of(0, PURGE_BATCH))) {
            if (receiptRepository.markImageDeleted(receipt.getId(), now) == 1) {
                delete(receipt.getFilePath());
                purged++;
            }
        }
        return purged;
    }

    private static String extension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "jpg";
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return OCR_EXTENSIONS.contains(ext) ? ext : "jpg";
    }
}
