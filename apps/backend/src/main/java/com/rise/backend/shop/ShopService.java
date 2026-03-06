package com.rise.backend.shop;

import com.rise.backend.audit.AuditService;
import com.rise.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final AuditService auditService;

    public ShopService(ShopRepository shopRepository, AuditService auditService) {
        this.shopRepository = shopRepository;
        this.auditService = auditService;
    }

    public ShopResponse create(CreateShopRequest request, Integer actorUserId) {
        String shopCode = request.shopCode().trim().toUpperCase(Locale.ROOT);
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);

        if (shopRepository.existsByShopCodeIgnoreCase(shopCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shop code already exists");
        }

        if (!shopRepository.businessUnitExists(request.businessUnitId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Business unit not found");
        }

        boolean active = request.isActive() == null || request.isActive();

        Integer id = shopRepository.create(
                shopCode,
                request.name().trim(),
                countryCode,
                request.region().trim(),
                request.businessUnitId(),
                active
        );

        ShopResponse created = shopRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Shop creation failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("shopId", created.id());
        payload.put("shopCode", created.shopCode());
        payload.put("countryCode", created.countryCode());
        payload.put("businessUnitId", created.businessUnitId());
        auditService.writeEvent("SHOP_CREATED", payload);

        return created;
    }

    public List<ShopResponse> findAll() {
        return shopRepository.findAll();
    }

    public ShopResponse update(Integer id, UpdateShopRequest request, Integer actorUserId) {
        ShopResponse current = shopRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shop not found"));

        String shopCode = request.shopCode().trim().toUpperCase(Locale.ROOT);
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);

        if (shopRepository.existsByShopCodeIgnoreCaseAndIdNot(shopCode, id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shop code already exists");
        }

        if (!shopRepository.businessUnitExists(request.businessUnitId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Business unit not found");
        }

        boolean active = request.isActive() == null || request.isActive();
        shopRepository.update(
                id,
                shopCode,
                request.name().trim(),
                countryCode,
                request.region().trim(),
                request.businessUnitId(),
                active
        );

        ShopResponse updated = shopRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Shop update failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("shopId", updated.id());
        payload.put("before", current);
        payload.put("after", updated);
        auditService.writeEvent("SHOP_UPDATED", payload);

        return updated;
    }
}
