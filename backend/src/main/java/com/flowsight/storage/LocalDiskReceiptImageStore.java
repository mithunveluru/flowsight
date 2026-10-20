package com.flowsight.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

// Receipt images under UPLOAD_DIR. Every key must resolve inside that directory, so neither a bad
// key nor an old absolute file_path can reach the report PDFs next to it.
@Component
@ConditionalOnProperty(prefix = "application.storage.receipt-images", name = "store", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalDiskReceiptImageStore implements ReceiptImageStore {

    private final Path root;

    public LocalDiskReceiptImageStore(
        @Value("${application.storage.upload-dir:/tmp/flowsight-receipts}") String uploadDir
    ) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
            log.info("Receipt image storage initialized at: {}", root);
        } catch (IOException e) {
            log.warn("Could not create upload directory {}: {}", root, e.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] image) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, image);
    }

    @Override
    public Optional<byte[]> get(String key) throws IOException {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String key) throws IOException {
        return Files.deleteIfExists(resolve(key));
    }

    // Absolute keys are file_path values written before V21; they resolve to themselves.
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Receipt image key outside the storage root");
        }
        return path;
    }
}
