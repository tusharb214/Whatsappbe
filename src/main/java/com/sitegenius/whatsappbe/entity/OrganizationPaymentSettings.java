package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "organization_payment_settings")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationPaymentSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "razorpay_enabled", nullable = false)
    private boolean razorpayEnabled;

    @Column(name = "razorpay_key_id", length = 100)
    private String razorpayKeyId;

    @Column(name = "razorpay_key_secret_enc", columnDefinition = "TEXT")
    private String razorpayKeySecretEnc;

    @Column(name = "razorpay_webhook_secret_enc", columnDefinition = "TEXT")
    private String razorpayWebhookSecretEnc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
