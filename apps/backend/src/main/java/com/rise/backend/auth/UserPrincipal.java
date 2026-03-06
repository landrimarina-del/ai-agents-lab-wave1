package com.rise.backend.auth;

public record UserPrincipal(
        Integer userId,
        String email,
        Role role
) {
}
