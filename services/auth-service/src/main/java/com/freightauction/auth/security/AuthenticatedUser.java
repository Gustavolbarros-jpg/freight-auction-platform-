package com.freightauction.auth.security;

import com.freightauction.auth.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UserRole role, Instant expiresAt) {
}
