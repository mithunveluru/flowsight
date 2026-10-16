package com.flowsight.onboarding;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.flowsight.entity.TransactionCategory.*;
import static com.flowsight.entity.TransactionType.CREDIT;
import static com.flowsight.entity.TransactionType.DEBIT;

/**
 * Three months of synthetic history shaped so every rule engine has something
 * to find: salary and rent, weekly groceries, dining, a subscription whose
 * price went up, two overlapping music subscriptions and a daily coffee habit.
 * Seeded, so the same day always produces the same rows.
 */
final class SampleDataGenerator {

    private static final long SEED = 7_301_995L;

    private SampleDataGenerator() {}

    static List<Transaction> generate(User user, LocalDate today) {
        Rows rows = new Rows(user, today, new Random(SEED));
        // the leak rules look back exactly three months; anchoring monthly
        // charges on that boundary gives the price-increase rule four payments
        LocalDate start = windowStart(today);

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            int dom = d.getDayOfMonth();
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

            if (dom == 1)  rows.add(d, "Northwind Labs", "Salary credit", INCOME, CREDIT, 92_000);
            if (dom == 3)  rows.add(d, "Greenview Apartments", "Monthly rent", OTHER, DEBIT, 22_000);
            if (dom == 7)  rows.add(d, "Index Fund SIP", "Monthly SIP", FINANCE, DEBIT, 10_000);
            if (dom == 12) rows.add(d, "BESCOM", "Electricity bill", UTILITIES, DEBIT, rows.between(1_350, 2_100));
            if (dom == 20) rows.add(d, "Airtel", "Postpaid bill", UTILITIES, DEBIT, 599);
            if (dom == 9)  rows.add(d, "Spotify", "Spotify Premium", SUBSCRIPTIONS, DEBIT, 119);
            if (dom == 15) rows.add(d, "Apple Music", "Apple Music subscription", SUBSCRIPTIONS, DEBIT, 99);

            if (dow == DayOfWeek.SATURDAY) {
                rows.add(d, "BigBasket", "Groceries", GROCERIES, DEBIT, rows.between(1_400, 2_900));
            }
            if (dow == DayOfWeek.WEDNESDAY && d.getDayOfYear() % 2 == 0) {
                rows.add(d, "DMart", "Groceries", GROCERIES, DEBIT, rows.between(800, 1_800));
            }
            if (!weekend && rows.chance(0.6)) {
                rows.add(d, "Third Wave Coffee", "Coffee", FOOD_DINING, DEBIT, rows.between(180, 320));
            }
            if ((dow == DayOfWeek.TUESDAY || dow == DayOfWeek.FRIDAY) && rows.chance(0.8)) {
                rows.add(d, "Swiggy", "Food delivery", FOOD_DINING, DEBIT, rows.between(520, 950));
            }
            if (weekend && rows.chance(0.4)) {
                rows.add(d, "Zomato", "Dinner order", FOOD_DINING, DEBIT, rows.between(600, 1_400));
            }
            if (dow == DayOfWeek.MONDAY && rows.chance(0.9)) {
                rows.add(d, "Uber", "Ride", TRANSPORTATION, DEBIT, rows.between(180, 450));
            }
        }

        for (int month = 0; month <= 3; month++) {
            LocalDate charged = start.plusMonths(month);
            if (!charged.isAfter(today)) {
                rows.add(charged, "Netflix", "Netflix subscription", SUBSCRIPTIONS, DEBIT, month < 2 ? 499 : 649);
            }
        }

        rows.add(today.minusDays(68), "Amazon", "Online order", SHOPPING, DEBIT, 2_499);
        rows.add(today.minusDays(41), "Amazon", "Online order", SHOPPING, DEBIT, 899);
        rows.add(today.minusDays(17), "Amazon", "Online order", SHOPPING, DEBIT, 3_499);
        rows.add(today.minusDays(55), "Apollo Pharmacy", "Pharmacy", HEALTHCARE, DEBIT, 640);
        rows.add(today.minusDays(23), "Apollo Pharmacy", "Pharmacy", HEALTHCARE, DEBIT, 385);
        rows.add(today.minusDays(33), null, "NEFT REF 4471982", UNCATEGORIZED, DEBIT, 2_500);
        rows.add(today.minusDays(6), null, "UPI 98450XXXXX", UNCATEGORIZED, DEBIT, 350);

        return rows.list;
    }

    static LocalDate windowStart(LocalDate today) {
        return today.minusMonths(3);
    }

    private static final class Rows {
        private final User user;
        private final LocalDate today;
        private final Random random;
        private final List<Transaction> list = new ArrayList<>();

        Rows(User user, LocalDate today, Random random) {
            this.user = user;
            this.today = today;
            this.random = random;
        }

        boolean chance(double p) {
            return random.nextDouble() < p;
        }

        int between(int min, int max) {
            return min + random.nextInt(max - min + 1);
        }

        void add(LocalDate date, String merchant, String description,
                 TransactionCategory category, TransactionType type, int rupees) {
            if (date.isAfter(today)) return;
            list.add(Transaction.builder()
                .user(user)
                .amount(BigDecimal.valueOf(rupees).setScale(2, RoundingMode.UNNECESSARY))
                .currency("INR")
                .transactionDate(date)
                .description(description)
                .merchant(merchant)
                .category(category)
                .type(type)
                .source(TransactionSource.SAMPLE)
                .build());
        }
    }
}
