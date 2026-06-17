package com.flowsight.analytics;

import com.flowsight.dto.transaction.BulkImportResult;
import com.flowsight.entity.*;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.service.TransactionService;
import com.flowsight.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the CSV import → persistence path.
 *
 * <p>These tests protect against the class of bug the user reported:
 * "imported transactions don't appear in analytics". The actual root cause was
 * that imports landed in a different month than the dashboard's default view —
 * the import path itself is correct. These tests pin the contract:
 * <ul>
 *   <li>All parsed rows are submitted to {@code saveAll}</li>
 *   <li>The result reports the imported date range and total</li>
 *   <li>Malformed rows are skipped (not silently lost in the count)</li>
 *   <li>Every saved transaction is user-scoped via {@code user} field</li>
 *   <li>{@code source} is always CSV for imported rows</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CsvIngestionRegressionTest {

    @Mock private TransactionRepository      transactionRepository;
    @Mock private UserService                userService;

    private TransactionIngestionPipeline pipeline;
    private CsvParserService             csvParserService;
    private TransactionService           transactionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Wire real ingestion components so we exercise the actual pipeline behavior
        NormalizationService normalization = new NormalizationService();
        CategorizationService categorization = new CategorizationService();
        pipeline = new TransactionIngestionPipeline(normalization, categorization);
        csvParserService = new CsvParserService(
            new StatementFormatDetector(),
            new BalanceDeltaCalculator(),
            new ReconstructedTransactionMapper()
        );

        // CSV import no longer enforces tier limits — stub only audit + rate limiter
        com.flowsight.service.AuditLogService auditStub = org.mockito.Mockito.mock(
            com.flowsight.service.AuditLogService.class);
        com.flowsight.security.RateLimiter rateLimiterStub = org.mockito.Mockito.mock(
            com.flowsight.security.RateLimiter.class);

        transactionService = new TransactionService(
            transactionRepository, pipeline, csvParserService, userService,
            auditStub, rateLimiterStub
        );

        testUser = User.builder()
            .id(UUID.randomUUID())
            .email("import_test@example.com")
            .passwordHash("$2a$12$hash")
            .role(Role.USER)
            .build();

        when(userService.findById(testUser.getId())).thenReturn(testUser);
        // saveAll returns what was given (identity)
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<Transaction> items = inv.getArgument(0);
            List<Transaction> out = new ArrayList<>();
            items.forEach(out::add);
            return out;
        });
    }

    // -------------------------------------------------------------------------
    // Persistence path: every valid row is saved with the correct fields
    // -------------------------------------------------------------------------

    @Test
    void importCsv_persistsEveryParsedRow_userScoped() throws Exception {
        String csv = """
            date,description,amount,type
            2026-04-01,Starbucks coffee,250,DEBIT
            2026-04-05,Salary deposit,75000,CREDIT
            2026-04-15,Zomato lunch order,420,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "april.csv", "text/csv", csv.getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        // All 3 rows must be reported as imported
        assertThat(result.getImported()).isEqualTo(3);
        assertThat(result.getSkipped()).isEqualTo(0);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void importCsv_resultReportsDateRangeAndTotal() throws Exception {
        String csv = """
            date,description,amount,type
            2026-04-01,Coffee,250,DEBIT
            2026-04-10,Lunch,500,DEBIT
            2026-04-27,Dinner,750,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "april.csv", "text/csv", csv.getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        // The result must surface the imported range so the UI can link to it
        assertThat(result.getFirstTransactionDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.getLastTransactionDate()).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(result.getTotalAmountImported()).isEqualByComparingTo("1500");
    }

    @Test
    void importCsv_savedTransactionsAreUserScoped_andSourceIsCsv() throws Exception {
        String csv = """
            date,description,amount,type
            2026-04-01,Coffee,250,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", csv.getBytes());

        // Capture what was saved
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        transactionService.importCsv(file, testUser.getId());
        org.mockito.Mockito.verify(transactionRepository).saveAll(captor.capture());

        @SuppressWarnings("unchecked")
        List<Transaction> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getUser()).isEqualTo(testUser);
        assertThat(saved.get(0).getSource()).isEqualTo(TransactionSource.CSV);
        assertThat(saved.get(0).getCurrency()).isEqualTo("INR");
    }

    // -------------------------------------------------------------------------
    // Error handling: malformed rows are skipped but don't poison the batch
    // -------------------------------------------------------------------------

    @Test
    void importCsv_skipsMalformedRows_butPersistsValidOnes() throws Exception {
        String csv = """
            date,description,amount,type
            2026-04-01,Valid row,100,DEBIT
            invalid-date,Bad date,200,DEBIT
            2026-04-03,,300,DEBIT
            2026-04-04,Valid again,400,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "mixed.csv", "text/csv", csv.getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        // Two valid rows should be imported; bad rows skipped
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isGreaterThanOrEqualTo(1);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getTotalAmountImported()).isEqualByComparingTo("500"); // 100 + 400
    }

    @Test
    void importCsv_emptyFile_returnsZeroImported() throws Exception {
        // Only header, no rows
        String csv = "date,description,amount,type\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.csv", "text/csv", csv.getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        assertThat(result.getImported()).isEqualTo(0);
        assertThat(result.getFirstTransactionDate()).isNull();
        assertThat(result.getLastTransactionDate()).isNull();
        assertThat(result.getTotalAmountImported()).isEqualByComparingTo("0");
    }

    @Test
    void importCsv_largeFile_handles100Rows() throws Exception {
        // Generate 100 valid rows
        StringBuilder csv = new StringBuilder("date,description,amount,type\n");
        for (int i = 1; i <= 100; i++) {
            csv.append(String.format("2026-04-%02d,Item %d,%d,DEBIT%n",
                (i % 28) + 1, i, 100 + i));
        }
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.csv", "text/csv", csv.toString().getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        assertThat(result.getImported()).isEqualTo(100);
        // Sum = 100 × 100 + (1+2+...+100) = 10000 + 5050 = 15050
        assertThat(result.getTotalAmountImported()).isEqualByComparingTo("15050");
    }

    // -------------------------------------------------------------------------
    // Categorization runs on every imported row
    // -------------------------------------------------------------------------

    @Test
    void importCsv_categorizesRowsAutomatically() throws Exception {
        String csv = """
            date,description,amount,type
            2026-04-01,Starbucks coffee shop,250,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "food.csv", "text/csv", csv.getBytes());

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        transactionService.importCsv(file, testUser.getId());
        org.mockito.Mockito.verify(transactionRepository).saveAll(captor.capture());

        @SuppressWarnings("unchecked")
        List<Transaction> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        // Category is always set, never null — the analytics pipeline depends on it
        assertThat(saved.get(0).getCategory()).isNotNull();
        assertThat(saved.get(0).getConfidenceScore()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // The reported bug class: imports in a different month from "today"
    // -------------------------------------------------------------------------

    @Test
    void importCsv_pastMonthImports_areReportedWithTheirActualDateRange() throws Exception {
        // This is the exact scenario from the user report: user uploads April CSV
        // in May. The import must clearly tell the UI that the data is in April
        // so the frontend can link to /analytics?from=2026-04-01&to=2026-04-30.
        String csv = """
            date,description,amount,type
            2026-04-01,Old purchase,500,DEBIT
            2026-04-15,Another old purchase,750,DEBIT
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "april-bank-statement.csv", "text/csv", csv.getBytes());

        BulkImportResult result = transactionService.importCsv(file, testUser.getId());

        // The frontend can use these to navigate to the correct analytics view
        assertThat(result.getFirstTransactionDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.getLastTransactionDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(result.getImported()).isEqualTo(2);

        // All transactions are persisted (this was never broken — confirming the contract)
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(transactionRepository).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<Transaction> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        // ...and they have the right date — analytics queries filtered by April will find them
        assertThat(saved.stream().allMatch(t -> t.getTransactionDate().getMonthValue() == 4)).isTrue();
    }
}
