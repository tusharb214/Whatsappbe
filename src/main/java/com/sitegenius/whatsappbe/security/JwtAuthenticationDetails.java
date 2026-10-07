package com.sitegenius.whatsappbe.security;

public class JwtAuthenticationDetails {

    private final Long userId;
    private final Long organizationId;

    public JwtAuthenticationDetails(
            Long userId,
            Long organizationId) {

        this.userId = userId;
        this.organizationId = organizationId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }
}