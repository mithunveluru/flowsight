package com.flowsight.llm;

import com.flowsight.ocr.MoneyParser;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects prose that states a number, percentage, amount or date absent from its facts.
 * A written number matches a fact when the fact rounds to it at the precision it was
 * written with: "₹1,234", "1234" and "₹1.2k" all match 1234.40; "22%" matches 22.4.
 * Percentages only match percentage facts and currency amounts only match amounts.
 * Verbatim text facts (merchant names, goal names) are removed first so digits inside them
 * do not count. Spelled-out numbers ("five thousand") are checked like digits; a bare "one"
 * is ignored because it is usually a pronoun.
 */
public final class GroundingValidator {

    public record Result(boolean grounded, List<String> ungrounded) {
    }

    private static final String MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?"
        + "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?";
    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern NUMERIC_DATE =
        Pattern.compile("(?<![\\d.,])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?![\\d.,])");
    private static final Pattern DAY_MONTH = Pattern.compile(
        "(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?" + MONTH + "(?:,?\\s+(\\d{4}))?(?![\\w])", CI);
    private static final Pattern MONTH_DAY = Pattern.compile(
        "\\b" + MONTH + "\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)(?:,?\\s+(\\d{4}))?(?![\\w])", CI);
    private static final Pattern MONTH_YEAR = Pattern.compile("\\b" + MONTH + "\\s+'?(\\d{4}|(?<=')\\d{2})(?!\\d)", CI);

    private static final Pattern NUMBER = Pattern.compile(
        "(?<![A-Za-z0-9_])(\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:\\.(\\d+))?"
        + "(?:(\\s*(?:%|(?i:percent|per\\s?cent)\\b))"
        + "|(k|K|L|cr|Cr|mn|bn|M)\\b"
        + "|\\s*((?i:thousand|lakhs?|lacs?|crores?|million|billion))\\b"
        + "|(st|nd|rd|th|x)\\b)?");
    private static final Pattern CURRENCY_BEFORE =
        Pattern.compile("(₹|rs\\.?|inr|\\$|usd|aed|sar|dhs?|€|£)\\s*$", CI);
    private static final Pattern CURRENCY_AFTER =
        Pattern.compile("^\\s*(rupees?|inr|aed|sar|usd|dirhams?|riyals?)\\b", CI);
    private static final Pattern HEDGE =
        Pattern.compile("(about|around|approximately|approx\\.?|roughly|nearly|almost|close to|some|~)\\s*\\S{0,4}\\s*$", CI);
    private static final Pattern WORD = Pattern.compile("[a-z]+");

    private static final Map<String, Long> SMALL = Map.ofEntries(
        Map.entry("zero", 0L), Map.entry("one", 1L), Map.entry("two", 2L), Map.entry("three", 3L),
        Map.entry("four", 4L), Map.entry("five", 5L), Map.entry("six", 6L), Map.entry("seven", 7L),
        Map.entry("eight", 8L), Map.entry("nine", 9L), Map.entry("ten", 10L), Map.entry("eleven", 11L),
        Map.entry("twelve", 12L), Map.entry("thirteen", 13L), Map.entry("fourteen", 14L),
        Map.entry("fifteen", 15L), Map.entry("sixteen", 16L), Map.entry("seventeen", 17L),
        Map.entry("eighteen", 18L), Map.entry("nineteen", 19L), Map.entry("twenty", 20L),
        Map.entry("thirty", 30L), Map.entry("forty", 40L), Map.entry("fifty", 50L), Map.entry("sixty", 60L),
        Map.entry("seventy", 70L), Map.entry("eighty", 80L), Map.entry("ninety", 90L));
    private static final Map<String, Long> SCALE = Map.of(
        "thousand", 1_000L, "lakh", 100_000L, "lakhs", 100_000L, "crore", 10_000_000L,
        "crores", 10_000_000L, "million", 1_000_000L, "billion", 1_000_000_000L);

    private GroundingValidator() {
    }

    public static Result check(String text, FactSet facts) {
        if (text == null || text.isBlank()) return new Result(true, List.of());
        char[] s = normalize(text).toCharArray();
        maskTextFacts(s, facts.texts());
        List<String> bad = new ArrayList<>();
        checkDates(s, facts.dates(), bad);
        checkNumbers(s, facts, bad);
        checkNumberWords(s, facts, bad);
        return new Result(bad.isEmpty(), bad);
    }

    private static String normalize(String text) {
        return MoneyParser.normalizeDigits(text)
            .replace(' ', ' ').replace(' ', ' ').replace('−', '-');
    }

    private static void maskTextFacts(char[] s, List<String> texts) {
        List<String> sorted = new ArrayList<>(texts);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        for (String t : sorted) {
            Matcher m = Pattern.compile(Pattern.quote(normalize(t)), CI).matcher(new String(s));
            while (m.find()) blank(s, m.start(), m.end());
        }
    }

    private static void checkDates(char[] s, List<LocalDate> dates, List<String> bad) {
        scan(s, ISO_DATE, m -> full(dates, num(m.group(1)), num(m.group(2)), num(m.group(3))), bad);
        scan(s, NUMERIC_DATE, m -> {
            int a = num(m.group(1)), b = num(m.group(2)), y = year(m.group(3));
            return full(dates, y, b, a) || full(dates, y, a, b);
        }, bad);
        scan(s, DAY_MONTH, m -> dayMonth(dates, month(m.group(2)), num(m.group(1)), m.group(3)), bad);
        scan(s, MONTH_DAY, m -> dayMonth(dates, month(m.group(1)), num(m.group(2)), m.group(3)), bad);
        scan(s, MONTH_YEAR, m -> {
            int y = year(m.group(2)), mo = month(m.group(1));
            return dates.stream().anyMatch(d -> d.getYear() == y && d.getMonthValue() == mo);
        }, bad);
    }

    private static boolean dayMonth(List<LocalDate> dates, int month, int day, String year) {
        if (year != null) return full(dates, year(year), month, day);
        return dates.stream().anyMatch(d -> d.getMonthValue() == month && d.getDayOfMonth() == day);
    }

    private static boolean full(List<LocalDate> dates, int y, int m, int d) {
        try {
            return dates.contains(LocalDate.of(y, m, d));
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static void checkNumbers(char[] s, FactSet facts, List<String> bad) {
        String str = new String(s);
        Matcher m = NUMBER.matcher(str);
        while (m.find()) {
            String digits = m.group(1).replace(",", "");
            String decimals = m.group(2);
            boolean percent = m.group(3) != null;
            String scaleWord = m.group(4) != null ? m.group(4) : m.group(5);
            BigDecimal value = new BigDecimal(decimals == null ? digits : digits + "." + decimals);
            BigDecimal unit = BigDecimal.ONE.movePointLeft(decimals == null ? 0 : decimals.length());
            if (scaleWord != null) {
                BigDecimal scale = BigDecimal.valueOf(scaleOf(scaleWord));
                value = value.multiply(scale);
                unit = unit.multiply(scale);
            }
            String before = str.substring(Math.max(0, m.start() - 24), m.start());
            boolean currency = CURRENCY_BEFORE.matcher(before).find()
                || CURRENCY_AFTER.matcher(str.substring(m.end(), Math.min(str.length(), m.end() + 12))).find();
            if (decimals == null && scaleWord == null && !percent && value.compareTo(BigDecimal.valueOf(100)) >= 0
                && HEDGE.matcher(before).find()) {
                // "about ₹45,000" is read to two significant figures at most
                int zeros = Math.min(trailingZeros(digits), digits.length() - 2);
                if (zeros > 0) unit = BigDecimal.ONE.movePointRight(zeros);
            }

            List<BigDecimal> candidates = new ArrayList<>();
            if (percent) {
                candidates.addAll(facts.numbers(FactSet.Kind.PERCENT));
            } else {
                candidates.addAll(facts.numbers(FactSet.Kind.AMOUNT));
                if (!currency) {
                    candidates.addAll(facts.numbers(FactSet.Kind.COUNT));
                    candidates.addAll(facts.numbers(FactSet.Kind.PERCENT));
                }
            }
            boolean ok = matchesAny(value, unit, candidates);
            if (!ok && !currency && !percent && scaleWord == null && decimals == null && digits.length() == 4) {
                int y = Integer.parseInt(digits);
                ok = facts.dates().stream().anyMatch(d -> d.getYear() == y);
            }
            if (!ok) bad.add(m.group().trim());
            // its scale word ("lakh") must not be read again as a spelled-out number
            blank(s, m.start(), m.end());
        }
    }

    private static void checkNumberWords(char[] s, FactSet facts, List<String> bad) {
        Matcher m = WORD.matcher(new String(s).toLowerCase());
        List<String> words = new ArrayList<>();
        while (m.find()) words.add(m.group());
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            boolean starts = SMALL.containsKey(w) || SCALE.containsKey(w) || w.equals("hundred");
            if (!starts) continue;
            int j = i;
            List<String> run = new ArrayList<>();
            while (j < words.size()) {
                String x = words.get(j);
                if (SMALL.containsKey(x) || SCALE.containsKey(x) || x.equals("hundred")) {
                    run.add(x);
                } else if (!(x.equals("and") && !run.isEmpty())) {
                    break;
                }
                j++;
            }
            i = j - 1;
            boolean hasScale = run.stream().anyMatch(x -> SCALE.containsKey(x) || x.equals("hundred"));
            if (run.size() == 1 && run.get(0).equals("one")) continue;
            if (run.size() == 1 && !hasScale && !SMALL.containsKey(run.get(0))) continue;
            long value = wordsValue(run);
            boolean percent = j < words.size() && words.get(j).equals("percent");
            List<BigDecimal> candidates = new ArrayList<>(facts.numbers(FactSet.Kind.PERCENT));
            if (!percent) {
                candidates.addAll(facts.numbers(FactSet.Kind.AMOUNT));
                candidates.addAll(facts.numbers(FactSet.Kind.COUNT));
            }
            if (!matchesAny(BigDecimal.valueOf(value), BigDecimal.ONE, candidates)) bad.add(String.join(" ", run));
        }
    }

    private static long wordsValue(List<String> run) {
        long total = 0, current = 0;
        for (String w : run) {
            if (SMALL.containsKey(w)) {
                current += SMALL.get(w);
            } else if (w.equals("hundred")) {
                current = Math.max(current, 1) * 100;
            } else if (SCALE.containsKey(w)) {
                total += Math.max(current, 1) * SCALE.get(w);
                current = 0;
            }
        }
        return total + current;
    }

    private static boolean matchesAny(BigDecimal value, BigDecimal unit, List<BigDecimal> candidates) {
        BigDecimal tolerance = unit.divide(BigDecimal.valueOf(2)).add(new BigDecimal("0.000001"));
        for (BigDecimal c : candidates) {
            if (c.subtract(value).abs().compareTo(tolerance) <= 0) return true;
        }
        return false;
    }

    private interface DateCheck {
        boolean ok(Matcher m);
    }

    private static void scan(char[] s, Pattern p, DateCheck check, List<String> bad) {
        Matcher m = p.matcher(new String(s));
        while (m.find()) {
            if (!check.ok(m)) bad.add(m.group().trim());
            blank(s, m.start(), m.end());
        }
    }

    private static void blank(char[] s, int from, int to) {
        Arrays.fill(s, from, to, ' ');
    }

    private static long scaleOf(String w) {
        String x = w.toLowerCase();
        if (x.equals("k") || x.equals("thousand")) return 1_000L;
        if (x.equals("l") || x.startsWith("lakh") || x.startsWith("lac")) return 100_000L;
        if (x.equals("cr") || x.startsWith("crore")) return 10_000_000L;
        if (x.equals("m") || x.equals("mn") || x.equals("million")) return 1_000_000L;
        if (x.equals("bn") || x.equals("billion")) return 1_000_000_000L;
        return 1L;
    }

    private static int trailingZeros(String digits) {
        int n = 0;
        for (int i = digits.length() - 1; i > 0 && digits.charAt(i) == '0'; i--) n++;
        return n;
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static int year(String s) {
        int y = Integer.parseInt(s);
        return s.length() == 2 ? 2000 + y : y;
    }

    private static int month(String s) {
        String m = s.toLowerCase().replace(".", "");
        return switch (m.substring(0, 3)) {
            case "jan" -> 1; case "feb" -> 2; case "mar" -> 3; case "apr" -> 4;
            case "may" -> 5; case "jun" -> 6; case "jul" -> 7; case "aug" -> 8;
            case "sep" -> 9; case "oct" -> 10; case "nov" -> 11; default -> 12;
        };
    }
}
