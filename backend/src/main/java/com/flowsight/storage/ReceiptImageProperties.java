package com.flowsight.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * store: which ReceiptImageStore to use (only "local" exists).
 * draftRetention: how long an unreviewed draft keeps its image.
 * reviewedRetention: how long the image stays after the user confirms (never past the draft limit).
 */
@ConfigurationProperties(prefix = "application.storage.receipt-images")
public record ReceiptImageProperties(
    @DefaultValue("local") String store,
    @DefaultValue("2000") int maxLongSide,
    @DefaultValue("0.8") float jpegQuality,
    @DefaultValue("P7D") Duration draftRetention,
    @DefaultValue("PT24H") Duration reviewedRetention
) {
}
