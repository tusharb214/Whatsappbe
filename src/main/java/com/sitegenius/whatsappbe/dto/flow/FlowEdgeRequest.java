package com.sitegenius.whatsappbe.dto.flow;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlowEdgeRequest(

        @NotNull(message = "Source node ID is required")
        Long sourceNodeId,

        @NotNull(message = "Target node ID is required")
        Long targetNodeId,

        @Size(max = 100, message = "Source handle must not exceed 100 characters")
        String sourceHandle,

        @Size(max = 500, message = "Condition must not exceed 500 characters")
        String condition

) {
}