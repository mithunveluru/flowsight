package com.flowsight.service;

import com.flowsight.dto.report.TaxDeductionEntry;
import com.flowsight.dto.report.TaxSection;
import com.flowsight.dto.report.TaxSummaryResponse;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionType;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects transactions eligible for Indian tax deductions under common sections.
 *
 * <p>Detection is keyword-based on merchant + description fields. This is a
 * "first-pass suggestion" — the user must confirm with their tax advisor.
 * It does not replace formal tax computation.
 *
 * <p>Sections covered:
 * <ul>
 *   <li><b>80C</b> — Tax-saving investments (limit ₹1.5L): LIC, PPF, ELSS, NPS, ULIP, NSC, EPF, school tuition</li>
 *   <li><b>80D</b> — Health insurance premium (limit ₹25k self / ₹50k senior): Star Health, HDFC Ergo, etc.</li>
 *   <li><b>80E</b> — Education loan interest (no limit): education loan EMI payments</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxDeductionDetector {

    private static final BigDecimal LIMIT_80C = new BigDecimal("150000");
    private static final BigDecimal LIMIT_80D = new BigDecimal("25000");

    private static final Pattern PATTERN_80C = Pattern.compile(
        "\\b(lic|life\\s+insurance|life\\s+ins|jeevan|mutual\\s+fund|" +
        "sip|elss|equity\\s+linked|ppf|public\\s+provident|nps|national\\s+pension|" +
        "ulip|nsc|epf|provident\\s+fund|tuition|school\\s+fee[s]?|" +
        "sukanya|hdfc\\s+(mf|amc|life)|sbi\\s+(mf|life)|icici\\s+(mf|prudential|life)|" +
        "axis\\s+(mf|max)|kotak\\s+(mf|life)|tata\\s+aia|tata\\s+(mf|aia)|" +
        "max\\s+life|aditya\\s+birla\\s+sun\\s+life|nippon\\s+(india|mf))\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_80D = Pattern.compile(
        "\\b(health\\s+insurance|health\\s+ins|mediclaim|" +
        "star\\s+health|hdfc\\s+ergo|bajaj\\s+allianz\\s+health|niva\\s+bupa|" +
        "max\\s+bupa|care\\s+health|aditya\\s+birla\\s+health|" +
        "religare|manipal\\s+cigna|" +
        "preventive\\s+health\\s+(check|checkup))\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_80E = Pattern.compile(
        "\\b(education\\s+loan|edu\\s+loan|student\\s+loan|" +
        "education\\s+loan\\s+(emi|interest)|" +
        "credila|propelld|hdfc\\s+credila|avanse)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final TransactionRepository transactionRepository;

    /**
     * Detects deductions for the Indian financial year containing {@code referenceDate}.
     * FY runs April 1 to March 31. If today is May 2026, FY = 2026 (April 2026 – March 2027).
     */
    public TaxSummaryResponse detectForFinancialYear(UUID userId, LocalDate referenceDate) {
        // Compute FY bounds
        int fyStart = referenceDate.getMonthValue() >= 4
            ? referenceDate.getYear()
            : referenceDate.getYear() - 1;
        LocalDate periodStart = LocalDate.of(fyStart, 4, 1);
        LocalDate periodEnd   = LocalDate.of(fyStart + 1, 3, 31);

        // Cap end at today (don't include future)
        LocalDate effectiveEnd = referenceDate.isBefore(periodEnd) ? referenceDate : periodEnd;

        List<Transaction> txns = transactionRepository.findForExport(
            userId, null, periodStart, effectiveEnd);

        // Only DEBIT transactions are deductible
        List<Transaction> debits = txns.stream()
            .filter(t -> t.getType() == TransactionType.DEBIT)
            .collect(Collectors.toList());

        TaxSection section80C = buildSection(debits, "80C", "Tax-saving investments",
            "Investments in LIC, PPF, ELSS, NPS, NSC, ULIP, tuition fees, etc. Limit ₹1.5L.",
            PATTERN_80C, LIMIT_80C);

        TaxSection section80D = buildSection(debits, "80D", "Health insurance premium",
            "Health insurance premiums and preventive checkups. Limit ₹25k (₹50k for senior citizens).",
            PATTERN_80D, LIMIT_80D);

        TaxSection section80E = buildSection(debits, "80E", "Education loan interest",
            "Interest paid on education loans. No upper limit on deduction.",
            PATTERN_80E, null);

        BigDecimal totalEligible = section80C.getTotalAmount()
            .add(section80D.getTotalAmount())
            .add(section80E.getTotalAmount());

        return TaxSummaryResponse.builder()
            .financialYear(fyStart)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .totalEligible(totalEligible)
            .sections(List.of(section80C, section80D, section80E))
            .build();
    }

    // -------------------------------------------------------------------------
    // Section builder
    // -------------------------------------------------------------------------

    private TaxSection buildSection(
        List<Transaction> debits, String code, String name, String description,
        Pattern pattern, BigDecimal limit
    ) {
        List<TaxDeductionEntry> entries = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Transaction tx : debits) {
            String merchant    = tx.getMerchant()    != null ? tx.getMerchant()    : "";
            String description2= tx.getDescription() != null ? tx.getDescription() : "";
            String combined    = merchant + " " + description2;

            var matcher = pattern.matcher(combined);
            if (!matcher.find()) continue;

            String detectedKeyword = matcher.group(0);
            entries.add(TaxDeductionEntry.builder()
                .merchant(merchant.isBlank() ? "Unknown" : merchant)
                .description(description2)
                .amount(tx.getAmount().setScale(2, RoundingMode.HALF_UP))
                .date(tx.getTransactionDate().toString())
                .detectedBy(detectedKeyword)
                .build());

            total = total.add(tx.getAmount());
        }

        BigDecimal remaining = limit != null
            ? limit.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            : null;

        return TaxSection.builder()
            .code(code)
            .name(name)
            .description(description)
            .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
            .limit(limit)
            .remainingLimit(remaining)
            .entries(entries)
            .build();
    }
}
