package com.flowsight.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the BALANCE_ONLY ingestion path - drives the public
 * {@link CsvParserService#parse} entry point with CSV byte content and asserts
 * the resulting {@link CsvParserService.CsvRow} list is shape-compatible with
 * what the downstream pipeline already consumes (date / description / amount /
 * isDebit). The test confirms reconstructed rows look identical to natively
 * parsed ones.
 */
class BalanceReconstructionIngestionTest {

    private CsvParserService parser;

    @BeforeEach
    void setUp() {
        parser = new CsvParserService(
            new StatementFormatDetector(),
            new BalanceDeltaCalculator(),
            new ReconstructedTransactionMapper()
        );
    }

    private CsvParserService.ParseResult parse(String csv) throws IOException {
        return parser.parse(new MockMultipartFile(
            "file", "statement.csv", "text/csv", csv.getBytes()));
    }

    // -------------------------------------------------------------------------
    // Spec example: balance-only statement reconstructs amounts
    // -------------------------------------------------------------------------
    @Test
    void specExample_balanceOnlyStatement_reconstructsExpenseAndIncome() throws Exception {
        String csv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-03,Salary Credit,14500
            """;
        var result = parse(csv);

        assertThat(result.getRows()).hasSize(2);

        var walmart = result.getRows().get(0);
        assertThat(walmart.getDescription()).isEqualTo("Walmart");
        assertThat(walmart.getAmount()).isEqualByComparingTo("500");
        assertThat(walmart.isDebit()).isTrue();
        assertThat(walmart.getDate()).isEqualTo(LocalDate.of(2026, 1, 2));

        var salary = result.getRows().get(1);
        assertThat(salary.getDescription()).isEqualTo("Salary Credit");
        assertThat(salary.getAmount()).isEqualByComparingTo("5000");
        assertThat(salary.isDebit()).isFalse();
    }

    @Test
    void explicitAmountFormat_stillUsesLegacyPath_notReconstruction() throws Exception {
        // The presence of an `amount` column must route through the NATIVE parser,
        // not the balance-delta reconstructor, even if a balance column is also present.
        String csv = """
            date,description,amount,type,balance
            2026-01-02,Walmart,500,DEBIT,9500
            2026-01-03,Salary,5000,CREDIT,14500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        // Both rows came through the explicit path - their amounts match the column, not deltas
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("500");
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void mixedCreditsAndDebits_overFullMonth() throws Exception {
        String csv = """
            date,description,balance
            2026-02-01,Opening Balance,50000
            2026-02-03,Rent,30000
            2026-02-05,Salary,105000
            2026-02-10,Grocery,103500
            2026-02-15,Fuel,101000
            2026-02-20,Refund,101800
            2026-02-28,Closing Balance,101800
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(5);

        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Rent");
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("20000");
        assertThat(result.getRows().get(0).isDebit()).isTrue();

        assertThat(result.getRows().get(1).getDescription()).isEqualTo("Salary");
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("75000");
        assertThat(result.getRows().get(1).isDebit()).isFalse();

        assertThat(result.getRows().get(4).getDescription()).isEqualTo("Refund");
        assertThat(result.getRows().get(4).isDebit()).isFalse();
        assertThat(result.getRows().get(4).getAmount()).isEqualByComparingTo("800");
    }

    @Test
    void noExplicitOpeningRow_firstRowSeedsSilently() throws Exception {
        String csv = """
            date,description,balance
            2026-01-02,Walmart,9500
            2026-01-03,Salary Credit,14500
            """;
        var result = parse(csv);
        // First row anchors silently - only the second row produces a transaction.
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Salary Credit");
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("5000");
        assertThat(result.getRows().get(0).isDebit()).isFalse();
    }

    @Test
    void missingBalanceRow_isSkipped_validRowsPersist() throws Exception {
        String csv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-03,Broken row,
            2026-01-04,Salary,14500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Walmart");
        assertThat(result.getRows().get(1).getDescription()).isEqualTo("Salary");
        assertThat(result.getErrors()).anyMatch(e -> e.contains("missing date or balance"));
    }

    @Test
    void malformedRows_areSkippedWithErrors() throws Exception {
        String csv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            not-a-date,Bad date row,9500
            2026-01-03,Bad balance,not-a-number
            2026-01-04,Salary,15000
            """;
        var result = parse(csv);
        // Only the salary row reconstructs because the chain of balances is broken by the
        // malformed rows. The malformed rows are reported in errors and dropped.
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Salary");
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void duplicateRows_areDedupedAcrossFullPath() throws Exception {
        String csv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-02,Walmart,9500
            2026-01-03,Salary,14500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Walmart");
        assertThat(result.getRows().get(1).getDescription()).isEqualTo("Salary");
        assertThat(result.getErrors()).anyMatch(e -> e.contains("duplicate"));
    }

    @Test
    void runningBalanceHeader_variant_isRecognized() throws Exception {
        // Banks use varying header names - "Running Balance" must work too.
        String csv = """
            Date,Narration,Running Balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-03,Salary Credit,14500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Walmart");
    }

    @Test
    void emptyBalanceOnlyFile_yieldsEmptyResult_noErrors() throws Exception {
        String csv = "date,description,balance\n";
        var result = parse(csv);
        assertThat(result.getRows()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void reconstructedRows_areIndistinguishableFromNativeParsedRows() throws Exception {
        // Same logical transactions expressed two ways. The downstream pipeline shouldn't
        // be able to tell the difference - both produce CsvRows with the same shape.
        String nativeCsv = """
            date,description,amount,type
            2026-01-02,Walmart,500,DEBIT
            2026-01-03,Salary Credit,5000,CREDIT
            """;
        String balanceCsv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-03,Salary Credit,14500
            """;

        var nativeResult  = parse(nativeCsv);
        var balanceResult = parse(balanceCsv);

        assertThat(balanceResult.getRows()).hasSameSizeAs(nativeResult.getRows());
        for (int i = 0; i < nativeResult.getRows().size(); i++) {
            CsvParserService.CsvRow n = nativeResult.getRows().get(i);
            CsvParserService.CsvRow b = balanceResult.getRows().get(i);
            assertThat(b.getDate()).isEqualTo(n.getDate());
            assertThat(b.getDescription()).isEqualTo(n.getDescription());
            assertThat(b.getAmount()).isEqualByComparingTo(n.getAmount());
            assertThat(b.isDebit()).isEqualTo(n.isDebit());
        }
    }

    @Test
    void allReconstructedRows_haveNonNullDateAmountAndDescription() throws Exception {
        // Contract guard: downstream TransactionIngestionPipeline.processCsvRow will NPE
        // if any of these is null.
        String csv = """
            date,description,balance
            2026-01-01,Opening Balance,10000
            2026-01-02,Walmart,9500
            2026-01-03,Salary,14500
            2026-01-10,Coffee,14200
            """;
        var result = parse(csv);
        assertThat(result.getRows())
            .allSatisfy(r -> {
                assertThat(r.getDate()).isNotNull();
                assertThat(r.getDescription()).isNotNull();
                assertThat(r.getAmount()).isNotNull();
            });
    }

    @Test
    void existingNativeCsv_unchangedByThisChange() throws Exception {
        // Regression guard: the previously supported NATIVE format must keep working
        // exactly as before.
        String csv = """
            date,description,amount,type
            2026-04-01,Starbucks coffee,250,DEBIT
            2026-04-05,Salary deposit,75000,CREDIT
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(1).isDebit()).isFalse();
        assertThat(result.getErrors()).isEmpty();
    }
}
