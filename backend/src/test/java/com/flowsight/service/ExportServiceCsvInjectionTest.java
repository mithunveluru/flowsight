package com.flowsight.service;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// CSV formula injection (CWE-1236): free-text fields starting with =,+,-,@ must be
// neutralized with a leading apostrophe; numeric columns must stay untouched.
@ExtendWith(MockitoExtension.class)
class ExportServiceCsvInjectionTest {

    @Mock private TransactionRepository transactionRepository;

    @Test
    void neutralizesFormulaPrefixesInTextFieldsButNotAmounts() {
        Transaction tx = Transaction.builder()
            .transactionDate(LocalDate.of(2026, 1, 15))
            .description("=cmd|' /C calc'!A0")
            .merchant("@SUM(1+1)")
            .amount(new BigDecimal("-42.50"))
            .currency("INR")
            .type(TransactionType.DEBIT)
            .notes("+dangerous")
            .build();
        when(transactionRepository.findForExport(any(), any(), any(), any()))
            .thenReturn(List.of(tx));

        String csv = new ExportService(transactionRepository)
            .exportTransactionsCsv(UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);

        String dataRow = csv.split("\r\n")[1];
        assertThat(dataRow).contains("'=cmd");
        assertThat(dataRow).contains("'@SUM(1+1)");
        assertThat(dataRow).contains("'+dangerous");
        // the amount column stays a parseable negative number
        assertThat(dataRow).contains(",-42.50,");
    }
}
