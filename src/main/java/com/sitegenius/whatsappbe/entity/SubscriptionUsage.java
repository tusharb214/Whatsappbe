package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One benefit given to a customer from their plan (one product in one order). */
@Entity
@Table(name = "subscription_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Long subscriptionId;

    @Column(nullable = false, length = 30)
    private String customerPhone;

    @Column(nullable = false)
    private Long orderId;

    @Column(length = 60)
    private String orderNumber;

    @Column(nullable = false)
    private Long productId;

    @Column(length = 255)
    private String productName;

    private Long categoryId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, updatable = false)
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        if (usedAt == null) {
            usedAt = LocalDateTime.now();
        }
    }
}
