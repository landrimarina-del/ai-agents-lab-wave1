package com.rise.backend.countryscope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCountryScopeRequest(
        @NotBlank @Size(min = 2, max = 2) String code,
        @NotBlank @Size(max = 100) String name,
        Boolean isActive
) {
}
