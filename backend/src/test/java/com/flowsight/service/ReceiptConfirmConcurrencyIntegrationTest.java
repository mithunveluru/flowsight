package com.flowsight.service;

import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.entity.Receipt;
import com.flowsight.entity.ReceiptStatus;
import com.flowsight.entity.Role;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.repository.ReceiptRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V17 against Postgres: the prediction survives confirmation, corrections land in user_feedback,
// and a concurrent double-submit books exactly one transaction.
@SpringBootTest
@ActiveProfiles("test")
class ReceiptConfirmConcurrencyIntegrationTest {

    @Autowired private ReceiptService    receiptService;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private UserRepository    userRepository;
    @Autowired private JdbcTemplate      jdbc;

    private User user;
    private Receipt receipt;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
            .email("ocr-" + UUID.randomUUID() + "@example.com").passwordHash("x").role(Role.USER).build());
        receipt = receiptRepository.save(Receipt.builder()
            .user(user).fileName("r.jpg").fileSize(10L).mimeType("image/jpeg").status(ReceiptStatus.COMPLETED)
            .extractedMerchant("PANDA").extractedAmount(new BigDecimal("45.00")).extractedDate(LocalDate.of(2025, 8, 16))
            .predictedMerchant("PANDA").predictedAmount(new BigDecimal("45.00")).predictedDate(LocalDate.of(2025, 8, 16))
            .currency("SAR").predictedCurrency("SAR").amountConfidence(new BigDecimal("0.950")).ocrDecision("ACCEPT")
            .extractionDetails("{}")
            .build());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    void concurrentConfirmsCreateOneTransaction() throws Exception {
        int callers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return receiptService.confirmReceipt(receipt.getId(), request("54.00"), user).getTransaction().getId();
            }));
        }
        start.countDown();
        List<UUID> ids = new ArrayList<>();
        for (Future<UUID> f : results) ids.add(f.get());
        pool.shutdown();

        assertThat(ids).containsOnly(ids.get(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE receipt_id = ?", Integer.class, receipt.getId()))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_feedback WHERE subject_id = ? AND field = 'amount'", Integer.class, receipt.getId()))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT predicted_amount FROM receipts WHERE id = ?", BigDecimal.class, receipt.getId()))
            .isEqualByComparingTo("45.00");
        assertThat(jdbc.queryForObject("SELECT extracted_amount FROM receipts WHERE id = ?", BigDecimal.class, receipt.getId()))
            .isEqualByComparingTo("54.00");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE resource_id = ? AND action = 'RECEIPT_CONFIRMED'", Integer.class,
            receipt.getId().toString())).isEqualTo(1);
    }

    @Test
    void anotherUsersReceiptIsNotFound() {
        User other = userRepository.save(User.builder()
            .email("ocr-" + UUID.randomUUID() + "@example.com").passwordHash("x").role(Role.USER).build());
        try {
            assertThatThrownBy(() -> receiptService.confirmReceipt(receipt.getId(), request("45.00"), other))
                .isInstanceOf(ResourceNotFoundException.class);
        } finally {
            userRepository.deleteById(other.getId());
        }
    }

    private static ReceiptConfirmRequest request(String amount) {
        ReceiptConfirmRequest r = new ReceiptConfirmRequest();
        r.setMerchant("PANDA");
        r.setAmount(new BigDecimal(amount));
        r.setDate(LocalDate.of(2025, 8, 16));
        r.setCategory(TransactionCategory.GROCERIES);
        return r;
    }
}
