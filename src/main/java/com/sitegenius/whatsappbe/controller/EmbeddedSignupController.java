package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.EmbeddedSignupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp/embedded-signup")
public class EmbeddedSignupController {

    private final EmbeddedSignupService embeddedSignupService;

    public EmbeddedSignupController(EmbeddedSignupService embeddedSignupService) {
        this.embeddedSignupService = embeddedSignupService;
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {

        if (error != null) {
            return ResponseEntity.badRequest().body(
                    "WhatsApp Embedded Signup failed: " +
                            (errorDescription != null ? errorDescription : error)
            );
        }

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Missing authorization code.");
        }

        // Exchange Meta authorization code for access token
        String accessToken =
                embeddedSignupService.exchangeCodeForAccessToken(code);

        // Do not expose or log the access token
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.internalServerError()
                    .body("Failed to obtain WhatsApp access token.");
        }

        return ResponseEntity.ok("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>WhatsApp Connected</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                </head>
                <body style="font-family: Arial; text-align: center; padding: 60px;">
                    <h2>WhatsApp Connected Successfully</h2>
                    <p>Your WhatsApp Business account authorization was received.</p>
                    <p>You can close this window.</p>
                </body>
                </html>
                """);
    }
}