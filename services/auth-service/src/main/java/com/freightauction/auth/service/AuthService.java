package com.freightauction.auth.service;

import com.freightauction.auth.domain.User;
import com.freightauction.auth.domain.UserRole;
import com.freightauction.auth.dto.AuthResponse;
import com.freightauction.auth.dto.LoginRequest;
import com.freightauction.auth.dto.RegisterRequest;
import com.freightauction.auth.dto.TokenValidationResponse;
import com.freightauction.auth.dto.UserResponse;
import com.freightauction.auth.exception.ConflictException;
import com.freightauction.auth.exception.UnauthorizedException;
import com.freightauction.auth.repository.UserRepository;
import com.freightauction.auth.security.AuthenticatedUser;
import com.freightauction.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already registered");
        }

        User saved = userRepository.save(new User(
                request.name().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.role()
        ));

        return new UserResponse(
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getRole(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        JwtService.GeneratedToken token = jwtService.generate(user);
        return new AuthResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                user.getId(),
                user.getRole()
        );
    }

    public TokenValidationResponse validate(String authorizationHeader) {
        AuthenticatedUser user = jwtService.validate(extractBearerToken(authorizationHeader));
        return new TokenValidationResponse(true, user.userId(), user.role(), user.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findUsers(UserRole role) {
        List<User> users = role == null
                ? userRepository.findAll()
                : userRepository.findByRoleOrderByCreatedAtDesc(role);

        return users.stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole(),
                        user.getCreatedAt()
                ))
                .toList();
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Bearer token required");
        }
        return authorizationHeader.substring(7).trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
