package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.EmbeddedSignupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sitegenius.whatsappbe.dto.EmbeddedSignupRequest;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.service.WhatsAppNumberService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp/embedded-signup")
public class EmbeddedSignupController {

    private final EmbeddedSignupService embeddedSignupService;

    private final WhatsAppNumberService whatsappNumberService;

    public EmbeddedSignupController(
            EmbeddedSignupService embeddedSignupService,
            WhatsAppNumberService whatsappNumberService) {
        this.embeddedSignupService = embeddedSignupService;
        this.whatsappNumberService = whatsappNumberService;
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



    @PostMapping("/connect")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> connect(
            @RequestBody EmbeddedSignupRequest request) {

        if (request.code() == null || request.code().isBlank()
                || request.wabaId() == null || request.wabaId().isBlank()
                || request.phoneNumberId() == null
                || request.phoneNumberId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Missing signup details"));
        }

        try {
            String accessToken =
                    embeddedSignupService.exchangeCodeForAccessToken(
                            request.code());

            boolean verified =
                    embeddedSignupService.wabaContainsPhone(
                            request.wabaId(),
                            request.phoneNumberId(),
                            accessToken);

            if (!verified) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Phone number verification failed"));
            }

            Map<?, ?> details =
                    embeddedSignupService.getPhoneDetails(
                            request.phoneNumberId(),
                            accessToken);

            Object phoneValue = details.get("display_phone_number");

            if (phoneValue == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Meta did not return the phone number"));
            }

            String phoneNumber = phoneValue.toString();

            Object nameValue = details.get("verified_name");
            String displayName = nameValue == null
                    ? phoneNumber
                    : nameValue.toString();

            WhatsAppNumber saved =
                    whatsappNumberService.saveEmbeddedSignupNumber(
                            phoneNumber,
                            displayName,
                            request.phoneNumberId(),
                            request.wabaId(),
                            accessToken);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "phoneNumber", saved.getPhoneNumber(),
                    "displayName", saved.getDisplayName(),
                    "status", saved.getStatus()
            ));

        } catch (Exception exception) {
            // Never log or return access tokens or authorization codes.
            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Connection failed. Check Meta configuration and permissions."));
        }
    }

}