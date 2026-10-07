package com.sitegenius.whatsappbe.dto.auth;

public class LoginResponse {

    private String token;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private Long organizationId;

    public LoginResponse(
            String token,
            Long userId,
            String name,
            String email,
            String role,
            Long organizationId) {

        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.organizationId = organizationId;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Long getOrganizationId() {
        return organizationId;
    }
}