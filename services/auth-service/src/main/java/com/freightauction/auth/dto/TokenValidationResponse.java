package com.freightauction.auth.dto;

import com.freightauction.auth.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record TokenValidationResponse(
        boolean valid,
        UUID userId,
        UserRole role,
        Instant expiresAt
) {
}
