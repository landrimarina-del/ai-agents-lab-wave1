package com.rise.backend.businessunit;

public record BusinessUnit(
        Integer id,
        String code,
        String name,
        String countryCode,
        String region,
        boolean active
) {
}
