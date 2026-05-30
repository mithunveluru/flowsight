package com.flowsight.ocr;

import com.flowsight.dto.receipt.ReceiptLineItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Post-processing validation layer that sits between receipt-ocr extraction and the
 * internal OcrExtractionResult.
 *
 * <p>Problem: receipt-ocr's LLM occasionally selects VAT, CGST, SGST, subtotal, or
 * a discount amount as {@code total_amount} instead of the final payable total.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Build an {@link AmountCandidate} from the raw {@code total_amount}.</li>
 *   <li>Scan {@code line_items} for entries whose name contains financial-summary
 *       keywords — these are additional candidates the LLM sometimes embeds there.</li>
 *   <li>Score each candidate with four independent signals:
 *       <ul>
 *         <li>Label classification (preferred/deprioritized keyword)</li>
 *         <li>Ratio against the sum of regular (non-summary) line items</li>
 *         <li>Magnitude check (total cannot be less than any single item)</li>
 *         <li>Consistency with the primary extracted total</li>
 *       </ul>
 *   </li>
 *   <li>Select the highest-scored candidate and derive an {@link AmountConfidence} tier.</li>
 * </ol>
 *
 * <p>The validator never throws; partial or missing data always produces a result with
 * an appropriate confidence level.
 */
@Component
@Slf4j
public class ReceiptAmountValidator {

    // Labels that should be preferred as the final payable total
    private static final Set<String> PREFERRED = Set.of(
        "grand total", "total due", "amount paid", "net total", "final total",
        "total payable", "amount payable", "net payable", "balance due",
        "amount due", "total amount", "net amount", "payable amount", "total"
    );

    // Labels that are definitively NOT the final total
    private static final Set<String> DEPRIORITIZED = Set.of(
        "vat", "tax", "cgst", "sgst", "igst", "service tax", "gst",
        "subtotal", "sub total", "sub-total", "subtot", "discount", "savings",
        "round off", "rounding", "coupon", "cashback", "cash back"
    );

    // Confidence thresholds
    private static final double HIGH_THRESHOLD   = 0.65;
    private static final double MEDIUM_THRESHOLD = 0.40;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates and potentially corrects the receipt-ocr {@code total_amount}.
     *
     * @param totalAmount amount from receipt-ocr (may be null)
     * @param lineItems   line items from receipt-ocr (may be null/empty)
     * @return best {@link AmountCandidate} — never null
     */
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

        List<AmountCandidate> candidates = new ArrayList<>();
        candidates.add(scorePrimary(totalAmount, regularSum, maxItemPrice));
        candidates.addAll(scoreLineItemCandidates(items, totalAmount, regularSum));

        AmountCandidate best = candidates.stream()
            .max(Comparator.comparingDouble(AmountCandidate::getScore))
            .orElseThrow(); // candidates always has ≥ 1 element

        log.debug("Amount validation: selected {} score={} conf={} reason='{}'",
            best.getAmount(), f2(best.getScore()), best.getConfidence(), best.getReason());
        return best;
    }

    // -------------------------------------------------------------------------
    // Candidate builders
    // -------------------------------------------------------------------------

    private AmountCandidate scorePrimary(
        BigDecimal total, BigDecimal regularSum, BigDecimal maxItemPrice
    ) {
        double score = 0.50;
        StringBuilder reason = new StringBuilder("primary total_amount");

        if (regularSum != null && regularSum.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = total.doubleValue() / regularSum.doubleValue();

            if (ratio < 0.30) {
                score -= 0.35;
                reason.append("; ratio=").append(f2(ratio)).append(" << item sum (likely tax/VAT)");
            } else if (ratio < 0.85) {
                score -= 0.10;
                reason.append("; ratio=").append(f2(ratio)).append(" < item sum (possible subtotal/discount)");
            } else if (ratio <= 1.05) {
                score += 0.15;
                reason.append("; ratio=").append(f2(ratio)).append(" ≈ item sum (subtotal)");
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

        // A valid total must be >= any single item price
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
        List<ReceiptLineItem> items, BigDecimal primaryTotal, BigDecimal regularSum
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

            // Should be >= primary total if it's a better grand total
            if (item.getItemPrice().compareTo(primaryTotal) >= 0) {
                score += 0.05;
            }

            // Cross-check against regular items sum
            if (regularSum != null && regularSum.compareTo(BigDecimal.ZERO) > 0) {
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

    // -------------------------------------------------------------------------
    // Line item analysis helpers
    // -------------------------------------------------------------------------

    /**
     * Sum of item prices for non-summary line items (product lines only).
     * Returns null when no qualifying items exist.
     */
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

    // -------------------------------------------------------------------------
    // Label classifiers
    // -------------------------------------------------------------------------

    boolean isSummaryLine(String label) {
        return containsPreferred(label) || containsDeprioritized(label);
    }

    boolean containsPreferred(String label) {
        return PREFERRED.stream().anyMatch(label::contains);
    }

    boolean containsDeprioritized(String label) {
        return DEPRIORITIZED.stream().anyMatch(label::contains);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

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
