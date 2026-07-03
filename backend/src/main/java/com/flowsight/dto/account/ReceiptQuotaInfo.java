package com.flowsight.dto.account;

import lombok.Builder;
import lombok.Data;

// Per-user receipt processing quota; the only gated feature in the product.
@Data
@Builder
public class ReceiptQuotaInfo {
    private int     used;             // receipts_processed
    private int     limit;            // receipt_limit
    private Integer remaining;        // null when unlimited
    private boolean unlimited;
    // True when the user can still process more receipts.
    private boolean canProcess;
}
