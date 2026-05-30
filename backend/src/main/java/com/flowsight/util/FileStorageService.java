package com.flowsight.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/**
 * Handles local file storage for receipt images.
 *
 * Phase 4 uses the local filesystem under UPLOAD_DIR (default: /tmp/flowsight-receipts).
 * /tmp is writable in Docker without volume permission issues. When Phase 11 wires up
 * S3, this service gains an S3StorageProvider implementation behind the same interface.
 */
@Service
@Slf4j
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS =
        List.of("jpg", "jpeg", "png", "webp", "tiff", "bmp");

    private final Path rootLocation;

    public FileStorageService(
        @Value("${application.storage.upload-dir:/tmp/flowsight-receipts}") String uploadDir
    ) {
        this.rootLocation = Paths.get(uploadDir);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Receipt storage initialized at: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not create upload directory {}: {}", rootLocation, e.getMessage());
        }
    }

    public Path store(MultipartFile file, UUID userId, UUID receiptId) throws IOException {
        String ext = extractExtension(file.getOriginalFilename());
        Path userDir = rootLocation.resolve(userId.toString());
        Files.createDirectories(userDir);

        Path destination = userDir.resolve(receiptId + "." + ext);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        log.debug("Stored receipt {} ({} bytes) at {}", receiptId, file.getSize(), destination);
        return destination;
    }

    public void delete(String filePath) {
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", filePath, e.getMessage());
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "jpg";
        String ext = filename.substring(dot + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : "jpg";
    }
}
