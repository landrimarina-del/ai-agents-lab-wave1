package com.rise.backend.businessunit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBusinessUnitRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @NotBlank @Size(max = 100) String region,
        Boolean isActive
) {
}
