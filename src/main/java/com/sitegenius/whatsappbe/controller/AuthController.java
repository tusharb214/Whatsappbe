package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.auth.LoginRequest;
import com.sitegenius.whatsappbe.dto.auth.LoginResponse;
import com.sitegenius.whatsappbe.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }
}