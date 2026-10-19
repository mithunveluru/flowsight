package com.flowsight.ml.categorization;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionType;

import java.util.List;

/** Body of POST /v1/capabilities/categorizer/predict. */
public record CategorizerRequest(List<Item> items) {

    public static final int MAX_ITEMS = 100;

    /** direction is DEBIT, CREDIT or null when unknown. */
    public record Item(String description, String merchant, String direction) {

        private static final int DESCRIPTION_LENGTH = 500;
        private static final int MERCHANT_LENGTH    = 255;

        public static Item of(String description, String merchant, TransactionType type) {
            return new Item(truncate(description, DESCRIPTION_LENGTH), truncate(merchant, MERCHANT_LENGTH),
                type == null ? null : type.name());
        }

        public static Item of(Transaction tx) {
            return of(tx.getDescription(), tx.getMerchant(), tx.getType());
        }

        private static String truncate(String s, int max) {
            if (s == null) return null;
            return s.length() <= max ? s : s.substring(0, max);
        }
    }
}
