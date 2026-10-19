package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.dto.merchant.ApplyPreviewResponse;
import com.flowsight.dto.merchant.ApplyResultResponse;
import com.flowsight.dto.merchant.MerchantRuleResponse;
import com.flowsight.dto.merchant.MerchantRuleSuggestion;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.categorization.CategorySource;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuditLogService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-user merchant rules: created from the user's own corrections, listed, deleted, and
 * applied to the user's other transactions with the same merchant grouping key.
 */
@Service
@RequiredArgsConstructor
public class MerchantRuleService {

    public static final String ACTION_RULE_PREVIEWED = "MERCHANT_RULE_PREVIEWED";
    public static final String ACTION_RULE_APPLIED   = "MERCHANT_RULE_APPLIED";
    public static final String ACTION_RULE_DELETED   = "MERCHANT_RULE_DELETED";

    private static final int      SAMPLE_SIZE      = 10;
    private static final int      MIN_KEY_LENGTH   = 3;
    private static final int      APPLY_CAPACITY   = 30;
    private static final Duration APPLY_WINDOW     = Duration.ofHours(1);

    private final UserMerchantRuleRepository   rules;
    private final MerchantNormalizationService normalizer;
    private final FeedbackService              feedback;
    private final PersonalizationService       personalization;
    private final RateLimiter                  rateLimiter;
    private final AuditLogService              auditLog;
    private final EntityManager                entityManager;

    /** The transaction's merchant fields as they were before the user's edit. */
    public record Before(String merchant, String merchantRaw, TransactionCategory category, BigDecimal confidence) {

        public static Before of(Transaction tx) {
            return new Before(tx.getMerchant(), tx.getMerchantRaw(), tx.getCategory(), tx.getConfidenceScore());
        }
    }

    /**
     * Records a merchant or category correction as feedback and, unless the user turned
     * personalization off, saves it as a rule. Returns null when nothing was corrected or
     * no rule was saved. Joins the caller's transaction.
     */
    @Transactional
    public MerchantRuleSuggestion recordCorrection(UUID userId, Transaction tx, Before before) {
        boolean merchantChanged = tx.getMerchant() != null && !tx.getMerchant().equals(before.merchant());
        boolean categoryChanged = tx.getCategory() != null && tx.getCategory() != before.category();
        if (!merchantChanged && !categoryChanged) return null;

        if (merchantChanged) {
            feedback.record(userId, new FeedbackCorrection(SubjectType.TRANSACTION, tx.getId(), "merchant",
                before.merchant(), tx.getMerchant(), FeedbackSource.TRANSACTION_EDIT, null, null));
        }
        if (categoryChanged) {
            feedback.record(userId, new FeedbackCorrection(SubjectType.TRANSACTION, tx.getId(), "category",
                before.category() == null ? null : before.category().name(), tx.getCategory().name(),
                FeedbackSource.TRANSACTION_EDIT, null,
                before.confidence() == null ? null : before.confidence().doubleValue()));
        }
        if (!personalization.isEnabled(userId)) return null;

        String key = normalizer.normalizeKey(before.merchantRaw() != null ? before.merchantRaw() : before.merchant());
        if (key.length() < MIN_KEY_LENGTH) return null;

        UserMerchantRule rule = rules.findByUserIdAndMatchKey(userId, key)
            .orElseGet(() -> UserMerchantRule.builder().userId(userId).matchKey(key).build());
        if (merchantChanged) rule.setCanonicalName(tx.getMerchant());
        if (categoryChanged) rule.setCategory(tx.getCategory());
        rule = rules.save(rule);

        return MerchantRuleSuggestion.builder()
            .ruleId(rule.getId())
            .canonicalName(rule.getCanonicalName())
            .category(rule.getCategory())
            .similarCount(matching(userId, rule).size())
            .build();
    }

    @Transactional(readOnly = true)
    public List<MerchantRuleResponse> list(UUID userId) {
        return rules.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(MerchantRuleService::toResponse).toList();
    }

    @Transactional
    public void delete(UUID ruleId, User user) {
        UserMerchantRule rule = owned(ruleId, user.getId());
        rules.delete(rule);
        auditLog.log(user, ACTION_RULE_DELETED, "MerchantRule", ruleId.toString());
    }

    @Transactional(readOnly = true)
    public ApplyPreviewResponse preview(UUID ruleId, User user) {
        rateLimiter.check("merchant-rules:" + user.getId(), APPLY_CAPACITY, APPLY_WINDOW);
        UserMerchantRule rule = owned(ruleId, user.getId());
        List<Transaction> matches = matching(user.getId(), rule);
        auditLog.log(user, ACTION_RULE_PREVIEWED, "MerchantRule", ruleId.toString(), Map.of("matched", matches.size()));
        return ApplyPreviewResponse.builder()
            .ruleId(ruleId)
            .matchCount(matches.size())
            .sample(matches.stream().limit(SAMPLE_SIZE).map(MerchantRuleService::toSample).toList())
            .build();
    }

    @Transactional
    public ApplyResultResponse apply(UUID ruleId, User user) {
        rateLimiter.check("merchant-rules:" + user.getId(), APPLY_CAPACITY, APPLY_WINDOW);
        UserMerchantRule rule = owned(ruleId, user.getId());
        List<Transaction> matches = matching(user.getId(), rule);
        for (Transaction tx : matches) {
            if (rule.getCanonicalName() != null) {
                if (tx.getMerchantRaw() == null) tx.setMerchantRaw(tx.getMerchant());
                tx.setMerchant(rule.getCanonicalName());
            }
            if (rule.getCategory() != null) {
                tx.setCategory(rule.getCategory());
                tx.setConfidenceScore(BigDecimal.ONE);
                tx.setCategorySource(CategorySource.USER);
                tx.setReviewed(true);
            }
        }
        rule.setAppliedCount(rule.getAppliedCount() + matches.size());
        rules.save(rule);
        auditLog.log(user, ACTION_RULE_APPLIED, "MerchantRule", ruleId.toString(), Map.of("updated", matches.size()));
        return ApplyResultResponse.builder().ruleId(ruleId).updated(matches.size()).build();
    }

    // The user's transactions with the rule's grouping key that the rule would still change.
    List<Transaction> matching(UUID userId, UserMerchantRule rule) {
        List<Transaction> candidates = entityManager.createQuery("""
                SELECT t FROM Transaction t
                WHERE t.user.id = :userId
                  AND (t.merchant IS NOT NULL OR t.merchantRaw IS NOT NULL)
                ORDER BY t.transactionDate DESC, t.createdAt DESC
                """, Transaction.class)
            .setParameter("userId", userId)
            .getResultList();
        Map<String, String> keys = new HashMap<>();
        List<Transaction> out = new ArrayList<>();
        for (Transaction tx : candidates) {
            String source = tx.getMerchantRaw() != null ? tx.getMerchantRaw() : tx.getMerchant();
            if (!rule.getMatchKey().equals(keys.computeIfAbsent(source, normalizer::normalizeKey))) continue;
            boolean merchantDiffers = rule.getCanonicalName() != null && !rule.getCanonicalName().equals(tx.getMerchant());
            boolean categoryDiffers = rule.getCategory() != null && rule.getCategory() != tx.getCategory();
            if (merchantDiffers || categoryDiffers) out.add(tx);
        }
        return out;
    }

    private UserMerchantRule owned(UUID ruleId, UUID userId) {
        return rules.findByIdAndUserId(Objects.requireNonNull(ruleId), userId)
            .orElseThrow(() -> new ResourceNotFoundException("MerchantRule", ruleId));
    }

    private static MerchantRuleResponse toResponse(UserMerchantRule r) {
        return MerchantRuleResponse.builder()
            .id(r.getId())
            .matchKey(r.getMatchKey())
            .canonicalName(r.getCanonicalName())
            .category(r.getCategory())
            .appliedCount(r.getAppliedCount())
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt())
            .build();
    }

    private static ApplyPreviewResponse.Sample toSample(Transaction tx) {
        return ApplyPreviewResponse.Sample.builder()
            .id(tx.getId())
            .transactionDate(tx.getTransactionDate())
            .merchant(tx.getMerchant())
            .category(tx.getCategory())
            .amount(tx.getAmount())
            .currency(tx.getCurrency())
            .build();
    }
}
