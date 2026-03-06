package com.rise.backend.shop;

public record ShopResponse(
        Integer id,
        String shopCode,
        String name,
        String countryCode,
        String region,
        Integer businessUnitId,
        boolean active
) {
}
