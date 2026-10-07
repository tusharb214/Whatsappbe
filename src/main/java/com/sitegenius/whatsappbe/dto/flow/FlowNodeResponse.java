package com.sitegenius.whatsappbe.dto.flow;

import com.sitegenius.whatsappbe.entity.FlowNode;

import java.time.LocalDateTime;

public record FlowNodeResponse(

        Long id,

        Long flowId,

        String nodeKey,

        FlowNode.NodeType nodeType,

        String name,

        String config,

        Double positionX,

        Double positionY,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}