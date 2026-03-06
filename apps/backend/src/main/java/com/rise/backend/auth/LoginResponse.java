package com.rise.backend.auth;

public record LoginResponse(
        String token,
        Role role,
        Integer userId
) {
}
