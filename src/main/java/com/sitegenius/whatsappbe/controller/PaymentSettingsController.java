package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.OrganizationPaymentSettings;
import com.sitegenius.whatsappbe.repository.OrganizationPaymentSettingsRepository;
import com.sitegenius.whatsappbe.service.SecretCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Each business stores its own Razorpay credentials here.
 * Secrets are encrypted and are never sent back to the browser.
 */
@RestController
@RequestMapping("/api/payment-settings")
@RequiredArgsConstructor
public class PaymentSettingsController {

    public record SaveRequest(
            Boolean razorpayEnabled,
            String razorpayKeyId,
            String razorpayKeySecret,
            String razorpayWebhookSecret
    ) {
    }

    private final OrganizationPaymentSettingsRepository repository;
    private final SecretCrypto crypto;

    private final RestClient restClient = RestClient.builder().build();

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> get(Authentication authentication) {
        Long organizationId = organizationId(authentication);
        OrganizationPaymentSettings settings = repository
                .findByOrganizationId(organizationId)
                .orElse(null);
        return ResponseEntity.ok(view(organizationId, settings));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody SaveRequest request,
            Authentication authentication
    ) {
        Long organizationId = organizationId(authentication);

        if (!crypto.isReady()) {
            return error("The server encryption key is not configured. Ask your administrator.");
        }

        OrganizationPaymentSettings settings = repository
                .findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    OrganizationPaymentSettings created = new OrganizationPaymentSettings();
                    created.setOrganizationId(organizationId);
                    return created;
                });

        String keyId = request.razorpayKeyId() == null ? "" : request.razorpayKeyId().trim();
        if (!keyId.isEmpty()) {
            if (!keyId.matches("^rzp_(test|live)_[A-Za-z0-9]+$")) {
                return error("Key ID must look like rzp_test_xxxx or rzp_live_xxxx.");
            }
            settings.setRazorpayKeyId(keyId);
        }

        String secret = request.razorpayKeySecret() == null ? "" : request.razorpayKeySecret().trim();
        if (!secret.isEmpty()) {
            settings.setRazorpayKeySecretEnc(crypto.encrypt(secret));
        }

        String webhookSecret = request.razorpayWebhookSecret() == null
                ? "" : request.razorpayWebhookSecret().trim();
        if (!webhookSecret.isEmpty()) {
            if (webhookSecret.length() < 8) {
                return error("Webhook secret must be at least 8 characters.");
            }
            settings.setRazorpayWebhookSecretEnc(crypto.encrypt(webhookSecret));
        }

        boolean enable = Boolean.TRUE.equals(request.razorpayEnabled());
        if (enable && (isBlank(settings.getRazorpayKeyId())
                || isBlank(settings.getRazorpayKeySecretEnc())
                || isBlank(settings.getRazorpayWebhookSecretEnc()))) {
            return error("Enter Key ID, Key Secret and Webhook Secret before turning online payments on.");
        }
        settings.setRazorpayEnabled(enable);

        repository.save(settings);
        return ResponseEntity.ok(view(organizationId, settings));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> test(Authentication authentication) {
        Long organizationId = organizationId(authentication);
        OrganizationPaymentSettings settings = repository
                .findByOrganizationId(organizationId)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();

        if (settings == null || isBlank(settings.getRazorpayKeyId())
                || isBlank(settings.getRazorpayKeySecretEnc())) {
            result.put("ok", false);
            result.put("message", "Save the Key ID and Key Secret first.");
            return ResponseEntity.ok(result);
        }

        try {
            String basic = Base64.getEncoder().encodeToString(
                    (settings.getRazorpayKeyId() + ":" + crypto.decrypt(settings.getRazorpayKeySecretEnc()))
                            .getBytes(StandardCharsets.UTF_8));

            restClient.get()
                    .uri("https://api.razorpay.com/v1/payment_links?count=1")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(String.class);

            result.put("ok", true);
            result.put("message", "Connected to Razorpay ("
                    + (settings.getRazorpayKeyId().startsWith("rzp_test_") ? "test" : "live") + " mode).");
        } catch (HttpStatusCodeException e) {
            result.put("ok", false);
            result.put("message", e.getStatusCode().value() == 401
                    ? "Key ID or Key Secret is wrong."
                    : "Razorpay replied " + e.getStatusCode().value()
                    + ". Check that Payment Links are enabled on your Razorpay account.");
        } catch (Exception e) {
            result.put("ok", false);
            result.put("message", "Could not reach Razorpay. Try again.");
        }
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> view(Long organizationId, OrganizationPaymentSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("razorpayEnabled", settings != null && settings.isRazorpayEnabled());
        map.put("razorpayKeyId", settings == null ? "" : settings.getRazorpayKeyId());
        map.put("hasKeySecret", settings != null && !isBlank(settings.getRazorpayKeySecretEnc()));
        map.put("hasWebhookSecret", settings != null && !isBlank(settings.getRazorpayWebhookSecretEnc()));
        map.put("webhookUrl", publicBaseUrl + "/api/webhook/razorpay/" + organizationId);
        map.put("encryptionReady", crypto.isReady());
        return map;
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", message);
        return ResponseEntity.badRequest().body(map);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long organizationId(Authentication authentication) {
        try {
            Object details = authentication.getDetails();
            Object value = details.getClass().getMethod("getOrganizationId").invoke(details);
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            throw new RuntimeException("Unable to determine organization from authentication", e);
        }
    }
}
