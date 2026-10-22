package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.WhatsAppWebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhookService;

    @Value("${META_APP_SECRET:}")
    private String appSecret;

    @Value("${META_VERIFY_TOKEN:sitegenius_verify_token}")
    private String expectedVerifyToken;

    public WhatsAppWebhookController(
            WhatsAppWebhookService webhookService) {

        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode)
                && expectedVerifyToken.equals(verifyToken)) {

            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity
                .status(403)
                .body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        if (appSecret != null && !appSecret.isBlank()
                && !signatureValid(payload, signature)) {
            return ResponseEntity.status(403).body("Invalid signature");
        }

        webhookService.processWebhook(payload);

        return ResponseEntity.ok(
                "EVENT_RECEIVED"
        );
    }

    private boolean signatureValid(String body, String header) {
        if (header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    appSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hash = mac.doFinal(
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String expected = "sha256=" + java.util.HexFormat.of().formatHex(hash);
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
