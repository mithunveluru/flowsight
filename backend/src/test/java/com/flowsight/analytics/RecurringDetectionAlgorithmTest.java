package com.flowsight.analytics;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Regression tests for the merchant grouping + detection logic.
class RecurringDetectionAlgorithmTest {

    private MerchantNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new MerchantNormalizationService();
    }

    // The core failure case: merchant name variations group together

    @Test
    void netflixVariations_allShareSameNormalizationKey() {
        String key1 = normalizer.normalizeKey("NETFLIX");
        String key2 = normalizer.normalizeKey("Netflix.com");
        String key3 = normalizer.normalizeKey("Netflix Subscription");
        String key4 = normalizer.normalizeKey("NETFLIX MONTHLY 199");

        assertThat(key1).isEqualTo(key2);
        assertThat(key2).isEqualTo(key3);
        assertThat(key3).isEqualTo(key4);
        assertThat(key1).isEqualTo("netflix");
    }

    @Test
    void zomatoVariations_allShareSameNormalizationKey() {
        String a = normalizer.normalizeKey("ZOMATO");
        String b = normalizer.normalizeKey("ZOMATO 4827193847 UPI");
        String c = normalizer.normalizeKey("Zomato Pro Plus");

        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    void airtelVariations_allShareSameNormalizationKey() {
        String a = normalizer.normalizeKey("AIRTEL");
        String b = normalizer.normalizeKey("Airtel Postpaid Bill");
        String c = normalizer.normalizeKey("AIRTEL BROADBAND");

        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    void multipleRentVariations_shareKey() {
        String a = normalizer.normalizeKey("MONTHLY RENT");
        String b = normalizer.normalizeKey("RENT FOR APARTMENT");
        String c = normalizer.normalizeKey("Rent Payment");

        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    // Period classification tests

    @Test
    void weeklyInterval_classifiedAsWeekly() {
        // 7 days exactly
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(7))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.WEEKLY);
        // 8 days (slightly delayed)
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(8))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.WEEKLY);
    }

    @Test
    void biweeklyInterval_classifiedAsBiweekly() {
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(14))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.BIWEEKLY);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(15))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.BIWEEKLY);
    }

    @Test
    void monthlyInterval_classifiedAsMonthly() {
        // Various lengths between 25-40 days (months vary 28-31, can be delayed)
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(28))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.MONTHLY);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(30))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.MONTHLY);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(31))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.MONTHLY);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(35))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.MONTHLY);
    }

    @Test
    void quarterlyInterval_classifiedAsQuarterly() {
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(91))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.QUARTERLY);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(92))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.QUARTERLY);
    }

    @Test
    void annualInterval_classifiedAsAnnual() {
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(365))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.ANNUAL);
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(366))
            .isEqualTo(com.flowsight.entity.RecurringPeriod.ANNUAL);
    }

    @Test
    void unusualInterval_returnsNull() {
        // 50 days — between MONTHLY (max 40) and QUARTERLY (min 80)
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(50)).isNull();
        // 1 day — too short
        assertThat(com.flowsight.entity.RecurringPeriod.fromDays(1)).isNull();
    }

    // OCR-normalized merchant variations

    @Test
    void ocrMerchantWithExtraSpaces_normalizesCleanly() {
        assertThat(normalizer.normalizeKey("  NETFLIX   "))
            .isEqualTo(normalizer.normalizeKey("Netflix"));
    }

    @Test
    void ocrMerchantWithGarbageCharacters_stillResolves() {
        // OCR sometimes produces garbage like artifacts; normalization should be tolerant
        assertThat(normalizer.normalize("NETFLIX!!! @@@").getCanonicalName())
            .isEqualTo("Netflix");
    }

    @Test
    void ocrMerchantWithLineNoiseDigits_stillResolves() {
        assertThat(normalizer.normalize("NETFLIX 7283645293").getCanonicalName())
            .isEqualTo("Netflix");
    }

    // False positive prevention

    @Test
    void differentMerchants_doNotShareKey() {
        String netflix = normalizer.normalizeKey("Netflix");
        String swiggy  = normalizer.normalizeKey("Swiggy");
        String zomato  = normalizer.normalizeKey("Zomato");

        assertThat(netflix).isNotEqualTo(swiggy);
        assertThat(swiggy).isNotEqualTo(zomato);
        assertThat(netflix).isNotEqualTo(zomato);
    }

    @Test
    void similarSoundingMerchants_remainDistinct() {
        String spotify = normalizer.normalizeKey("Spotify");
        String shopify = normalizer.normalizeKey("Shopify");
        assertThat(spotify).isNotEqualTo(shopify);
    }
}
