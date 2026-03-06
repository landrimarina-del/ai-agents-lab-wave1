package com.rise.backend.auth;

import java.time.OffsetDateTime;
import java.util.List;

public record AppUser(
        Integer id,
        String email,
        String fullName,
        String passwordHash,
        Role role,
        List<String> countryScope,
        boolean active,
        int failedAttempts,
        OffsetDateTime lockedUntil
) {
}
