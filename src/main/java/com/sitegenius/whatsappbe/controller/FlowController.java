package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.flow.FlowRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowResponse;
import com.sitegenius.whatsappbe.dto.flow.FlowNodeRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowNodeResponse;
import com.sitegenius.whatsappbe.dto.flow.FlowEdgeRequest;
import com.sitegenius.whatsappbe.dto.flow.FlowEdgeResponse;
import com.sitegenius.whatsappbe.service.FlowService;
import com.sitegenius.whatsappbe.service.FlowNodeService;
import com.sitegenius.whatsappbe.service.FlowEdgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.sitegenius.whatsappbe.service.MetaFlowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@RequestMapping("/api/flows")
public class FlowController {

    private final FlowService flowService;
    private final FlowNodeService flowNodeService;
    private final FlowEdgeService flowEdgeService;
    private final MetaFlowService metaFlowService;
    private final ObjectMapper objectMapper;

    public FlowController(
            FlowService flowService,
            FlowNodeService flowNodeService,
            FlowEdgeService flowEdgeService,
            MetaFlowService metaFlowService,
            ObjectMapper objectMapper
    ) {
        this.flowService = flowService;
        this.flowNodeService = flowNodeService;
        this.flowEdgeService = flowEdgeService;
        this.metaFlowService = metaFlowService;
        this.objectMapper = objectMapper;
    }

    // =========================
    // FLOW
    // =========================

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowResponse> createFlow(
            @Valid @RequestBody FlowRequest request,
            Authentication authentication
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        flowService.createFlow(
                                request,
                                authentication
                        )
                );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<FlowResponse>> getAllFlows(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowService.getAllFlows(authentication)
        );
    }

    @GetMapping("/{flowId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<FlowResponse> getFlowById(
            @PathVariable Long flowId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowService.getFlowById(
                        flowId,
                        authentication
                )
        );
    }

    @PutMapping("/{flowId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowResponse> updateFlow(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowService.updateFlow(
                        flowId,
                        request,
                        authentication
                )
        );
    }

    @DeleteMapping("/{flowId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteFlow(
            @PathVariable Long flowId,
            Authentication authentication
    ) {

        flowService.deleteFlow(
                flowId,
                authentication
        );

        return ResponseEntity.noContent().build();
    }

    // =========================
    // NODES
    // =========================

    @PostMapping("/{flowId}/nodes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowNodeResponse> createNode(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowNodeRequest request,
            Authentication authentication
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        flowNodeService.createNode(
                                flowId,
                                request,
                                authentication
                        )
                );
    }

    @GetMapping("/{flowId}/nodes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<FlowNodeResponse>> getNodes(
            @PathVariable Long flowId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowNodeService.getNodes(
                        flowId,
                        authentication
                )
        );
    }

    @GetMapping("/{flowId}/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<FlowNodeResponse> getNodeById(
            @PathVariable Long flowId,
            @PathVariable Long nodeId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowNodeService.getNodeById(
                        flowId,
                        nodeId,
                        authentication
                )
        );
    }

    @PutMapping("/{flowId}/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowNodeResponse> updateNode(
            @PathVariable Long flowId,
            @PathVariable Long nodeId,
            @Valid @RequestBody FlowNodeRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowNodeService.updateNode(
                        flowId,
                        nodeId,
                        request,
                        authentication
                )
        );
    }

    @DeleteMapping("/{flowId}/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteNode(
            @PathVariable Long flowId,
            @PathVariable Long nodeId,
            Authentication authentication
    ) {

        flowNodeService.deleteNode(
                flowId,
                nodeId,
                authentication
        );

        return ResponseEntity.noContent().build();
    }

    // =========================
    // EDGES
    // =========================

    @PostMapping("/{flowId}/edges")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowEdgeResponse> createEdge(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowEdgeRequest request,
            Authentication authentication
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        flowEdgeService.createEdge(
                                flowId,
                                request,
                                authentication
                        )
                );
    }

    @GetMapping("/{flowId}/edges")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<FlowEdgeResponse>> getEdges(
            @PathVariable Long flowId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowEdgeService.getEdges(
                        flowId,
                        authentication
                )
        );
    }

    @GetMapping("/{flowId}/edges/{edgeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<FlowEdgeResponse> getEdgeById(
            @PathVariable Long flowId,
            @PathVariable Long edgeId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowEdgeService.getEdgeById(
                        flowId,
                        edgeId,
                        authentication
                )
        );
    }

    @PutMapping("/{flowId}/edges/{edgeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<FlowEdgeResponse> updateEdge(
            @PathVariable Long flowId,
            @PathVariable Long edgeId,
            @Valid @RequestBody FlowEdgeRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                flowEdgeService.updateEdge(
                        flowId,
                        edgeId,
                        request,
                        authentication
                )
        );
    }


    @DeleteMapping("/{flowId}/edges/{edgeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteEdge(
            @PathVariable Long flowId,
            @PathVariable Long edgeId,
            Authentication authentication
    ) {

        flowEdgeService.deleteEdge(
                flowId,
                edgeId,
                authentication
        );

        return ResponseEntity.noContent().build();
    }

    // =========================
// META FLOW
// =========================

    @PostMapping(value = "/{flowId}/sync-meta", produces = "application/json")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<String> syncMeta(
            @PathVariable Long flowId,
            Authentication authentication
    ) throws Exception {
        return ResponseEntity.ok(
                objectMapper.writeValueAsString(
                        metaFlowService.syncMeta(flowId, authentication)
                )
        );
    }


    @GetMapping("/{flowId}/meta-json")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> getMetaJson(
            @PathVariable Long flowId,
            Authentication authentication
    ) throws Exception {

        JsonNode metaJson =
                metaFlowService.getMetaFlowJson(
                        flowId,
                        authentication
                );

        return ResponseEntity.ok(
                objectMapper.writeValueAsString(metaJson)
        );
    }

    // =========================
    // PUBLISH META FLOW
    // =========================

    @PostMapping("/{flowId}/publish-meta")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> publishMeta(
            @PathVariable Long flowId,
            Authentication authentication
    ) {

        metaFlowService.publishMeta(
                flowId,
                authentication
        );

        return ResponseEntity.ok().build();
    }
    // =========================
// TEST META FLOW
// =========================

    @PostMapping("/{flowId}/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<String> testFlow(
            @PathVariable Long flowId,
            @RequestParam String customerPhoneNumber,
            Authentication authentication
    ) {

        String messageId =
                metaFlowService.sendTestFlow(
                        flowId,
                        customerPhoneNumber,
                        authentication
                );

        return ResponseEntity.ok(messageId);
    }
}

