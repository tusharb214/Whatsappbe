package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.flow.FlowNodeRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowNodeResponse;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FlowNodeService {

    private final FlowNodeRepository flowNodeRepository;
    private final FlowRepository flowRepository;

    public FlowNodeService(
            FlowNodeRepository flowNodeRepository,
            FlowRepository flowRepository
    ) {
        this.flowNodeRepository = flowNodeRepository;
        this.flowRepository = flowRepository;
    }

    public FlowNodeResponse createNode(
            Long flowId,
            FlowNodeRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);
        Flow flow = getOrganizationFlow(flowId, organizationId);

        if (flowNodeRepository.existsByFlowIdAndNodeKey(
                flowId,
                request.nodeKey()
        )) {
            throw new RuntimeException(
                    "A node with this key already exists in this flow"
            );
        }
        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }
        FlowNode node = FlowNode.builder()
                .flowId(flowId)
                .nodeKey(request.nodeKey())
                .nodeType(request.nodeType())
                .name(request.name())
                .config(request.config())
                .positionX(request.positionX())
                .positionY(request.positionY())
                .build();

        return toResponse(
                flowNodeRepository.save(node)
        );
    }

    @Transactional(readOnly = true)
    public List<FlowNodeResponse> getNodes(
            Long flowId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        getOrganizationFlow(flowId, organizationId);

        return flowNodeRepository
                .findByFlowIdOrderByIdAsc(flowId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowNodeResponse getNodeById(
            Long flowId,
            Long nodeId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        getOrganizationFlow(flowId, organizationId);

        FlowNode node = getFlowNode(
                nodeId,
                flowId
        );

        return toResponse(node);
    }

    public FlowNodeResponse updateNode(
            Long flowId,
            Long nodeId,
            FlowNodeRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(flowId, organizationId);

        FlowNode node = getFlowNode(
                nodeId,
                flowId
        );

        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }

        if (!node.getNodeKey().equals(request.nodeKey())
                && flowNodeRepository.existsByFlowIdAndNodeKey(
                flowId,
                request.nodeKey()
        )) {

            throw new RuntimeException(
                    "A node with this key already exists in this flow"
            );
        }

        node.setNodeKey(request.nodeKey());
        node.setNodeType(request.nodeType());
        node.setName(request.name());
        node.setConfig(request.config());
        node.setPositionX(request.positionX());
        node.setPositionY(request.positionY());

        return toResponse(
                flowNodeRepository.save(node)
        );
    }

    public void deleteNode(
            Long flowId,
            Long nodeId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(flowId, organizationId);

        FlowNode node = getFlowNode(
                nodeId,
                flowId
        );
        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }

        flowNodeRepository.delete(node);
    }

    private Flow getOrganizationFlow(
            Long flowId,
            Long organizationId
    ) {

        return flowRepository
                .findByIdAndOrganizationId(
                        flowId,
                        organizationId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Flow not found"
                        )
                );
    }

    private FlowNode getFlowNode(
            Long nodeId,
            Long flowId
    ) {

        return flowNodeRepository
                .findByIdAndFlowId(
                        nodeId,
                        flowId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Flow node not found"
                        )
                );
    }

    private FlowNodeResponse toResponse(
            FlowNode node
    ) {

        return new FlowNodeResponse(
                node.getId(),
                node.getFlowId(),
                node.getNodeKey(),
                node.getNodeType(),
                node.getName(),
                node.getConfig(),
                node.getPositionX(),
                node.getPositionY(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }

    private Long getOrganizationId(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getDetails() == null) {

            throw new RuntimeException(
                    "Authentication required"
            );
        }

        Object details = authentication.getDetails();

        try {

            var method = details
                    .getClass()
                    .getMethod("getOrganizationId");

            Object organizationId =
                    method.invoke(details);

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization ID not found in authentication"
                );
            }

            return Long.valueOf(
                    organizationId.toString()
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to determine organization from authentication",
                    e
            );
        }
    }
}