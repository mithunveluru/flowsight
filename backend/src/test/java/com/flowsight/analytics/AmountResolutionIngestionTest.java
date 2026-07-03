package com.flowsight.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite for the "amounts silently parsed as zero" bug.
 *
 * <p>Cause: the legacy parsers assumed bank Debit columns are always positive.
 * Statements that exported debits as negative numbers (e.g. {@code -1500.0})
 * failed the {@code > 0} check and fell through to {@code amount = 0}.
 *
 * <p>These tests pin: never silently produce zero; row is either imported with
 * the correct amount + direction or rejected with a clear error.
 */
class AmountResolutionIngestionTest {

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
        return parser.parse(new MockMultipartFile("file", "stmt.csv", "text/csv", csv.getBytes()));
    }

    // The exact CSV from the bug report - debit column carries SIGNED values.
    @Test
    void signedDebitInDebitColumn_resolvesToCorrectExpense_notZero() throws Exception {
        String csv = """
            Date,Narration,Credit Amount,Debit Amount,Closing Balance
            01/06/2026,UPI-GYM MEMBERSHIP,,-1500.0,18500.0
            05/06/2026,NEFT-PARENT ALLOWANCE,5000.0,,22720.0
            """;
        var result = parse(csv);

        assertThat(result.getRows()).hasSize(2);

        var gym = result.getRows().get(0);
        assertThat(gym.getDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(gym.getDescription()).isEqualTo("UPI-GYM MEMBERSHIP");
        assertThat(gym.getAmount()).isEqualByComparingTo("1500.0");
        assertThat(gym.isDebit()).isTrue();

        var allowance = result.getRows().get(1);
        assertThat(allowance.getDescription()).isEqualTo("NEFT-PARENT ALLOWANCE");
        assertThat(allowance.getAmount()).isEqualByComparingTo("5000.0");
        assertThat(allowance.isDebit()).isFalse();
    }

    // Traditional HDFC convention - debits exported as positive magnitudes
    @Test
    void unsignedDebitInDebitColumn_stillResolvesAsDebit() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,UPI-GYM MEMBERSHIP,1500.0,,18500.0
            05/06/2026,SALARY,,75000.0,93500.0
            """;
        var result = parse(csv);

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("1500.0");
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("75000.0");
        assertThat(result.getRows().get(1).isDebit()).isFalse();
    }

    // Column-name aliases - same logical shape, different banks
    @Test
    void withdrawalAndDepositAliases_areRecognized() throws Exception {
        String csv = """
            Date,Description,Withdrawal,Deposit,Balance
            2026-06-01,Card payment,1500,,18500
            2026-06-05,Salary,,75000,93500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("1500");
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("75000");
        assertThat(result.getRows().get(1).isDebit()).isFalse();
    }

    @Test
    void moneyInMoneyOutAliases_areRecognized() throws Exception {
        // Common in UK / international bank exports.
        String csv = """
            Date,Description,Money Out,Money In,Balance
            2026-06-01,Tube fare,150,,9850
            2026-06-02,Refund,,300,10150
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("150");
        assertThat(result.getRows().get(1).isDebit()).isFalse();
    }

    // Single-amount-column layouts
    @Test
    void singleAmountColumn_signedValues_directionFromSign() throws Exception {
        // No type column - sign of the amount tells direction.
        String csv = """
            date,description,amount
            2026-06-01,Walmart,-500
            2026-06-02,Salary,5000
            """;
        var result = parse(csv);
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("500");
        assertThat(result.getRows().get(1).isDebit()).isFalse();
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void singleAmountColumn_withCrDrTypeColumn_typeWinsOverSign() throws Exception {
        String csv = """
            Date,Description,Amount,Cr/Dr
            2026-06-01,Walmart,500,DR
            2026-06-02,Salary,5000,CR
            """;
        var result = parse(csv);
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("500");
        assertThat(result.getRows().get(1).isDebit()).isFalse();
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void transactionAmountAlias_isRecognized() throws Exception {
        String csv = """
            Transaction Date,Description,Transaction Amount,Type
            2026-06-01,Coffee,250,DEBIT
            2026-06-02,Refund,500,CREDIT
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("250");
        assertThat(result.getRows().get(0).isDebit()).isTrue();
        assertThat(result.getRows().get(1).isDebit()).isFalse();
    }

    // Empty / malformed rows
    @Test
    void rowWithBothDebitAndCreditEmpty_isRejected_notSilentlyZero() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,Suspicious empty row,,,18500.0
            02/06/2026,Real expense,500,,18000.0
            """;
        var result = parse(csv);

        // The empty row must be rejected with an error - never imported as amount = 0.
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Real expense");
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 2") && e.contains("No amount"));
    }

    @Test
    void invalidNumericValue_inAmountColumn_isRejected_withClearError() throws Exception {
        String csv = """
            date,description,amount,type
            2026-06-01,Valid,500,DEBIT
            2026-06-02,Broken,abc,DEBIT
            2026-06-03,Also valid,750,CREDIT
            """;
        var result = parse(csv);

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Valid");
        assertThat(result.getRows().get(1).getDescription()).isEqualTo("Also valid");
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 3") && e.contains("invalid amount"));
    }

    @Test
    void invalidNumericValue_inDebitColumn_isRejected_notSilentlyZero() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,Valid debit,500,,9500
            02/06/2026,Garbage,N/A,,9500
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Valid debit");
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 3") && e.contains("invalid debit"));
    }

    // Number-formatting quirks
    @Test
    void commasInNumbers_areStripped() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,Big payment,"1,50,000.50",,8,50,000.00
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("150000.50");
    }

    @Test
    void currencySymbolsAndWhitespace_areStripped() throws Exception {
        String csv = """
            Date,Description,Amount,Type
            2026-06-01,Coffee, ₹250.00 ,DEBIT
            """;
        var result = parse(csv);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void decimalsArePreserved_notTruncated() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,Precise expense,-1499.99,,8500.01
            """;
        var result = parse(csv);
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("1499.99");
    }

    // Unknown header layout
    @Test
    void unrecognizedHeaders_yieldErrorMessage_notSilentZeros() throws Exception {
        String csv = """
            FooDate,BarDescription,BazNumber
            01/06/2026,UPI-GYM MEMBERSHIP,1500
            """;
        var result = parse(csv);

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Unrecognized CSV header layout"));
    }

    // No-amount, no-debit, no-credit row inside a sea of valid rows
    @Test
    void mixedFileWithSomeEmptyAmountRows_importsValidOnesAndReportsRest() throws Exception {
        String csv = """
            Date,Narration,Debit Amount,Credit Amount,Closing Balance
            01/06/2026,Salary,,75000,75000
            02/06/2026,Rent,-30000,,45000
            03/06/2026,Empty fee waiver,,,45000
            04/06/2026,Grocery,-1500,,43500
            """;
        var result = parse(csv);

        assertThat(result.getRows()).hasSize(3);
        assertThat(result.getRows().get(0).getDescription()).isEqualTo("Salary");
        assertThat(result.getRows().get(0).getAmount()).isEqualByComparingTo("75000");
        assertThat(result.getRows().get(1).getDescription()).isEqualTo("Rent");
        assertThat(result.getRows().get(1).getAmount()).isEqualByComparingTo("30000");
        assertThat(result.getRows().get(2).getDescription()).isEqualTo("Grocery");
        assertThat(result.getRows().get(2).getAmount()).isEqualByComparingTo("1500");

        assertThat(result.getErrors()).anyMatch(e -> e.contains("Row 4"));
    }
}
