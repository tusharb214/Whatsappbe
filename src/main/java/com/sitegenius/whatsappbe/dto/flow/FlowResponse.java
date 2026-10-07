package com.sitegenius.whatsappbe.dto.flow;

import com.sitegenius.whatsappbe.entity.Flow;

import java.time.LocalDateTime;

public record FlowResponse(

        Long id,

        Long organizationId,

        String name,

        String description,

        String metaFlowId,

        Flow.FlowStatus status,

        String metaFlowStatus,

        Integer version,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        LocalDateTime lastPublishedAt,

        String lastPublishError

) {
}