package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Aliases match whole tokens only; substring hits were the audit's false positives.
class AliasMatchingTest {

    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();

    @ParameterizedTest
    @CsvSource({
        "Emirates NBD,           EMI",
        "Apollo Chemist,         EMI",
        "Coca-Cola Vending,      Ola",
        "RENTOMOJO,              Rent",
        "Parent Teacher Assoc,   Rent",
        "PAYPAL *OLAPLEX,        Ola",
        "SOLA SALON STUDIOS,     Ola",
        "UPI/DR/1/OLA CABS/HDFC/ola@icici/Payment, ICICI Bank",
        "SMARTEMI SOLUTIONS,     EMI",
        "COFFEE AT THE CORNER,   AT&T",
        "PG EATERY,              PG&E",
    })
    void substringsOfOtherWordsNeverFire(String raw, String wrongCanonical) {
        assertThat(normalizer.normalize(raw).getCanonicalName()).isNotEqualTo(wrongCanonical);
    }

    @ParameterizedTest
    @CsvSource({
        "Emirates NBD,                               Emirates NBD",
        "BILL PAYMENT EMIRATES NBD CC PAYMENT 7303356119, Emirates NBD",
        "E-COMMERCE EMIRATES AIRLINE 083028,         Emirates",
        "Coca-Cola Vending,                          Coca-Cola",
        "UPI/DR/957220082619/OLA CABS ANI TECH/YESB/ola@icici/Payment from Ph, Ola",
        "GOOGLE *YouTubePremium,                     YouTube Premium",
        "PAYPAL *MICROSOFT365,                       Microsoft 365",
        "APPLE MEDIA SERVICES ICLOUD,                Apple iCloud",
        "ECOM PUR 1234XXXXXX5678 TATA 1MG,           Tata 1mg",
        "Jio Mart Order,                             JioMart",
        "EMI ICICI HOME LOAN,                        EMI",
        "BBPS ADANI ELECTRICITY MUMBAI 6541636246,   Adani Electricity",
        "ANYTIME FITNESS DUBAI,                      Anytime Fitness",
    })
    void wholeTokenAliasesResolve(String raw, String canonical) {
        assertThat(normalizer.normalize(raw).getCanonicalName()).isEqualTo(canonical);
    }

    @Test
    void everyCanonicalNameResolvesToItself() {
        // stored transactions hold canonical names; recurring detection re-normalizes them
        for (AliasIndex.Entry e : MerchantNormalizationService.builtInAliases()) {
            MerchantNormalizationService.Normalized n = normalizer.normalize(e.canonicalName());
            assertThat(n.getCanonicalName()).as(e.canonicalName()).isEqualTo(e.canonicalName());
            assertThat(n.getKey()).as(e.canonicalName()).isEqualTo(e.canonicalKey());
        }
    }

    @Test
    void aliasesAreUniqueAndKeysLongEnoughForRecurringDetection() {
        Set<String> seen = new HashSet<>();
        for (AliasIndex.Entry e : MerchantNormalizationService.builtInAliases()) {
            assertThat(seen.add(e.alias())).as(e.alias()).isTrue();
            assertThat(e.alias()).isEqualTo(MerchantText.matchText(e.alias()));
            assertThat(e.canonicalKey().length()).as(e.canonicalName()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void cleanedDisplayNamesKeepTheirRawGroupingKey() {
        // CSV rows store the cleaned name; it must group with the raw descriptor it came from
        for (String raw : new String[] {
            "DEBIT CARD PURCHASE RAVI RESTAURANT SHARJAH", "SQ *JOE'S PIZZA CHICAGO IL", "BILL'S DINER",
            "POS 437484XXXXXX7771 FOOBAR BAKERY HYDERABAD", "TOTAL WINE & MORE #0042"}) {
            assertThat(normalizer.normalizeKey(MerchantText.clean(raw))).as(raw).isEqualTo(normalizer.normalizeKey(raw));
        }
    }
}
