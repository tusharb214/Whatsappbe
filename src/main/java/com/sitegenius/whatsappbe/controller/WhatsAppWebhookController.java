package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.WhatsAppWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(
            WhatsAppWebhookService webhookService) {

        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        String expectedVerifyToken =
                "sitegenius_verify_token";

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
            @RequestBody String payload) {

        System.out.println(
                "WhatsApp Webhook Received:"
        );

        System.out.println(payload);

        webhookService.processWebhook(payload);

        return ResponseEntity.ok(
                "EVENT_RECEIVED"
        );
    }
}