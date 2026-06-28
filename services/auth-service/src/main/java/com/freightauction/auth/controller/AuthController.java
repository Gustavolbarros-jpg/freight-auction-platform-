package com.freightauction.auth.controller;

import com.freightauction.auth.dto.AuthResponse;
import com.freightauction.auth.dto.LoginRequest;
import com.freightauction.auth.dto.RegisterRequest;
import com.freightauction.auth.dto.TokenValidationResponse;
import com.freightauction.auth.dto.UserResponse;
import com.freightauction.auth.domain.UserRole;
import com.freightauction.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Auth Endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register User")
    @ApiResponse(responseCode = "201", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login User")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/validate")
    @Operation(summary = "Valid User")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public TokenValidationResponse validate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.validate(authorizationHeader);
    }

    @GetMapping("/users")
    @Operation(summary = "List Users")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public List<UserResponse> findUsers(@RequestParam(required = false) UserRole role) {
        return authService.findUsers(role);
    }
}
