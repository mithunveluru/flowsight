package com.flowsight.ml.recurring;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpendBaselineTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 20);

    private static Transaction debit(String merchant, String amount, LocalDate date) {
        return Transaction.builder().merchant(merchant).amount(new BigDecimal(amount)).type(TransactionType.DEBIT)
            .transactionDate(date).build();
    }

    @Test
    void fallsBackToGlobalThresholdsWithoutTwoCompleteMonths() {
        List<Transaction> txns = List.of(debit("Cafe", "5", AS_OF.minusDays(3)), debit("Rent", "2000", AS_OF.minusDays(10)));
        assertThat(SpendBaseline.personal(txns, AS_OF)).isEqualTo(SpendBaseline.global());
        assertThat(SpendBaseline.global().personal()).isFalse();
    }

    @Test
    void personalThresholdsComeFromMerchantMediansAndMonthlyOutflow() {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 1; m <= 4; m++) {
            LocalDate month = AS_OF.minusMonths(m).withDayOfMonth(1);
            txns.add(debit("Oakwood Apartments", "2000", month.plusDays(1)));
            txns.add(debit("Kroger", "120", month.plusDays(5)));
            txns.add(debit("Netflix", "15.49", month.plusDays(8)));
            // a frequent habit must not drag the ticket bar down to its own price
            for (int d = 0; d < 20; d++) txns.add(debit("Starbucks", "6", month.plusDays(d)));
        }
        SpendBaseline b = SpendBaseline.personal(txns, AS_OF);
        assertThat(b.personal()).isTrue();
        // per-merchant medians 2000, 120, 15.49 and 6: the median of those is (15.49 + 120) / 2
        assertThat(b.maxTicket()).isEqualByComparingTo("67.75");
        // each complete month: 2000 + 120 + 15.49 + 20 x 6 = 2255.49, and 1% of it
        assertThat(b.minMonthly()).isEqualByComparingTo("22.55");
    }
}
