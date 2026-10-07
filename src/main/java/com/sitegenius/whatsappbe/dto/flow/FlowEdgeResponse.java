package com.sitegenius.whatsappbe.dto.flow;

import java.time.LocalDateTime;

public record FlowEdgeResponse(

        Long id,

        Long flowId,

        Long sourceNodeId,

        Long targetNodeId,

        String sourceHandle,

        String condition,

        LocalDateTime createdAt

) {
}