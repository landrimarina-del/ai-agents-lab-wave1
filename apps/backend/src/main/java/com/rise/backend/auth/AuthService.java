package com.rise.backend.auth;

import com.rise.backend.common.ApiException;
import com.rise.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }

        if (user.lockedUntil() != null && user.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED, "Account is locked. Try again later");
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int newFailedAttempts = user.failedAttempts() + 1;
            OffsetDateTime lockedUntil = null;
            if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil = OffsetDateTime.now().plusMinutes(15);
            }
            userRepository.setFailedAttemptsAndLock(request.email(), newFailedAttempts, lockedUntil);

            if (lockedUntil != null) {
                throw new ApiException(HttpStatus.LOCKED, "Account is locked. Try again later");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        userRepository.resetLockoutAndAttempts(user.id());
        UserPrincipal principal = new UserPrincipal(user.id(), user.email(), user.role());
        String token = jwtService.generateToken(principal);

        return new LoginResponse(token, user.role(), user.id());
    }
}
