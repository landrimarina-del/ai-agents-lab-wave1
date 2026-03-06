package com.rise.backend.user;

import com.rise.backend.auth.Role;

import java.util.List;

public record UserResponse(
        Integer id,
        String email,
        String fullName,
        Role role,
        List<String> countryScope,
        boolean active
) {
}
