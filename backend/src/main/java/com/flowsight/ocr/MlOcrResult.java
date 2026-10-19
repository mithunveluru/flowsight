package com.flowsight.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// "result" of the ml-service OCR capability. Amounts are read leniently; every field is optional.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlOcrResult(
    String merchant,
    @JsonProperty("merchant_raw") String merchantRaw,
    @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal amount,
    String date,
    String currency,
    @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal subtotal,
    List<Charge> taxes,
    List<Charge> discounts,
    @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal tip,
    @JsonProperty("round_off") @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal roundOff,
    @JsonProperty("line_items") List<Item> lineItems,
    @JsonProperty("amount_candidates") List<Option> amountCandidates,
    Map<String, Field> fields,
    String decision,
    String text
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Charge(String label, @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String name, String quantity, @JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(@JsonDeserialize(using = LenientAmountDeserializer.class) BigDecimal amount, String label, String basis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field(Double confidence, String decision, String basis) {
    }
}
