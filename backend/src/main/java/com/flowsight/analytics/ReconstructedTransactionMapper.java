package com.flowsight.analytics;

import org.springframework.stereotype.Service;

// Adapts a reconstructed balance-delta transaction to the CsvRow shape.
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
