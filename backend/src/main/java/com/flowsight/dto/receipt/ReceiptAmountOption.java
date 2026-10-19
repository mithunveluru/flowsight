package com.flowsight.dto.receipt;

import java.math.BigDecimal;

// A total read from the receipt, offered to the user when totals conflict or are uncertain.
public record ReceiptAmountOption(BigDecimal amount, String label, String basis) {
}
