package com.flowsight.dto.account;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdatePreferencesRequest {

    @NotNull(message = "personalizationEnabled is required")
    private Boolean personalizationEnabled;
}
