package com.sitegenius.whatsappbe.entity;

import com.sitegenius.whatsappbe.enums.Role;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }

    public User() {
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

    public void setName(String name) {
        this.name = name;
    }

    // ================================
    // EMAIL
    // ================================

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // ================================
    // PASSWORD
    // ================================

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // ================================
    // ROLE
    // ================================

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    // ================================
    // STATUS
    // ================================

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    // ================================
    // ORGANIZATION
    // ================================

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    // ================================
    // CREATED AT
    // ================================

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}