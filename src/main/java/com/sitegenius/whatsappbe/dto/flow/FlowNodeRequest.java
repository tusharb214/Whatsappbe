package com.sitegenius.whatsappbe.dto.flow;

import com.sitegenius.whatsappbe.entity.FlowNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlowNodeRequest(

        @NotBlank(message = "Node key is required")
        @Size(max = 100, message = "Node key must not exceed 100 characters")
        String nodeKey,

        @NotNull(message = "Node type is required")
        FlowNode.NodeType nodeType,

        @NotBlank(message = "Node name is required")
        @Size(max = 150, message = "Node name must not exceed 150 characters")
        String name,

        String config,

        Double positionX,

        Double positionY

) {
}