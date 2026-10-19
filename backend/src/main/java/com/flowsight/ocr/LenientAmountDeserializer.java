package com.flowsight.ocr;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Reads an LLM-produced amount that may be a JSON number or a string such as "1,234.50",
 * "1.234,50", "SAR 45.00" or "١٢٣٫٥٠". Anything unreadable becomes null instead of failing the
 * whole response, so the merchant and date still come through.
 */
public class LenientAmountDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) return p.getDecimalValue();
        if (t == JsonToken.VALUE_STRING) return MoneyParser.parse(p.getText()).orElse(null);
        p.skipChildren();
        return null;
    }
}
