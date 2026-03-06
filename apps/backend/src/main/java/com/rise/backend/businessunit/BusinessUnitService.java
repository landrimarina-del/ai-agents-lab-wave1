package com.rise.backend.businessunit;

import com.rise.backend.audit.AuditService;
import com.rise.backend.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BusinessUnitService {

    private final BusinessUnitRepository businessUnitRepository;
    private final AuditService auditService;

    public BusinessUnitService(BusinessUnitRepository businessUnitRepository, AuditService auditService) {
        this.businessUnitRepository = businessUnitRepository;
        this.auditService = auditService;
    }

    public BusinessUnit create(CreateBusinessUnitRequest request, Integer actorUserId) {
        String code = normalizeCode(request.code());
        String countryCode = normalizeCountryCode(request.countryCode());
        boolean active = request.isActive() == null || request.isActive();

        if (businessUnitRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Business unit code already exists");
        }

        Integer id = businessUnitRepository.create(code, request.name().trim(), countryCode, request.region().trim(), active);
        BusinessUnit created = businessUnitRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Business unit creation failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("businessUnitId", created.id());
        payload.put("code", created.code());
        payload.put("name", created.name());
        payload.put("countryCode", created.countryCode());
        payload.put("region", created.region());
        payload.put("isActive", created.active());
        auditService.writeEvent("BUSINESS_UNIT_CREATED", payload);

        return created;
    }

    public List<BusinessUnit> findAll() {
        return businessUnitRepository.findAll();
    }

    public BusinessUnit update(Integer id, UpdateBusinessUnitRequest request, Integer actorUserId) {
        BusinessUnit current = businessUnitRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business unit not found"));

        String code = normalizeCode(request.code());
        String countryCode = normalizeCountryCode(request.countryCode());
        boolean active = request.isActive() == null || request.isActive();

        if (businessUnitRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Business unit code already exists");
        }

        businessUnitRepository.update(id, code, request.name().trim(), countryCode, request.region().trim(), active);
        BusinessUnit updated = businessUnitRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Business unit update failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("businessUnitId", updated.id());
        payload.put("before", current);
        payload.put("after", updated);
        auditService.writeEvent("BUSINESS_UNIT_UPDATED", payload);

        return updated;
    }

    public void delete(Integer id, Integer actorUserId) {
        BusinessUnit current = businessUnitRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business unit not found"));

        try {
            businessUnitRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete business unit because it is associated with users");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("businessUnitId", current.id());
        payload.put("code", current.code());
        payload.put("name", current.name());
        auditService.writeEvent("BUSINESS_UNIT_DELETED", payload);
    }

    private String normalizeCode(String rawCode) {
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCountryCode(String rawCountryCode) {
        return rawCountryCode.trim().toUpperCase(Locale.ROOT);
    }
}
