package com.freightauction.auth.controller;

import com.freightauction.auth.dto.AuthResponse;
import com.freightauction.auth.dto.LoginRequest;
import com.freightauction.auth.dto.RegisterRequest;
import com.freightauction.auth.dto.TokenValidationResponse;
import com.freightauction.auth.dto.UserResponse;
import com.freightauction.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/validate")
    public TokenValidationResponse validate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.validate(authorizationHeader);
    }
}
