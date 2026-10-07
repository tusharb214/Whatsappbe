package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.dto.flow.FlowRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowResponse;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FlowService {

    private final FlowRepository flowRepository;

    public FlowService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public FlowResponse createFlow(
            FlowRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        if (flowRepository.existsByNameAndOrganizationId(
                request.name(),
                organizationId
        )) {
            throw new RuntimeException(
                    "A flow with this name already exists in your organization"
            );
        }

        Flow flow = Flow.builder()
                .organizationId(organizationId)
                .name(request.name())
                .description(request.description())
                .status(Flow.FlowStatus.DRAFT)
                .version(1)
                .build();

        Flow savedFlow = flowRepository.save(flow);

        return toResponse(savedFlow);
    }

    @Transactional(readOnly = true)
    public List<FlowResponse> getAllFlows(
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return flowRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowResponse getFlowById(
            Long flowId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(
                flowId,
                organizationId
        );

        return toResponse(flow);
    }

    public FlowResponse updateFlow(
            Long flowId,
            FlowRequest request,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(
                flowId,
                organizationId
        );

        if (!flow.getName().equals(request.name())
                && flowRepository.existsByNameAndOrganizationId(
                request.name(),
                organizationId
        )) {

            throw new RuntimeException(
                    "A flow with this name already exists in your organization"
            );
        }

        flow.setName(request.name());
        flow.setDescription(request.description());

        if (Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())) {
            flow.setStatus(Flow.FlowStatus.DRAFT);
        }

        Flow updatedFlow = flowRepository.save(flow);

        return toResponse(updatedFlow);
    }

    public void deleteFlow(
            Long flowId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        Flow flow = getOrganizationFlow(
                flowId,
                organizationId
        );

        flowRepository.delete(flow);
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

    private FlowResponse toResponse(Flow flow) {

        return new FlowResponse(
                flow.getId(),
                flow.getOrganizationId(),
                flow.getName(),
                flow.getDescription(),
                flow.getMetaFlowId(),
                flow.getStatus(),
                flow.getMetaFlowStatus(),
                flow.getVersion(),
                flow.getCreatedAt(),
                flow.getUpdatedAt(),
                flow.getLastPublishedAt(),
                flow.getLastPublishError()
        );
    }

    private Long getOrganizationId(
            Authentication authentication
    ) {

        if (authentication == null
                || authentication.getPrincipal() == null) {

            throw new RuntimeException(
                    "Authentication required"
            );
        }

        Object principal = authentication.getPrincipal();

        /*
         * Our JWT filter stores organizationId
         * inside JwtAuthenticationDetails.
         */
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