package com.rise.backend.countryscope;

import com.rise.backend.audit.AuditService;
import com.rise.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CountryScopeService {

    private final CountryScopeRepository countryScopeRepository;
    private final AuditService auditService;

    public CountryScopeService(CountryScopeRepository countryScopeRepository, AuditService auditService) {
        this.countryScopeRepository = countryScopeRepository;
        this.auditService = auditService;
    }

    public List<CountryScopeResponse> findAll(boolean includeInactive) {
        return countryScopeRepository.findAll(includeInactive);
    }

    public CountryScopeResponse create(CreateCountryScopeRequest request, Integer actorUserId) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (countryScopeRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Country code already exists");
        }

        Integer id = countryScopeRepository.create(code, request.name().trim());
        CountryScopeResponse created = countryScopeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Country scope creation failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("countryScopeId", created.id());
        payload.put("code", created.code());
        auditService.writeEvent("COUNTRY_SCOPE_CREATED", payload);

        return created;
    }

    public CountryScopeResponse update(Integer id, UpdateCountryScopeRequest request, Integer actorUserId) {
        CountryScopeResponse current = countryScopeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Country scope not found"));

        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (countryScopeRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Country code already exists");
        }

        boolean active = request.isActive() == null || request.isActive();
        countryScopeRepository.update(id, code, request.name().trim(), active);

        CountryScopeResponse updated = countryScopeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Country scope update failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("countryScopeId", updated.id());
        payload.put("before", current);
        payload.put("after", updated);
        auditService.writeEvent("COUNTRY_SCOPE_UPDATED", payload);

        return updated;
    }

    public void logicalDelete(Integer id, Integer actorUserId) {
        CountryScopeResponse current = countryScopeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Country scope not found"));

        countryScopeRepository.logicalDelete(id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("countryScopeId", current.id());
        payload.put("code", current.code());
        payload.put("action", "LOGICAL_DELETE");
        auditService.writeEvent("COUNTRY_SCOPE_DELETED", payload);
    }
}
