package com.flowsight.analytics;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

// Strips reference IDs, UPI handles, and noise from bank descriptions.
@Service
public class NormalizationService {

    // noise patterns in Indian bank descriptions
    private static final Pattern UPI_REF = Pattern.compile(
        "[0-9]{9,20}|UPI/[A-Z0-9]+|REF[A-Z0-9]+|NEFT/[A-Z0-9]+|IMPS/[A-Z0-9]+",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SLASH_SEGMENT = Pattern.compile("/[A-Z0-9]{6,}/");
    private static final Pattern EXTRA_WHITESPACE = Pattern.compile("\\s{2,}");
    private static final Pattern TRAILING_NOISE = Pattern.compile("[/@\\-_]+$");

    // prefixes stripped when extracting merchant
    private static final Pattern MERCHANT_PREFIX = Pattern.compile(
        "^(UPI-|NEFT-|IMPS-|VPA-|POS-|ATM-|ACH-|SI-|ECS-|DD-|NACH-)",
        Pattern.CASE_INSENSITIVE
    );

    public String normalize(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        text = UPI_REF.matcher(text).replaceAll(" ");
        text = SLASH_SEGMENT.matcher(text).replaceAll(" ");
        text = EXTRA_WHITESPACE.matcher(text).replaceAll(" ");
        text = TRAILING_NOISE.matcher(text).replaceAll("");
        return text.trim();
    }

    public String extractMerchant(String description) {
        if (description == null || description.isBlank()) return null;

        String text = description.trim();

        text = MERCHANT_PREFIX.matcher(text).replaceFirst("");

        // first token; bank descriptions are "MERCHANT/CITY/DATE"
        String[] parts = text.split("[/|\\-]", 3);
        String candidate = parts[0].trim();

        // numeric candidates are reference IDs, not merchants
        if (candidate.matches("[0-9\\s]+")) {
            return parts.length > 1 ? parts[1].trim() : null;
        }

        return candidate.length() >= 3 ? candidate : null;
    }
}
