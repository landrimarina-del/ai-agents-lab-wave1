package com.rise.backend.user;

import com.rise.backend.audit.AuditService;
import com.rise.backend.auth.AppUser;
import com.rise.backend.auth.Role;
import com.rise.backend.businessunit.BusinessUnitRepository;
import com.rise.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            BusinessUnitRepository businessUnitRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.businessUnitRepository = businessUnitRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public UserResponse createUser(CreateUserRequest request, Integer actorUserId) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already exists");
        }

        if (request.role() == Role.COUNTRY_MANAGER && (request.countryScope() == null || request.countryScope().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "countryScope is required for COUNTRY_MANAGER");
        }

        List<String> countryScope = request.countryScope() == null ? List.of() : request.countryScope();
        String passwordHash = passwordEncoder.encode(request.password());

        Integer userId = userRepository.createUser(
                request.email(),
                request.fullName(),
                passwordHash,
                request.role(),
                countryScope
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("userId", userId);
        payload.put("email", request.email());
        payload.put("role", request.role());
        payload.put("countryScope", countryScope);
        auditService.writeEvent("USER_CREATED", payload);

        return new UserResponse(userId, request.email(), request.fullName(), request.role(), countryScope, true);
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserResponse(
                        user.id(),
                        user.email(),
                        user.fullName(),
                        user.role(),
                        user.countryScope(),
                        user.active()
                ))
                .toList();
    }

    public void deactivateUser(Integer targetUserId, Integer actorUserId) {
        AppUser targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        userRepository.deactivateUser(targetUserId);

        Map<String, Object> payload = Map.of(
                "actorUserId", actorUserId,
                "userId", targetUserId,
            "email", targetUser.email(),
            "role", targetUser.role(),
            "previousActive", targetUser.active(),
            "newActive", false,
            "action", "DEACTIVATE"
        );
        auditService.writeEvent("USER_DEACTIVATED", payload);
    }

    public void reactivateUser(Integer targetUserId, Integer actorUserId) {
        AppUser targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        userRepository.reactivateUser(targetUserId);

        Map<String, Object> payload = Map.of(
                "actorUserId", actorUserId,
                "userId", targetUserId,
                "email", targetUser.email(),
                "role", targetUser.role(),
                "previousActive", targetUser.active(),
                "newActive", true,
                "action", "REACTIVATE"
        );
        auditService.writeEvent("USER_REACTIVATED", payload);
    }

    public void updateUserBusinessUnits(Integer targetUserId, UpdateUserBusinessUnitsRequest request, Integer actorUserId) {
        if (request == null || request.businessUnitIds() == null || request.businessUnitIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "businessUnitIds must be a non-empty list");
        }

        AppUser targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (targetUser.role() != Role.COUNTRY_MANAGER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target user must be COUNTRY_MANAGER");
        }

        List<Integer> normalizedBusinessUnitIds = new ArrayList<>(new LinkedHashSet<>(request.businessUnitIds()));
        int existingCount = businessUnitRepository.countExistingIds(normalizedBusinessUnitIds);
        if (existingCount != normalizedBusinessUnitIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more business units were not found");
        }

        businessUnitRepository.replaceUserBusinessUnits(targetUserId, normalizedBusinessUnitIds);

        Map<String, Object> payload = new HashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("userId", targetUserId);
        payload.put("email", targetUser.email());
        payload.put("role", targetUser.role());
        payload.put("businessUnitIds", normalizedBusinessUnitIds);
        auditService.writeEvent("USER_BUSINESS_UNITS_UPDATED", payload);
    }
}
