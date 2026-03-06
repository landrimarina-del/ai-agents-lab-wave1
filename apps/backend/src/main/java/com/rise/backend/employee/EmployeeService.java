package com.rise.backend.employee;

import com.rise.backend.audit.AuditService;
import com.rise.backend.auth.AppUser;
import com.rise.backend.auth.Role;
import com.rise.backend.common.ApiException;
import com.rise.backend.shop.ShopRepository;
import com.rise.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.employeeRepository = employeeRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public EmployeeResponse create(CreateEmployeeRequest request, Integer actorUserId) {
        String employeeId = request.employeeId().trim().toUpperCase(Locale.ROOT);

        if (employeeRepository.existsByEmployeeIdIgnoreCase(employeeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Employee ID already exists");
        }

        if (!shopRepository.existsById(request.shopId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shop not found");
        }

        ensureScopeAccess(actorUserId, request.shopId());

        Integer id = employeeRepository.create(
                employeeId,
                request.fullName().trim(),
                request.email().trim().toLowerCase(Locale.ROOT),
                request.shopId()
        );

        EmployeeResponse created = employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Employee creation failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("employeeDbId", created.id());
        payload.put("employeeId", created.employeeId());
        payload.put("shopId", created.shopId());
        auditService.writeEvent("EMPLOYEE_CREATED", payload);

        return created;
    }

    public List<EmployeeResponse> findAll() {
        return employeeRepository.findAll();
    }

    public EmployeeResponse update(Integer id, UpdateEmployeeRequest request, Integer actorUserId) {
        EmployeeResponse current = employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));

        if (!shopRepository.existsById(request.shopId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shop not found");
        }

        ensureScopeAccess(actorUserId, request.shopId());

        employeeRepository.update(
                id,
                request.fullName().trim(),
                request.email().trim().toLowerCase(Locale.ROOT),
                request.shopId()
        );

        EmployeeResponse updated = employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Employee update failed"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("employeeDbId", updated.id());
        payload.put("before", current);
        payload.put("after", updated);
        auditService.writeEvent("EMPLOYEE_UPDATED", payload);

        return updated;
    }

    public void deactivate(Integer id, Integer actorUserId) {
        EmployeeResponse current = employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));

        employeeRepository.deactivate(id);

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("employeeDbId", current.id());
        payload.put("employeeId", current.employeeId());
        payload.put("action", "DEACTIVATE");
        auditService.writeEvent("EMPLOYEE_DEACTIVATED", payload);
    }

    private void ensureScopeAccess(Integer actorUserId, Integer shopId) {
        AppUser actorUser = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Actor user not found"));

        if (actorUser.role() != Role.COUNTRY_MANAGER) {
            return;
        }

        Optional<String> countryCode = shopRepository.findCountryCodeByShopId(shopId);
        if (countryCode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shop country not found");
        }

        List<String> scope = actorUser.countryScope() == null ? List.of() : actorUser.countryScope();
        if (scope.stream().noneMatch(entry -> countryCode.get().equalsIgnoreCase(entry))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Shop outside your country scope");
        }
    }
}
