package com.freightauction.auth.dto;

import com.freightauction.auth.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        String name,
        String email,
        UserRole role
) {
}
