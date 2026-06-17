package com.flowsight.analytics;

import org.springframework.stereotype.Service;

/**
 * Bridges {@link BalanceDeltaCalculator.ReconstructedTransaction} into the
 * existing {@link CsvParserService.CsvRow} shape so the downstream ingestion
 * pipeline (normalization, categorization, persistence) treats reconstructed
 * rows identically to natively-parsed ones.
 *
 * <p>The reconstruction confidence is currently informational only - it is
 * surfaced via {@link BalanceDeltaCalculator.Result#getWarnings()} so the
 * UI/audit log can flag suspicious rows. The CsvRow contract is preserved.
 */
@Service
public class ReconstructedTransactionMapper {

    public CsvParserService.CsvRow toCsvRow(BalanceDeltaCalculator.ReconstructedTransaction tx) {
        return CsvParserService.CsvRow.builder()
            .date(tx.getDate())
            .description(tx.getDescription())
            .amount(tx.getAmount())
            .isDebit(tx.isDebit())
            .rawText(tx.getRawText())
            .build();
    }
}
