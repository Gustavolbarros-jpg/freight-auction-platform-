package com.freightauction.auth.dto;

import com.freightauction.auth.domain.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
}
