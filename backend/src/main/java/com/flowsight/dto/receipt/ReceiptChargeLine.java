package com.flowsight.dto.receipt;

import java.math.BigDecimal;

// A tax, discount or other charge line read from the receipt.
public record ReceiptChargeLine(String label, BigDecimal amount) {
}
