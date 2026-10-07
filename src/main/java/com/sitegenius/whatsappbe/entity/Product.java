package com.sitegenius.whatsappbe.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"organization_id", "sku"}
                )
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================
    // ORGANIZATION
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // =========================================================
    // CATEGORY
    // =========================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // =========================================================
    // PRODUCT DETAILS
    // =========================================================

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String status = "ACTIVE";


    // =========================================================
// META CATALOG
// =========================================================

    @Column(name = "meta_product_id", length = 100)
    private String metaProductId;

    @Column(name = "meta_retailer_id", length = 100)
    private String metaRetailerId;
    // =========================================================
    // TIMESTAMPS
    // =========================================================

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================
    // CREATE
    // =========================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public Product() {
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(
            Organization organization
    ) {
        this.organization = organization;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(
            Category category
    ) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name
    ) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description
    ) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(
            BigDecimal price
    ) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(
            String currency
    ) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(
            String imageUrl
    ) {
        this.imageUrl = imageUrl;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(
            String sku
    ) {
        this.sku = sku;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status
    ) {
        this.status = status;
    }

    public String getMetaProductId() {
        return metaProductId;
    }

    public void setMetaProductId(
            String metaProductId
    ) {
        this.metaProductId = metaProductId;
    }

    public String getMetaRetailerId() {
        return metaRetailerId;
    }

    public void setMetaRetailerId(
            String metaRetailerId
    ) {
        this.metaRetailerId = metaRetailerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}