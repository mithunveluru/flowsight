package com.flowsight.storage;

import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.entity.Receipt;
import com.flowsight.entity.ReceiptStatus;
import com.flowsight.entity.Role;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import com.flowsight.repository.ReceiptRepository;
import com.flowsight.repository.UserRepository;
import com.flowsight.service.ReceiptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// V21 against Postgres and the local disk store: retention timestamps and the cleanup job.
@SpringBootTest
@ActiveProfiles("test")
class ReceiptImageRetentionIntegrationTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired private ReceiptService      receiptService;
    @Autowired private ReceiptImageService images;
    @Autowired private ReceiptImageStore   store;
    @Autowired private ReceiptRepository   receiptRepository;
    @Autowired private UserRepository      userRepository;
    @Autowired private JdbcTemplate        jdbc;
    @Value("${application.storage.upload-dir}") private String uploadDir;

    private User user;

    @BeforeEach
    void setUp() {
        user = newUser();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    void cleanupDeletesOnlyExpiredImages_andMarksTheRow() throws IOException {
        Instant now = Instant.now();
        Receipt expired = receiptWithImage(now.minus(Duration.ofMinutes(1)));
        Receipt live = receiptWithImage(now.plus(Duration.ofHours(1)));

        images.purgeExpired(now);

        Receipt purged = receiptRepository.findById(expired.getId()).orElseThrow();
        assertThat(purged.getFilePath()).isNull();
        assertThat(purged.getImageDeletedAt()).isCloseTo(now, within(Duration.ofMillis(1)));
        assertThat(store.get(expired.getFilePath())).isEmpty();
        assertThat(store.get(live.getFilePath())).isPresent();
        assertThat(receiptRepository.findById(live.getId()).orElseThrow().getImageDeletedAt()).isNull();
    }

    @Test
    void cleanupNeverTouchesReportPdfs_evenIfARowPointsAtOne() throws IOException {
        Path reports = Files.createDirectories(Paths.get(uploadDir, "..", "flowsight-reports").toAbsolutePath().normalize());
        Path pdf = Files.write(reports.resolve(UUID.randomUUID() + ".pdf"), new byte[]{'%', 'P', 'D', 'F'});
        try {
            Receipt bad = receiptRepository.save(draft().filePath("../flowsight-reports/" + pdf.getFileName())
                .imageExpiresAt(Instant.now().minusSeconds(60)).build());
            Receipt legacyAbsolute = receiptRepository.save(draft().filePath(pdf.toString())
                .imageExpiresAt(Instant.now().minusSeconds(60)).build());

            images.purgeExpired(Instant.now());

            assertThat(pdf).exists();
            assertThat(receiptRepository.findById(bad.getId()).orElseThrow().getImageDeletedAt()).isNotNull();
            assertThat(receiptRepository.findById(legacyAbsolute.getId()).orElseThrow().getImageDeletedAt()).isNotNull();
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void confirmShortensRetention_andKeepsTheImageUntilThen() throws IOException {
        Receipt receipt = receiptWithImage(Instant.now().plus(Duration.ofDays(7)));

        receiptService.confirmReceipt(receipt.getId(), confirmRequest(), user);

        Instant expected = Instant.now().plus(Duration.ofHours(24));
        assertThat(receiptRepository.findById(receipt.getId()).orElseThrow().getImageExpiresAt())
            .isCloseTo(expected, within(Duration.ofMinutes(1)));
        images.purgeExpired(Instant.now());
        assertThat(store.get(receipt.getFilePath()).orElseThrow()).isEqualTo(IMAGE);
    }

    @Test
    void deletingAReceipt_deletesItsImageAtOnce() throws IOException {
        Receipt receipt = receiptWithImage(Instant.now().plus(Duration.ofDays(7)));

        receiptService.delete(receipt.getId(), user.getId());

        assertThat(store.get(receipt.getFilePath())).isEmpty();
        assertThat(receiptRepository.findById(receipt.getId())).isEmpty();
    }

    private Receipt receiptWithImage(Instant expiresAt) throws IOException {
        Receipt receipt = receiptRepository.save(draft().imageExpiresAt(expiresAt).build());
        String key = user.getId() + "/" + receipt.getId() + ".jpg";
        store.put(key, IMAGE);
        receipt.setFilePath(key);
        return receiptRepository.save(receipt);
    }

    private Receipt.ReceiptBuilder draft() {
        return Receipt.builder().user(user).fileName("r.jpg").fileSize(10L).mimeType("image/jpeg")
            .status(ReceiptStatus.COMPLETED).extractedMerchant("PANDA").extractedAmount(new BigDecimal("45.00"))
            .extractedDate(LocalDate.of(2025, 8, 16));
    }

    private User newUser() {
        return userRepository.save(User.builder()
            .email("img-" + UUID.randomUUID() + "@example.com").passwordHash("x").role(Role.USER).build());
    }

    private static ReceiptConfirmRequest confirmRequest() {
        ReceiptConfirmRequest r = new ReceiptConfirmRequest();
        r.setMerchant("PANDA");
        r.setAmount(new BigDecimal("45.00"));
        r.setDate(LocalDate.of(2025, 8, 16));
        r.setCategory(TransactionCategory.GROCERIES);
        return r;
    }
}
