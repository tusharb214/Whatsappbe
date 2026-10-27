package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay posts payment events here. The organization id in the URL selects
 * which business's webhook secret is used to verify the signature.
 */
@RestController
@RequestMapping("/api/webhook/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final OrderPaymentService paymentService;

    @PostMapping("/{organizationId}")
    public ResponseEntity<String> receive(
            @PathVariable Long organizationId,
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) throws Exception {

        boolean accepted = paymentService.handleWebhook(organizationId, payload, signature);

        return accepted
                ? ResponseEntity.ok("OK")
                : ResponseEntity.status(403).body("Invalid signature");
    }
}
