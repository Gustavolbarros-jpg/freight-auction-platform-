package com.freightauction.auth.security;

import com.freightauction.auth.domain.User;
import com.freightauction.auth.domain.UserRole;
import com.freightauction.auth.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters";

    @Test
    void shouldGenerateAndValidateToken() {
        JwtService jwtService = new JwtService(SECRET, 60);
        User user = new User(
                "Transportadora Teste",
                "carrier@teste.com",
                "hash",
                UserRole.TRANSPORTADORA
        );

        JwtService.GeneratedToken generatedToken = jwtService.generate(user);
        AuthenticatedUser authenticatedUser = jwtService.validate(generatedToken.value());

        assertEquals(user.getId(), authenticatedUser.userId());
        assertEquals(UserRole.TRANSPORTADORA, authenticatedUser.role());
        assertEquals(generatedToken.expiresAt(), authenticatedUser.expiresAt());
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtService jwtService = new JwtService(SECRET, 60);
        User user = new User("Admin", "admin@teste.com", "hash", UserRole.ADMIN);
        String token = jwtService.generate(user).value();
        char replacement = token.endsWith("A") ? 'B' : 'A';
        String tamperedToken = token.substring(0, token.length() - 1) + replacement;

        assertThrows(UnauthorizedException.class, () -> jwtService.validate(tamperedToken));
    }
}
