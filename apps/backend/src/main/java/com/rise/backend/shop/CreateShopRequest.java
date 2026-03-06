package com.rise.backend.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShopRequest(
        @NotBlank @Size(max = 50) String shopCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @NotBlank @Size(max = 100) String region,
        @NotNull Integer businessUnitId,
        Boolean isActive
) {
}
