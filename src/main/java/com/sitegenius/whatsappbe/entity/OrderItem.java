package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    private String sku;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    private String currency;

    private String metaProductId;

    private String metaRetailerId;

    @PrePersist
    @PreUpdate
    protected void calculateTotal() {
        if (unitPrice != null && quantity != null) {
            totalPrice = unitPrice.multiply(
                    BigDecimal.valueOf(quantity)
            );
        }

        if (totalPrice == null) {
            totalPrice = BigDecimal.ZERO;
        }

        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }
    }
}