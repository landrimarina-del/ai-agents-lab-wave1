package com.rise.backend.countryscope;

public record CountryScopeResponse(
        Integer id,
        String code,
        String name,
        boolean active
) {
}
