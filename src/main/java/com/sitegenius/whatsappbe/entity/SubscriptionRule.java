package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One benefit rule of a plan, e.g. "1 item per day from category X".
 * Business-neutral: it only says WHAT it applies to, HOW MANY, and for WHICH period.
 */
@Entity
@Table(name = "subscription_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppliesTo appliesTo;

    // category id or product id; null when appliesTo = ANY
    private Long targetId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Period period;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AppliesTo {
        ANY,
        CATEGORY,
        PRODUCT
    }

    public enum Period {
        DAY,
        WEEK,
        PLAN
    }
}
