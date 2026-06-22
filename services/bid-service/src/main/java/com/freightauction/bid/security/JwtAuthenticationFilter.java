package com.freightauction.bid.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

    private final JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equals("GET") || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            JwtTokenVerifier.AuthenticatedUser user = tokenVerifier.verify(extractToken(request));
            if (!"TRANSPORTADORA".equals(user.role())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "TRANSPORTADORA role required");
                return;
            }

            request.setAttribute(USER_ID_ATTRIBUTE, user.userId());
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer token required");
        }
        return header.substring(7).trim();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
