package com.sitegenius.whatsappbe.dto.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlowRequest(

        @NotBlank(message = "Flow name is required")
        @Size(max = 150, message = "Flow name must not exceed 150 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description

) {
}