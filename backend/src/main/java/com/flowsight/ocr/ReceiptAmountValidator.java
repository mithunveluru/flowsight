package com.flowsight.ocr;

import com.flowsight.dto.receipt.ReceiptLineItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Post-processes receipt-ocr totals: the LLM sometimes picks VAT/subtotal/discount as the total.
// Scores the primary total plus summary line-items and picks the best. Never throws.
@Component
@Slf4j
public class ReceiptAmountValidator {

    // labels are classified by whole tokens (ReceiptLabels), so "Total Items" and "Total Tendered"
    // are not totals and "Subtotal" is not "Total"
    private static final Set<ReceiptLabels.Kind> DEPRIORITIZED = Set.of(
        ReceiptLabels.Kind.SUBTOTAL, ReceiptLabels.Kind.TAX, ReceiptLabels.Kind.TAX_TOTAL, ReceiptLabels.Kind.DISCOUNT,
        ReceiptLabels.Kind.SAVINGS, ReceiptLabels.Kind.ROUND_OFF, ReceiptLabels.Kind.ITEM_COUNT,
        ReceiptLabels.Kind.TENDERED, ReceiptLabels.Kind.CHANGE, ReceiptLabels.Kind.SERVICE_CHARGE, ReceiptLabels.Kind.TIP);
    private static final Set<ReceiptLabels.Kind> CHARGES = Set.of(
        ReceiptLabels.Kind.TAX, ReceiptLabels.Kind.TAX_TOTAL, ReceiptLabels.Kind.SERVICE_CHARGE);
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private static final double HIGH_THRESHOLD   = 0.65;
    private static final double MEDIUM_THRESHOLD = 0.40;

    // validate and potentially correct the total; never null
    public AmountCandidate validate(BigDecimal totalAmount, List<ReceiptLineItem> lineItems) {
        List<ReceiptLineItem> items = lineItems != null ? lineItems : List.of();

        if (totalAmount == null) {
            return AmountCandidate.builder()
                .amount(null)
                .label(null)
                .score(0.0)
                .confidence(AmountConfidence.LOW)
                .reason("receipt-ocr returned no total_amount")
                .build();
        }

        BigDecimal regularSum     = sumRegularItems(items);
        BigDecimal maxItemPrice   = maxRegularItemPrice(items);
        BigDecimal expected       = expectedTotal(items, regularSum);

        List<AmountCandidate> candidates = new ArrayList<>();
        candidates.add(scorePrimary(totalAmount, regularSum, maxItemPrice, expected, hasCharges(items)));
        candidates.addAll(scoreLineItemCandidates(items, totalAmount, regularSum, expected));

        AmountCandidate best = candidates.stream()
            .max(Comparator.comparingDouble(AmountCandidate::getScore))
            .orElseThrow(); // candidates always has ≥ 1 element

        log.debug("Amount validation: selected {} score={} conf={} reason='{}'",
            best.getAmount(), f2(best.getScore()), best.getConfidence(), best.getReason());
        return best;
    }

    private AmountCandidate scorePrimary(
        BigDecimal total, BigDecimal regularSum, BigDecimal maxItemPrice, BigDecimal expected, boolean hasCharges
    ) {
        double score = 0.50;
        StringBuilder reason = new StringBuilder("primary total_amount");

        if (expected != null && total.subtract(expected).abs().compareTo(CENT.multiply(BigDecimal.valueOf(2))) <= 0) {
            score += 0.25;
            reason.append("; equals items + charges - discounts");
        } else if (regularSum != null && regularSum.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = total.doubleValue() / regularSum.doubleValue();

            if (ratio < 0.30) {
                score -= 0.35;
                reason.append("; ratio=").append(f2(ratio)).append(" << item sum (likely tax/VAT)");
            } else if (ratio < 0.85) {
                score -= 0.10;
                reason.append("; ratio=").append(f2(ratio)).append(" < item sum (possible subtotal/discount)");
            } else if (ratio <= 1.05) {
                // close to the item sum but not equal to it: a subtotal when taxes exist, a misread otherwise
                score -= hasCharges ? 0.15 : 0.05;
                reason.append("; ratio=").append(f2(ratio)).append(" ≈ item sum but not equal (subtotal?)");
            } else if (ratio <= 1.35) {
                score += 0.25;
                reason.append("; ratio=").append(f2(ratio)).append(" ≈ subtotal+tax (good)");
            } else {
                score -= 0.05;
                reason.append("; ratio=").append(f2(ratio)).append(" >> item sum");
            }
        } else {
            reason.append("; no regular items to cross-check");
        }

        // total must be >= any single item price
        if (maxItemPrice != null && total.compareTo(maxItemPrice) < 0) {
            score -= 0.30;
            reason.append("; total < max item price (").append(maxItemPrice).append(") — suspect");
        }

        score = clamp(score);
        return AmountCandidate.builder()
            .amount(total)
            .label(null)
            .score(score)
            .confidence(toConfidence(score))
            .reason(reason.toString())
            .build();
    }

    private List<AmountCandidate> scoreLineItemCandidates(
        List<ReceiptLineItem> items, BigDecimal primaryTotal, BigDecimal regularSum, BigDecimal expected
    ) {
        List<AmountCandidate> result = new ArrayList<>();
        for (ReceiptLineItem item : items) {
            if (item.getItemName() == null || item.getItemPrice() == null) continue;
            if (item.getItemPrice().compareTo(BigDecimal.ZERO) <= 0) continue;

            String label = item.getItemName().toLowerCase().trim();
            if (!isSummaryLine(label)) continue; // only summary lines are total candidates

            double score = 0.40;
            StringBuilder reason = new StringBuilder("line item '").append(item.getItemName()).append("'");

            if (containsPreferred(label)) {
                score += 0.40;
                reason.append(" [preferred label]");
            }
            if (containsDeprioritized(label)) {
                score -= 0.40;
                reason.append(" [deprioritized label]");
            }

            // prefer values >= the primary total (a better grand total)
            if (item.getItemPrice().compareTo(primaryTotal) >= 0) {
                score += 0.05;
            }

            if (expected != null && containsPreferred(label)
                && item.getItemPrice().subtract(expected).abs().compareTo(CENT.multiply(BigDecimal.valueOf(2))) <= 0) {
                score += 0.20;
                reason.append(" [equals items + charges - discounts]");
            } else if (regularSum != null && regularSum.compareTo(BigDecimal.ZERO) > 0) {
                // cross-check against the item sum
                double ratio = item.getItemPrice().doubleValue() / regularSum.doubleValue();
                if (ratio >= 0.85 && ratio <= 1.35) {
                    score += 0.15;
                    reason.append(" [amount consistent with items]");
                }
            }

            score = clamp(score);
            result.add(AmountCandidate.builder()
                .amount(item.getItemPrice())
                .label(item.getItemName())
                .score(score)
                .confidence(toConfidence(score))
                .reason(reason.toString())
                .build());
        }
        return result;
    }

    // sum of non-summary item prices (product lines); null when none
    BigDecimal sumRegularItems(List<ReceiptLineItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (ReceiptLineItem item : items) {
            if (item.getItemPrice() == null || item.getItemName() == null) continue;
            if (isSummaryLine(item.getItemName().toLowerCase().trim())) continue;
            if (item.getItemPrice().compareTo(BigDecimal.ZERO) <= 0) continue;
            sum = sum.add(item.getItemPrice());
            count++;
        }
        return count > 0 ? sum : null;
    }

    private BigDecimal maxRegularItemPrice(List<ReceiptLineItem> items) {
        return items.stream()
            .filter(i -> i.getItemPrice() != null && i.getItemName() != null)
            .filter(i -> !isSummaryLine(i.getItemName().toLowerCase().trim()))
            .filter(i -> i.getItemPrice().compareTo(BigDecimal.ZERO) > 0)
            .map(ReceiptLineItem::getItemPrice)
            .max(BigDecimal::compareTo)
            .orElse(null);
    }

    boolean isSummaryLine(String label) {
        return containsPreferred(label) || containsDeprioritized(label);
    }

    boolean containsPreferred(String label) {
        return ReceiptLabels.classify(label).isTotal() || ReceiptLabels.tokens(label).containsAll(List.of("amount", "paid"));
    }

    boolean containsDeprioritized(String label) {
        return DEPRIORITIZED.contains(ReceiptLabels.classify(label));
    }

    private boolean hasCharges(List<ReceiptLineItem> items) {
        return items.stream().anyMatch(i -> i.getItemName() != null && CHARGES.contains(ReceiptLabels.classify(i.getItemName())));
    }

    // items + taxes/service - discounts, when the LLM returned the charge lines; null otherwise
    private BigDecimal expectedTotal(List<ReceiptLineItem> items, BigDecimal regularSum) {
        if (regularSum == null) return null;
        BigDecimal total = regularSum;
        boolean any = false;
        for (ReceiptLineItem i : items) {
            if (i.getItemName() == null || i.getItemPrice() == null) continue;
            ReceiptLabels.Kind kind = ReceiptLabels.classify(i.getItemName());
            if (CHARGES.contains(kind)) { total = total.add(i.getItemPrice().abs()); any = true; }
            else if (kind == ReceiptLabels.Kind.DISCOUNT) { total = total.subtract(i.getItemPrice().abs()); any = true; }
        }
        return any ? total : regularSum;
    }

    private AmountConfidence toConfidence(double score) {
        if (score >= HIGH_THRESHOLD)   return AmountConfidence.HIGH;
        if (score >= MEDIUM_THRESHOLD) return AmountConfidence.MEDIUM;
        return AmountConfidence.LOW;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String f2(double v) {
        return String.format("%.2f", v);
    }
}
