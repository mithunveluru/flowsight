package com.flowsight.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

// Body of POST /v1/capabilities/ocr/predict on ml-service.
public record MlOcrRequest(
    @JsonProperty("image_base64") String imageBase64,
    @JsonProperty("mime_type") String mimeType
) {
}
