package com.flowsight.dto.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// Deserializes the receipt-ocr microservice JSON; all fields optional, validated null-safe by the mapper.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptOcrResponse {

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("merchant_address")
    private String merchantAddress;

    @JsonProperty("transaction_date")
    private String transactionDate;

    @JsonProperty("transaction_time")
    private String transactionTime;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("line_items")
    private List<ReceiptLineItem> lineItems;

    private Double confidence;
}
