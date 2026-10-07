package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.flow.FlowEdgeRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowEdgeResponse;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowEdge;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.repository.FlowEdgeRepository;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FlowEdgeService {

    private final FlowEdgeRepository flowEdgeRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final FlowRepository flowRepository;

    public FlowEdgeService(
            FlowEdgeRepository flowEdgeRepository,
            FlowNodeRepository flowNodeRepository,
            FlowRepository flowRepository
    ) {
        this.flowEdgeRepository = flowEdgeRepository;
        this.flowNodeRepository = flowNodeRepository;
        this.flowRepository = flowRepository;
    }

    public FlowEdgeResponse createEdge(
            Long flowId,
            FlowEdgeRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(flowId, organizationId);
        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }

        FlowNode sourceNode = getFlowNode(
                request.sourceNodeId(),
                flowId
        );

        FlowNode targetNode = getFlowNode(
                request.targetNodeId(),
                flowId
        );

        if (sourceNode.getId().equals(targetNode.getId())) {
            throw new RuntimeException(
                    "A node cannot connect to itself"
            );
        }

        FlowEdge edge = FlowEdge.builder()
                .flowId(flowId)
                .sourceNodeId(sourceNode.getId())
                .targetNodeId(targetNode.getId())
                .sourceHandle(request.sourceHandle())
                .condition(request.condition())
                .build();

        return toResponse(
                flowEdgeRepository.save(edge)
        );
    }

    @Transactional(readOnly = true)
    public List<FlowEdgeResponse> getEdges(
            Long flowId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        getOrganizationFlow(flowId, organizationId);

        return flowEdgeRepository
                .findByFlowIdOrderByIdAsc(flowId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowEdgeResponse getEdgeById(
            Long flowId,
            Long edgeId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        getOrganizationFlow(flowId, organizationId);

        FlowEdge edge = getFlowEdge(
                edgeId,
                flowId
        );

        return toResponse(edge);
    }

    public FlowEdgeResponse updateEdge(
            Long flowId,
            Long edgeId,
            FlowEdgeRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(flowId, organizationId);

        FlowEdge edge = getFlowEdge(
                edgeId,
                flowId
        );

        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }

        FlowNode sourceNode = getFlowNode(
                request.sourceNodeId(),
                flowId
        );

        FlowNode targetNode = getFlowNode(
                request.targetNodeId(),
                flowId
        );

        if (sourceNode.getId().equals(targetNode.getId())) {
            throw new RuntimeException(
                    "A node cannot connect to itself"
            );
        }

        edge.setSourceNodeId(sourceNode.getId());
        edge.setTargetNodeId(targetNode.getId());
        edge.setSourceHandle(request.sourceHandle());
        edge.setCondition(request.condition());

        return toResponse(
                flowEdgeRepository.save(edge)
        );
    }

    public void deleteEdge(
            Long flowId,
            Long edgeId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(flowId, organizationId);

        FlowEdge edge = getFlowEdge(
                edgeId,
                flowId
        );
        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
            flowRepository.save(flow);
        }

        flowEdgeRepository.delete(edge);
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

    private FlowEdge getFlowEdge(
            Long edgeId,
            Long flowId
    ) {

        return flowEdgeRepository
                .findByIdAndFlowId(
                        edgeId,
                        flowId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Flow edge not found"
                        )
                );
    }

    private FlowEdgeResponse toResponse(
            FlowEdge edge
    ) {

        return new FlowEdgeResponse(
                edge.getId(),
                edge.getFlowId(),
                edge.getSourceNodeId(),
                edge.getTargetNodeId(),
                edge.getSourceHandle(),
                edge.getCondition(),
                edge.getCreatedAt()
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