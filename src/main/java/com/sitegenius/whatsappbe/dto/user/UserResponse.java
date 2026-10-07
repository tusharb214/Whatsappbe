package com.sitegenius.whatsappbe.dto.user;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String status;
    private Long organizationId;
    private String organizationName;
    private LocalDateTime createdAt;

    public UserResponse(
            Long id,
            String name,
            String email,
            String role,
            String status,
            Long organizationId,
            String organizationName,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.createdAt = createdAt;
    }

    // ================================
    // ID
    // ================================

    public Long getId() {
        return id;
    }

    // ================================
    // NAME
    // ================================

    public String getName() {
        return name;
    }

    // ================================
    // EMAIL
    // ================================

    public String getEmail() {
        return email;
    }

    // ================================
    // ROLE
    // ================================

    public String getRole() {
        return role;
    }

    // ================================
    // STATUS
    // ================================

    public String getStatus() {
        return status;
    }

    // ================================
    // ORGANIZATION ID
    // ================================

    public Long getOrganizationId() {
        return organizationId;
    }

    // ================================
    // ORGANIZATION NAME
    // ================================

    public String getOrganizationName() {
        return organizationName;
    }

    // ================================
    // CREATED AT
    // ================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}