package com.flowsight.ml.forecast;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A user's transactions as complete calendar months plus the current month so far. The first month
 * counts as complete when its first transaction falls in the opening week (the BaselineCalculator rule).
 *
 * @param months    complete months, oldest first
 * @param sameDays  per series, total of the complete months on days up to today's day of month
 */
public record MonthlyHistory(
    LocalDate today, List<YearMonth> months,
    double[] income, double[] spend, Map<TransactionCategory, double[]> categories,
    double incomeToDate, double spendToDate, Map<TransactionCategory, Double> categoriesToDate,
    double incomeSameDays, double spendSameDays, Map<TransactionCategory, Double> categoriesSameDays
) {
    public static final int MAX_MONTHS = 24;
    private static final int FIRST_MONTH_GRACE_DAYS = 7;

    public record Txn(LocalDate date, TransactionType type, TransactionCategory category, double amount) {}

    /**
     * @param earliest the user's first transaction date, which may lie before the loaded window
     */
    public static MonthlyHistory of(List<Txn> txns, LocalDate earliest, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<YearMonth> months = new ArrayList<>();
        if (earliest != null && !earliest.isAfter(today)) {
            YearMonth first = earliest.getDayOfMonth() <= FIRST_MONTH_GRACE_DAYS
                ? YearMonth.from(earliest) : YearMonth.from(earliest).plusMonths(1);
            YearMonth windowStart = current.minusMonths(MAX_MONTHS);
            for (YearMonth m = first.isBefore(windowStart) ? windowStart : first; m.isBefore(current); m = m.plusMonths(1)) {
                months.add(m);
            }
        }
        int n = months.size();
        YearMonth start = n > 0 ? months.get(0) : current;
        double[] income = new double[n];
        double[] spend = new double[n];
        Map<TransactionCategory, double[]> categories = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Double> catToDate = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Double> catSameDays = new EnumMap<>(TransactionCategory.class);
        double incomeToDate = 0, spendToDate = 0, incomeSameDays = 0, spendSameDays = 0;
        int day = today.getDayOfMonth();

        for (Txn t : txns) {
            if (t.date().isAfter(today)) continue;
            YearMonth m = YearMonth.from(t.date());
            boolean credit = t.type() == TransactionType.CREDIT;
            if (m.equals(current)) {
                if (credit) {
                    incomeToDate += t.amount();
                } else {
                    spendToDate += t.amount();
                    if (t.category() != null) {
                        catToDate.merge(t.category(), t.amount(), Double::sum);
                        categories.computeIfAbsent(t.category(), c -> new double[n]);
                    }
                }
                continue;
            }
            if (m.isBefore(start)) continue;
            int i = (int) start.until(m, java.time.temporal.ChronoUnit.MONTHS);
            boolean early = t.date().getDayOfMonth() <= day;
            if (credit) {
                income[i] += t.amount();
                if (early) incomeSameDays += t.amount();
            } else {
                spend[i] += t.amount();
                if (early) spendSameDays += t.amount();
                if (t.category() != null) {
                    categories.computeIfAbsent(t.category(), c -> new double[n])[i] += t.amount();
                    if (early) catSameDays.merge(t.category(), t.amount(), Double::sum);
                }
            }
        }
        return new MonthlyHistory(today, List.copyOf(months), income, spend, categories,
            incomeToDate, spendToDate, catToDate, incomeSameDays, spendSameDays, catSameDays);
    }

    public int dataMonths() {
        return months.size();
    }

    public double[] net() {
        double[] out = new double[income.length];
        for (int i = 0; i < out.length; i++) out[i] = income[i] - spend[i];
        return out;
    }

    public double dayFraction() {
        return (double) today.getDayOfMonth() / today.lengthOfMonth();
    }
}
