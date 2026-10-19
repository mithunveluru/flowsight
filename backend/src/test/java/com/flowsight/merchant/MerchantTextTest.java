package com.flowsight.merchant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // UPI and bill-pay rails: the payee segment, not references, bank codes or handles
        "UPI/DR/601815908301/SWIGGY/UTIB/swiggy@paytm/Payment from Ph | Swiggy",
        "UPI-ZOMATO-zomato@axl-UTIB0000601-895559797114-UPI           | Zomato",
        "NEFT-HDFC0000123-AIRTEL                                       | Airtel",
        "BIL/BPAY/922359852/TATA POWER DDL                             | Tata Power DDL",
        // card and POS prefixes, masked card numbers, city suffixes
        "POS 532122XXXXXX3307 DOMINOS PIZZA HYDERABAD                  | Dominos Pizza",
        "ECOM PUR 500376XXXXXX1436 FLIPKART INTERNET                   | Flipkart Internet",
        "DEBIT CARD PURCHASE RAVI RESTAURANT SHARJAH                   | Ravi Restaurant",
        "APPLE PAY TAMIMI MARKETS RIYADH                               | Tamimi Markets",
        "MADA PURCHASE LULU HYPERMARKET DAMMAM                         | Lulu Hypermarket",
        "POS PURCHASE RIYADH METRO AL KHOBAR SA                        | Riyadh Metro",
        "CHECKCARD 0618 MCDONALD'S SAN FRANCISCO CA                    | Mcdonald's",
        "SQ *BLUE BOTTLE COFFEE AUSTIN TX                              | Blue Bottle Coffee",
        // store numbers, phone numbers, PPD ids, trailing references
        "STARBUCKS COFFEE #12345                                       | Starbucks Coffee",
        "TOTAL WINE & MORE #0042                                       | Total Wine & More",
        "ETSY 097-350-7752 NY                                          | Etsy",
        "T-MOBILE PAYMENT PPD ID: 8759017142                           | T-Mobile",
        "SPOTIFY P1A2B3C4                                              | Spotify",
        "SHELL OIL 57444212                                            | Shell Oil",
        "ACH D- TP ACH LIC OF INDIA PREMIUM-695680582                  | Lic of India Premium",
        "AMZN Mktp US*95A2M62                                          | AMZN Mktp",
        "SWIGGY*ORDER 123456                                           | Swiggy",
        "Netflix.com                                                   | Netflix",
        "HALDIRAM'S SNACKS PVT LTD                                     | Haldiram's Snacks",
        "BILL'S DINER                                                  | Bill's Diner",
        "Carrefour City Marina                                         | Carrefour City Marina",
        "DUBAI TAXI DUBAI AE                                           | Dubai Taxi",
    })
    void cleansDescriptorsToTheMerchantName(String raw, String expected) {
        assertThat(MerchantText.clean(raw)).isEqualTo(expected);
    }

    @Test
    void cleaningIsIdempotent() {
        for (String raw : new String[] {
            "POS 532122XXXXXX3307 DOMINOS PIZZA HYDERABAD", "MARRIOTT RIYADH RIYADH SA", "BANK OF NEW YORK",
            "UPI/DR/1/SWIGGY/UTIB/x@y/Payment from Ph", "Rent Payment", "PAYPAL *SPOTIFYUSA"}) {
            String once = MerchantText.clean(raw);
            assertThat(MerchantText.clean(once)).as(raw).isEqualTo(once);
        }
    }

    @Test
    void keepsAtLeastOneWordAndGuardsPlaceNamesInsideNames() {
        assertThat(MerchantText.clean("MUMBAI")).isEqualTo("Mumbai");
        assertThat(MerchantText.clean("APPLE PAY")).isEqualTo("Apple Pay");
        assertThat(MerchantText.clean("BANK OF NEW YORK")).isEqualTo("Bank of New York");
    }

    @Test
    void returnsEmptyForNoiseOnly() {
        assertThat(MerchantText.clean(null)).isEmpty();
        assertThat(MerchantText.clean("   ")).isEmpty();
        assertThat(MerchantText.clean("123456789")).isEmpty();
        assertThat(MerchantText.clean("///---")).isEmpty();
    }

    @Test
    void matchTextFoldsCasePunctuationAndDomains() {
        assertThat(MerchantText.matchText("Nature's Basket")).isEqualTo("natures basket");
        assertThat(MerchantText.matchText("www.Booking.com")).isEqualTo("booking");
        assertThat(MerchantText.matchText("Coca-Cola Vending")).isEqualTo("coca cola vending");
    }
}
