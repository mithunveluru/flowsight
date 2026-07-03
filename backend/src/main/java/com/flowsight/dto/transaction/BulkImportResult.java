package com.flowsight.dto.transaction;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class BulkImportResult {
    private int totalRows;
    private int imported;
    private int skipped;
    private List<String> errors;

    // Date range covered by successfully-imported transactions — helps the UI link to the right view.
    private LocalDate firstTransactionDate;
    private LocalDate lastTransactionDate;
    private BigDecimal totalAmountImported;
}
