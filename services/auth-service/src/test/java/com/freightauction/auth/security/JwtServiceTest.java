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

    @Test
    void shouldRejectExpiredToken() {
        JwtService jwtService = new JwtService(SECRET, -1); 
        User user = new User("Carrier", "carrier@teste.com", "hash", UserRole.TRANSPORTADORA);
        String token = jwtService.generate(user).value();

        assertThrows(UnauthorizedException.class, () -> jwtService.validate(token));
}

    @Test
    void shouldRejectTokenWithInvalidFormat() {
        JwtService jwtService = new JwtService(SECRET, 60);

        assertThrows(UnauthorizedException.class, () -> jwtService.validate("token.invalido"));
}

    @Test
    void shouldRejectSecretShorterThan32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("curto", 60));
}

    @Test
    void shouldGenerateAndValidateAdminToken() {
        JwtService jwtService = new JwtService(SECRET, 60);
        User user = new User("Admin", "admin@teste.com", "hash", UserRole.ADMIN);

        JwtService.GeneratedToken token = jwtService.generate(user);
        AuthenticatedUser authenticated = jwtService.validate(token.value());

        assertEquals(UserRole.ADMIN, authenticated.role());
        assertEquals(user.getId(), authenticated.userId());
}
}
