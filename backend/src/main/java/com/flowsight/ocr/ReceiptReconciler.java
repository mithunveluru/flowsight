package com.flowsight.ocr;

import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.ReceiptAmountOption;
import com.flowsight.dto.receipt.ReceiptDecision;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Chooses the receipt total by consistency instead of by label alone. A value read on a total
 * or payment line is checked against subtotal (or the item sum) - discounts + taxes + service
 * (+ tip, +/- round-off), against the card/UPI payment line and against cash minus change, in
 * every OCR pass. Two well-supported totals that no tip or round-off explains are a conflict:
 * the result is ASK_USER with both values offered, never a silent pick.
 */
@Component
public class ReceiptReconciler {

    private static final BigDecimal CENT = new BigDecimal("0.01");
    // a total printed rounded to the rupee/riyal without a round-off line
    private static final BigDecimal UNPRINTED_ROUNDING = new BigDecimal("0.50");
    private static final int MAX_OPTIONS = 4;

    public record TotalAssessment(
        BigDecimal value,
        FieldAssessment assessment,
        boolean conflict,
        List<ReceiptAmountOption> options,
        int pass,
        boolean itemsReconciled
    ) {
    }

    /** Each tax, discount and charge line is rounded on its own, so allow a cent per such line. */
    static BigDecimal tolerance(int components) {
        return CENT.multiply(BigDecimal.valueOf(components));
    }

    private static final class Evidence {
        final BigDecimal value;
        boolean grand;
        boolean labelled;
        boolean payment;
        boolean arithmetic;
        boolean derived;
        boolean lastTotal;
        String label;
        String arithmeticBasis;
        int arithmeticPass = -1;
        int firstPass = -1;
        final TreeSet<Integer> passes = new TreeSet<>();

        Evidence(BigDecimal value) {
            this.value = value;
        }

        boolean strong() {
            return (arithmetic && (labelled || payment)) || (labelled && payment);
        }

        double score() {
            double s = 0;
            if (arithmetic) s += 0.40;
            if (grand) s += 0.25;
            else if (labelled) s += 0.18;
            // what the customer actually paid is the transaction amount, tip included
            if (payment) s += 0.40;
            if (lastTotal) s += 0.04;
            s += 0.03 * Math.min(2, passes.size() - 1);
            return s;
        }

        String basis() {
            List<String> parts = new ArrayList<>();
            if (arithmetic) parts.add("arithmetic" + (arithmeticBasis != null ? "(" + arithmeticBasis + ")" : ""));
            if (labelled) parts.add(grand ? "grand-total-label" : "total-label");
            if (payment) parts.add("payment");
            if (derived) parts.add("derived");
            if (passes.size() > 1) parts.add(passes.size() + "-passes");
            return String.join("+", parts);
        }
    }

    private record Components(
        BigDecimal itemsSum, List<BigDecimal> subtotals, BigDecimal tax, BigDecimal discount, BigDecimal service,
        List<BigDecimal> tips, BigDecimal roundOff, boolean hasRoundOff, boolean hasTax, int count
    ) {
    }

    public TotalAssessment reconcile(List<ReceiptTextParser.Parsed> passes) {
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        boolean itemsReconciledAny = false;
        boolean[] itemsReconciledByPass = new boolean[passes.size()];

        List<Components> components = new ArrayList<>();
        List<List<ReceiptTextParser.AmountLine>> totalsByPass = new ArrayList<>();
        for (int k = 0; k < passes.size(); k++) {
            ReceiptTextParser.Parsed p = passes.get(k);
            Components c = components(p);
            List<ReceiptTextParser.AmountLine> totals = p.amounts().stream()
                .filter(a -> a.kind().isTotal() && a.value().signum() > 0).toList();
            int lastTotalIndex = totals.stream().mapToInt(ReceiptTextParser.AmountLine::index).max().orElse(-1);
            components.add(c);
            totalsByPass.add(totals);

            for (ReceiptTextParser.AmountLine a : totals) {
                Evidence e = evidence.computeIfAbsent(key(a.value()), x -> new Evidence(scale(a.value())));
                e.labelled = true;
                // cash minus change already pointed at this value: that is payment evidence
                if (e.derived) e.payment = true;
                e.derived = false;
                e.grand |= a.kind() == ReceiptLabels.Kind.GRAND_TOTAL;
                e.lastTotal |= a.index() == lastTotalIndex;
                if (e.label == null) e.label = label(a.text());
                touch(e, k);
            }
            for (ReceiptTextParser.AmountLine a : p.ofKind(ReceiptLabels.Kind.PAYMENT)) {
                if (a.value().signum() <= 0) continue;
                Evidence e = evidence.computeIfAbsent(key(a.value()), x -> new Evidence(scale(a.value())));
                e.payment = true;
                e.derived = false;
                if (e.label == null) e.label = label(a.text());
                touch(e, k);
            }
            List<BigDecimal> tendered = values(p, ReceiptLabels.Kind.TENDERED);
            List<BigDecimal> change = values(p, ReceiptLabels.Kind.CHANGE);
            for (BigDecimal t : tendered) {
                if (change.isEmpty()) {
                    Evidence e = evidence.get(key(t));
                    if (e != null) e.payment = true;
                }
                for (BigDecimal ch : change) {
                    BigDecimal paid = t.subtract(ch.abs());
                    if (paid.signum() <= 0) continue;
                    Evidence e = evidence.get(key(paid));
                    if (e == null) {
                        e = new Evidence(scale(paid));
                        e.derived = true;
                        e.label = "Cash minus change";
                        evidence.put(key(paid), e);
                    } else if (!e.derived) {
                        e.payment = true;
                    }
                    touch(e, k);
                }
            }

            itemsReconciledByPass[k] = c.itemsSum() != null && (c.subtotals().stream().anyMatch(s -> close(s, c.itemsSum(), 0))
                || totals.stream().anyMatch(t -> close(t.value(), c.itemsSum(), 0)));
            itemsReconciledAny |= itemsReconciledByPass[k];
        }

        // a value read in one pass may be confirmed by the subtotal and taxes another pass read
        for (Evidence e : evidence.values()) {
            for (int k = 0; k < passes.size() && !e.arithmetic; k++) {
                String basis = arithmetic(e.value, components.get(k), totalsByPass.get(k));
                if (basis != null) {
                    e.arithmetic = true;
                    e.arithmeticBasis = basis;
                    e.arithmeticPass = k;
                }
            }
        }

        if (evidence.isEmpty()) {
            return new TotalAssessment(null, FieldAssessment.missing(), false, List.of(), -1, itemsReconciledAny);
        }

        List<Evidence> ranked = new ArrayList<>(evidence.values());
        ranked.sort(Comparator.comparingDouble(Evidence::score).reversed()
            .thenComparing(e -> e.value, Comparator.reverseOrder()));
        Evidence best = ranked.get(0);
        // the bill after a printed tip is what was paid; the pre-tip total is not the amount
        for (Evidence other : ranked) {
            if (other.value.compareTo(best.value) > 0 && (other.labelled || other.payment)
                && differsByTip(best.value, other.value, passes)) {
                best = other;
            }
        }

        boolean conflict = false;
        for (Evidence other : ranked) {
            if (other == best || related(best.value, other.value, passes)) continue;
            boolean bothWeak = !best.strong() && !other.strong() && other.labelled && best.labelled;
            // the receipt itself says "grand total" is something else
            boolean grandDisagrees = other.grand && !best.grand;
            // cash minus change says otherwise and the chosen total has no corroboration
            boolean paidDisagrees = other.derived && !best.strong();
            if (other.strong() || bothWeak || grandDisagrees || paidDisagrees) {
                conflict = true;
                break;
            }
        }

        double confidence = confidence(best);
        int disagreeing = disagreeingPasses(best.value, passes);
        // another pass read a different number on its total line: the digits are uncertain
        if (disagreeing == 1) confidence = Math.min(confidence, 0.85);
        else if (disagreeing > 1) confidence = Math.min(confidence, 0.65);
        FieldAssessment assessment;
        if (conflict) assessment = FieldAssessment.ask(Math.min(confidence, 0.45), "conflict:" + best.basis());
        else if (best.derived) assessment = FieldAssessment.of(Math.min(confidence, ReceiptDecision.ACCEPT_AT - 0.01), best.basis());
        else assessment = FieldAssessment.of(confidence, best.basis());
        final Evidence chosen = best;
        List<ReceiptAmountOption> options = Stream.concat(Stream.of(chosen),
                ranked.stream().filter(e -> e != chosen))
            .limit(MAX_OPTIONS)
            .map(e -> new ReceiptAmountOption(e.value, e.label, e.basis())).toList();
        int pass = best.arithmeticPass >= 0 ? best.arithmeticPass : best.firstPass;
        boolean itemsReconciled = pass >= 0 && pass < itemsReconciledByPass.length && itemsReconciledByPass[pass];
        return new TotalAssessment(best.value, assessment, conflict, options, pass, itemsReconciled);
    }

    // Probability the chosen total is right, per evidence pattern. Checked against the synthetic eval set's
    // reliability table (eval/run.sh); revisit once real confirmed receipts exist.
    static double confidence(Evidence e) {
        double c;
        if (e.derived) return e.arithmetic ? 0.80 : 0.30;
        if (e.arithmetic && e.labelled && e.payment) c = 0.98;
        else if (e.arithmetic && (e.labelled || e.payment)) c = 0.95;
        else if (e.labelled && e.payment) c = 0.90;
        else if (e.arithmetic) c = 0.75;
        else if (e.grand) c = 0.65;
        else if (e.labelled) c = 0.55;
        else c = 0.45;
        if (e.passes.size() > 1 && c < 0.98) c = Math.min(0.99, c + 0.02);
        return c;
    }

    private static int disagreeingPasses(BigDecimal value, List<ReceiptTextParser.Parsed> passes) {
        int n = 0;
        for (ReceiptTextParser.Parsed p : passes) {
            List<BigDecimal> totals = p.amounts().stream()
                .filter(a -> a.kind().isTotal() && a.value().signum() > 0)
                .map(ReceiptTextParser.AmountLine::value).toList();
            if (totals.isEmpty()) continue;
            boolean agrees = totals.stream().anyMatch(t -> close(t, value, 0) || related(t, value, List.of(p)));
            if (!agrees) n++;
        }
        return n;
    }

    private static void touch(Evidence e, int pass) {
        e.passes.add(pass);
        if (e.firstPass < 0) e.firstPass = pass;
    }

    private static Components components(ReceiptTextParser.Parsed p) {
        BigDecimal itemsSum = p.items().isEmpty() ? null
            : p.items().stream().map(ReceiptTextParser.Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> subtotals = values(p, ReceiptLabels.Kind.SUBTOTAL).stream().filter(v -> v.signum() > 0).toList();
        List<BigDecimal> taxLines = values(p, ReceiptLabels.Kind.TAX);
        if (taxLines.isEmpty()) taxLines = values(p, ReceiptLabels.Kind.TAX_TOTAL);
        List<BigDecimal> discounts = values(p, ReceiptLabels.Kind.DISCOUNT);
        List<BigDecimal> service = values(p, ReceiptLabels.Kind.SERVICE_CHARGE);
        List<BigDecimal> roundOff = values(p, ReceiptLabels.Kind.ROUND_OFF);
        int count = taxLines.size() + discounts.size() + service.size() + roundOff.size();
        return new Components(itemsSum, subtotals, sumAbs(taxLines), sumAbs(discounts), sumAbs(service),
            values(p, ReceiptLabels.Kind.TIP).stream().map(BigDecimal::abs).toList(),
            roundOff.stream().reduce(BigDecimal.ZERO, BigDecimal::add), !roundOff.isEmpty(), !taxLines.isEmpty(), count);
    }

    // which equation, if any, makes v consistent with this pass's other lines
    private static String arithmetic(BigDecimal v, Components c, List<ReceiptTextParser.AmountLine> totals) {
        List<BigDecimal> bases = new ArrayList<>(c.subtotals());
        if (c.itemsSum() != null) bases.add(c.itemsSum());
        for (int i = 0; i < bases.size(); i++) {
            BigDecimal base = bases.get(i);
            boolean fromItems = i == c.subtotals().size();
            if (!c.hasTax() && c.discount().signum() == 0 && c.service().signum() == 0) {
                if (close(base, v, c.count())) return fromItems ? "items" : "subtotal";
                if (!c.hasRoundOff() && wholeUnits(v) && base.subtract(v).abs().compareTo(UNPRINTED_ROUNDING) < 0) {
                    return "rounded";
                }
                continue;
            }
            BigDecimal expected = base.subtract(c.discount()).add(c.tax()).add(c.service()).add(c.roundOff());
            String name = fromItems ? "items+charges" : "subtotal+charges";
            if (close(expected, v, c.count())) return name;
            for (BigDecimal tip : c.tips()) {
                if (close(expected.add(tip), v, c.count())) return name + "+tip";
            }
            if (!c.hasRoundOff() && wholeUnits(v) && expected.subtract(v).abs().compareTo(UNPRINTED_ROUNDING) < 0) {
                return name + "+rounded";
            }
        }
        for (BigDecimal tip : c.tips()) {
            for (ReceiptTextParser.AmountLine t : totals) {
                if (!close(t.value(), v, 0) && close(t.value().add(tip), v, 0)) return "total+tip";
            }
        }
        return null;
    }

    private static boolean differsByTip(BigDecimal low, BigDecimal high, List<ReceiptTextParser.Parsed> passes) {
        BigDecimal diff = high.subtract(low);
        return passes.stream().flatMap(p -> p.ofKind(ReceiptLabels.Kind.TIP).stream())
            .anyMatch(t -> close(t.value().abs(), diff, 0));
    }

    // two totals differing by exactly a tip or the round-off are the same bill before/after it
    private static boolean related(BigDecimal a, BigDecimal b, List<ReceiptTextParser.Parsed> passes) {
        BigDecimal diff = a.subtract(b).abs();
        for (ReceiptTextParser.Parsed p : passes) {
            for (ReceiptTextParser.AmountLine line : p.amounts()) {
                if ((line.kind() == ReceiptLabels.Kind.TIP || line.kind() == ReceiptLabels.Kind.ROUND_OFF)
                    && close(line.value().abs(), diff, 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<BigDecimal> values(ReceiptTextParser.Parsed p, ReceiptLabels.Kind kind) {
        return p.ofKind(kind).stream().map(ReceiptTextParser.AmountLine::value).toList();
    }

    private static BigDecimal sumAbs(List<BigDecimal> values) {
        return values.stream().map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean close(BigDecimal a, BigDecimal b, int components) {
        return a.subtract(b).abs().compareTo(tolerance(components)) <= 0;
    }

    private static boolean wholeUnits(BigDecimal v) {
        return v.remainder(BigDecimal.ONE).signum() == 0;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String key(BigDecimal v) {
        return scale(v).toPlainString();
    }

    static String label(String line) {
        String normalized = MoneyParser.normalizeDigits(line);
        int cut = normalized.length();
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isDigit(normalized.charAt(i))) {
                cut = i;
                break;
            }
        }
        String label = normalized.substring(0, cut)
            .replaceAll("(?i)[\\s:@*#=(\\-]*(?:Rs\\.?|INR|SAR|AED|USD|SR|[$₹€£])?[\\s:.@*#=(\\-]*$", "")
            .trim();
        return label.isEmpty() ? null : label;
    }
}
