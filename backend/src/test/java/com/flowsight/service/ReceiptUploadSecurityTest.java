package com.flowsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.CategorizationService;
import com.flowsight.analytics.NormalizationService;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.ocr.OcrService;
import com.flowsight.ocr.ReceiptOcrClientService;
import com.flowsight.ocr.ReceiptOcrMapper;
import com.flowsight.ocr.ReceiptParserService;
import com.flowsight.repository.ReceiptRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.security.RateLimiter;
import com.flowsight.util.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Security regression tests for the receipt upload boundary.
 *
 * <p>Upload validation runs before any storage or OCR work, so a rejected file
 * never touches disk, the OCR microservice, or the database. The key new
 * protection under test is magic-byte sniffing: a spoofed {@code Content-Type}
 * cannot smuggle a non-image payload past validation.
 */
class ReceiptUploadSecurityTest {

    private ReceiptRepository      receiptRepository;
    private TransactionRepository  transactionRepository;
    private FileStorageService     fileStorage;
    private ReceiptService         receiptService;
    private User user;

    @BeforeEach
    void setUp() {
        receiptRepository     = mock(ReceiptRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        fileStorage           = mock(FileStorageService.class);

        com.flowsight.service.ReceiptQuotaService quotaService =
            mock(com.flowsight.service.ReceiptQuotaService.class); // requireQuotaAvailable is a no-op mock
        RateLimiter rateLimiter = mock(RateLimiter.class);          // checkUploadAttempt is a no-op mock

        receiptService = new ReceiptService(
            receiptRepository,
            transactionRepository,
            fileStorage,
            new CategorizationService(),
            new NormalizationService(),
            new ObjectMapper(),
            quotaService,
            mock(AuditLogService.class),
            rateLimiter,
            mock(ReceiptOcrClientService.class),
            mock(ReceiptOcrMapper.class),
            mock(OcrService.class),
            mock(ReceiptParserService.class));

        user = User.builder()
            .id(UUID.randomUUID())
            .email("receipt_sec@example.com")
            .passwordHash("$2a$12$hash")
            .role(Role.USER)
            .build();
    }

    @Test
    void spoofedContentType_nonImageBytes_isRejected_neverStored() {
        // Claims to be a PNG but the bytes are an HTML/script payload.
        byte[] notAnImage = "<html><script>alert(1)</script></html>".getBytes();
        MockMultipartFile spoofed = new MockMultipartFile(
            "file", "receipt.png", "image/png", notAnImage);

        assertThatThrownBy(() -> receiptService.processReceipt(spoofed, user))
            .isInstanceOf(FlowsightException.class)
            .hasMessageContaining("does not match a supported image format");

        // The malicious file never reached storage or the database.
        verifyNoInteractions(fileStorage);
        verifyNoInteractions(receiptRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void disallowedMimeType_isRejected() {
        MockMultipartFile pdf = new MockMultipartFile(
            "file", "receipt.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> receiptService.processReceipt(pdf, user))
            .isInstanceOf(FlowsightException.class)
            .hasMessageContaining("Unsupported file type");

        verifyNoInteractions(fileStorage);
    }

    @Test
    void oversizedFile_isRejected_beforeReadingBytes() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile huge = new MockMultipartFile(
            "file", "receipt.png", "image/png", tooBig);

        assertThatThrownBy(() -> receiptService.processReceipt(huge, user))
            .isInstanceOf(FlowsightException.class)
            .hasMessageContaining("5 MB");

        verifyNoInteractions(fileStorage);
    }

    @Test
    void emptyFile_isRejected_withBadRequest() {
        MockMultipartFile empty = new MockMultipartFile(
            "file", "receipt.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> receiptService.processReceipt(empty, user))
            .isInstanceOf(FlowsightException.class)
            .satisfies(e -> assertThat(((FlowsightException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }
}
