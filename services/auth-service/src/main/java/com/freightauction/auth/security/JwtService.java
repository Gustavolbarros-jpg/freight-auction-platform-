package com.freightauction.auth.security;

import com.freightauction.auth.domain.User;
import com.freightauction.auth.domain.UserRole;
import com.freightauction.auth.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("\"sub\":\"([^\"]+)\"");
    private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\":\"([^\"]+)\"");
    private static final Pattern EXPIRATION_PATTERN = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secret;
    private final long expirationMinutes;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expiration-minutes}") long expirationMinutes
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET must contain at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMinutes = expirationMinutes;
    }

    public GeneratedToken generate(User user) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES);
        String payload = """
                {"sub":"%s","role":"%s","iat":%d,"exp":%d}
                """.formatted(
                user.getId(),
                user.getRole(),
                issuedAt.getEpochSecond(),
                expiresAt.getEpochSecond()
        ).strip();

        String unsignedToken = encode(HEADER) + "." + encode(payload);
        return new GeneratedToken(unsignedToken + "." + sign(unsignedToken), expiresAt);
    }

    public AuthenticatedUser validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new UnauthorizedException("Invalid token");
            }

            String unsignedToken = parts[0] + "." + parts[1];
            byte[] expectedSignature = decode(sign(unsignedToken));
            byte[] receivedSignature = decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
                throw new UnauthorizedException("Invalid token signature");
            }

            String payload = new String(decode(parts[1]), StandardCharsets.UTF_8);
            UUID userId = UUID.fromString(extract(SUBJECT_PATTERN, payload));
            UserRole role = UserRole.valueOf(extract(ROLE_PATTERN, payload));
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(extract(EXPIRATION_PATTERN, payload)));

            if (!expiresAt.isAfter(Instant.now())) {
                throw new UnauthorizedException("Token expired");
            }

            return new AuthenticatedUser(userId, role, expiresAt);
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("Invalid token");
        }
    }

    private String extract(Pattern pattern, String payload) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw new UnauthorizedException("Invalid token claims");
        }
        return matcher.group(1);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign token", exception);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public record GeneratedToken(String value, Instant expiresAt) {
    }
}
