package com.freightauction.bid.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtTokenVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("\"sub\":\"([^\"]+)\"");
    private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\":\"([^\"]+)\"");
    private static final Pattern EXPIRATION_PATTERN = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secret;

    public JwtTokenVerifier(@Value("${auth.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public AuthenticatedUser verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token");
            }

            String unsignedToken = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(decode(sign(unsignedToken)), decode(parts[2]))) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            String payload = new String(decode(parts[1]), StandardCharsets.UTF_8);
            UUID userId = UUID.fromString(extract(SUBJECT_PATTERN, payload));
            String role = extract(ROLE_PATTERN, payload);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(extract(EXPIRATION_PATTERN, payload)));
            if (!expiresAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("Token expired");
            }

            return new AuthenticatedUser(userId, role);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token", exception);
        }
    }

    private String extract(Pattern pattern, String payload) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid token claims");
        }
        return matcher.group(1);
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public record AuthenticatedUser(UUID userId, String role) {
    }
}
