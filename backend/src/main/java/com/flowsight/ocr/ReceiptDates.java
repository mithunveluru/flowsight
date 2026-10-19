package com.flowsight.ocr;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the transaction date on a receipt. Numeric dates whose day and month are both <= 12
 * are read month-first only for US-dollar receipts and day-first otherwise, and are flagged
 * ambiguous so the review screen can ask. A receipt without a readable date yields no date.
 */
public final class ReceiptDates {

    /** date is null when none was found; ambiguous when day/month order came from the locale. */
    public record Guess(LocalDate date, boolean ambiguous, boolean labelled, String text) {
        static final Guess NONE = new Guess(null, false, false, null);
    }

    private static final Pattern ISO = Pattern.compile("(?<!\\d)(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)");
    private static final Pattern NUMERIC = Pattern.compile("(?<![\\d.])(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?![\\d])");
    private static final Pattern TEXTUAL_DMY = Pattern.compile(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-/.,]*([A-Za-z]{3,9})[\\s\\-/.,']*(\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern TEXTUAL_MDY = Pattern.compile(
        "\\b([A-Za-z]{3,9})[\\s.\\-]+(\\d{1,2})(?:st|nd|rd|th)?,?[\\s\\-]+(\\d{4})(?!\\d)");
    private static final Pattern DATE_LABEL = Pattern.compile("\\b(?:date|dated|dt|bill\\s+date|inv(?:oice)?\\s+date)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final List<String> MONTHS = List.of(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
        "november", "december");
    private static final int EARLIEST_YEAR = 2000;

    private ReceiptDates() {
    }

    public static Guess find(List<String> lines, String currency) {
        return find(lines, currency, Clock.systemDefaultZone());
    }

    static Guess find(List<String> lines, String currency, Clock clock) {
        LocalDate latest = LocalDate.now(clock).plusDays(1);
        boolean monthFirst = "USD".equals(currency);
        List<Guess> found = new ArrayList<>();
        for (String raw : lines) {
            String line = MoneyParser.normalizeDigits(raw);
            boolean labelled = DATE_LABEL.matcher(line).find();
            collect(line, labelled, monthFirst, latest, found);
        }
        Guess best = Guess.NONE;
        for (Guess g : found) {
            if (best.date() == null || rank(g) > rank(best)) best = g;
        }
        return best;
    }

    private static int rank(Guess g) {
        return (g.labelled() ? 2 : 0) + (g.ambiguous() ? 0 : 1);
    }

    private static void collect(String line, boolean labelled, boolean monthFirst, LocalDate latest, List<Guess> out) {
        Matcher iso = ISO.matcher(line);
        while (iso.find()) {
            add(out, date(i(iso.group(1)), i(iso.group(2)), i(iso.group(3))), false, labelled, iso.group(), latest);
        }
        Matcher num = NUMERIC.matcher(line);
        while (num.find()) {
            int a = i(num.group(1));
            int b = i(num.group(2));
            int year = year(num.group(3));
            boolean ambiguous = a <= 12 && b <= 12 && a != b;
            boolean dayFirst = a > 12 || (b <= 12 && !monthFirst);
            LocalDate d = dayFirst ? date(year, b, a) : date(year, a, b);
            add(out, d, ambiguous, labelled, num.group(), latest);
        }
        Matcher dmy = TEXTUAL_DMY.matcher(line);
        while (dmy.find()) {
            Integer month = month(dmy.group(2));
            if (month != null) add(out, date(year(dmy.group(3)), month, i(dmy.group(1))), false, labelled, dmy.group(), latest);
        }
        Matcher mdy = TEXTUAL_MDY.matcher(line);
        while (mdy.find()) {
            Integer month = month(mdy.group(1));
            if (month != null) add(out, date(i(mdy.group(3)), month, i(mdy.group(2))), false, labelled, mdy.group(), latest);
        }
    }

    private static void add(List<Guess> out, LocalDate d, boolean ambiguous, boolean labelled, String text, LocalDate latest) {
        if (d == null || d.getYear() < EARLIEST_YEAR || d.isAfter(latest)) return;
        out.add(new Guess(d, ambiguous, labelled, text.trim()));
    }

    // "Nov", "Sept" and "November" are months; "Market" is not
    private static Integer month(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        for (int m = 0; m < MONTHS.size(); m++) {
            if (w.length() >= 3 && MONTHS.get(m).startsWith(w)) return m + 1;
        }
        return null;
    }

    private static int year(String y) {
        int v = i(y);
        return y.length() == 2 ? 2000 + v : v;
    }

    private static LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }
}
