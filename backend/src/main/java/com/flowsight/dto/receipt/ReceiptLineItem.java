package com.flowsight.dto.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceiptLineItem {

    @JsonProperty("item_name")
    private String itemName;

    @JsonProperty("item_quantity")
    private String itemQuantity;

    @JsonProperty("item_price")
    private BigDecimal itemPrice;
}
