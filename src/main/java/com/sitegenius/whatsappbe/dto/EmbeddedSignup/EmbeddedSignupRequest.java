package com.sitegenius.whatsappbe.dto;

public record EmbeddedSignupRequest(
        String code,
        String wabaId,
        String phoneNumberId,
        String businessId
) {}