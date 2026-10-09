
package com.sitegenius.whatsappbe.dto.auth;

public class SignupResponse {

    private String message;
    private Long userId;
    private Long organizationId;
    private String name;
    private String email;
    private String role;

    public SignupResponse() {
    }

    public SignupResponse(
            String message,
            Long userId,
            Long organizationId,
            String name,
            String email,
            String role
    ) {
        this.message = message;
        this.userId = userId;
        this.organizationId = organizationId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrganizationId() {
        return organizationId;
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
}