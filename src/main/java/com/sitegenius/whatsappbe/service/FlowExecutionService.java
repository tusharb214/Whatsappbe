package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowEdge;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.repository.FlowEdgeRepository;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowExecutionService {

    private final FlowNodeRepository flowNodeRepository;
    private final FlowEdgeRepository flowEdgeRepository;
    private final FlowRepository flowRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final ObjectMapper objectMapper;

    /**
     * Entry point for an incoming WhatsApp message.
     *
     * @param organizationId organization receiving the message
     * @param customerPhoneNumber customer WhatsApp number
     * @param messageText incoming message text
     * @param messageType incoming WhatsApp message type
     */
    @Transactional
    public void processIncomingMessage(
            Long organizationId,
            String customerPhoneNumber,
            String messageText,
            String messageType
    ) {

        if (organizationId == null) {
            return;
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {
            return;
        }

        List<FlowNode> allNodes =
                flowNodeRepository.findAll();

        if (allNodes.isEmpty()) {
            return;
        }

        for (FlowNode triggerNode : allNodes) {

            if (triggerNode.getNodeType() != FlowNode.NodeType.KEYWORD_TRIGGER &&
                    triggerNode.getNodeType() != FlowNode.NodeType.MESSAGE_TRIGGER) {
                continue;
            }

            Flow flow = findPublishedFlow(
                    triggerNode.getFlowId(),
                    organizationId
            );

            System.out.println("=== FLOW DEBUG ===");
            System.out.println("Organization ID = " + organizationId);
            System.out.println("Trigger Node ID = " + triggerNode.getId());
            System.out.println("Trigger Node Type = " + triggerNode.getNodeType());
            System.out.println("Trigger Node Config = " + triggerNode.getConfig());
            System.out.println("Message Text = [" + messageText + "]");
            System.out.println("Message Type = [" + messageType + "]");
            System.out.println("Flow Found = " + (flow != null));

            if (flow == null) {
                continue;
            }

            boolean matched;

            if (triggerNode.getNodeType() ==
                    FlowNode.NodeType.KEYWORD_TRIGGER) {

                matched = matchesKeywordTrigger(
                        triggerNode,
                        messageText
                );

            } else {

                matched = matchesMessageTrigger(
                        triggerNode,
                        messageType
                );
            }

            if (!matched) {
                continue;
            }

            System.out.println(
                    "=== FLOW TRIGGER MATCHED ==="
            );

            System.out.println(
                    "Flow ID = " + flow.getId()
            );

            System.out.println(
                    "Flow Name = " + flow.getName()
            );

            System.out.println(
                    "Trigger Node ID = " + triggerNode.getId()
            );

            executeFromNode(
                    flow,
                    triggerNode,
                    organizationId,
                    customerPhoneNumber,
                    messageText,
                    messageType,
                    new HashSet<>()
            );

            /*
             * One incoming message should trigger one matching flow.
             */
            return;
        }
    }

    /**
     * Finds a published flow belonging to the organization.
     */
    private Flow findPublishedFlow(
            Long flowId,
            Long organizationId
    ) {

        return flowRepository
                .findByIdAndOrganizationId(
                        flowId,
                        organizationId
                )
                .filter(flow ->
                        flow.getStatus() ==
                                Flow.FlowStatus.PUBLISHED
                )
                .orElse(null);
    }

    /**
     * Checks KEYWORD_TRIGGER configuration.
     *
     * Supported config examples:
     *
     * {
     *   "keywords": "hi,hello,start"
     * }
     *
     * or
     *
     * {
     *   "keywords": ["hi", "hello", "start"]
     * }
     */
    private boolean matchesKeywordTrigger(
            FlowNode node,
            String messageText
    ) {

        if (messageText == null ||
                messageText.isBlank()) {
            return false;
        }

        try {

            JsonNode config =
                    objectMapper.readTree(node.getConfig());

            JsonNode keywordsNode =
                    config.get("keywords");

            if (keywordsNode == null) {
                return false;
            }

            String incoming =
                    messageText.trim().toLowerCase();

            if (keywordsNode.isArray()) {

                for (JsonNode keyword :
                        keywordsNode) {

                    if (keyword.asText()
                            .trim()
                            .equalsIgnoreCase(incoming)) {

                        return true;
                    }
                }

            } else {

                String keywords =
                        keywordsNode.asText();

                String[] values =
                        keywords.split(",");

                for (String keyword : values) {

                    if (keyword.trim()
                            .equalsIgnoreCase(incoming)) {

                        return true;
                    }
                }
            }

        } catch (Exception exception) {

            System.err.println(
                    "Failed to parse keyword trigger config: "
                            + exception.getMessage()
            );
        }

        return false;
    }

    /**
     * Checks MESSAGE_TRIGGER configuration.
     *
     * Supported config:
     *
     * {
     *   "messageType": "ANY"
     * }
     *
     * Examples:
     *
     * TEXT
     * IMAGE
     * VIDEO
     * AUDIO
     * DOCUMENT
     * LOCATION
     * CONTACT
     */
    private boolean matchesMessageTrigger(
            FlowNode node,
            String messageType
    ) {

        if (messageType == null ||
                messageType.isBlank()) {
            return false;
        }

        try {

            JsonNode config =
                    objectMapper.readTree(node.getConfig());

            JsonNode typeNode =
                    config.get("messageType");

            if (typeNode == null) {
                return false;
            }

            String configuredType =
                    typeNode.asText();

            if ("ANY".equalsIgnoreCase(
                    configuredType
            )) {
                return true;
            }

            return configuredType.equalsIgnoreCase(
                    messageType
            );

        } catch (Exception exception) {

            System.err.println(
                    "Failed to parse message trigger config: "
                            + exception.getMessage()
            );
        }

        return false;
    }

    /**
     * Executes nodes connected after the trigger.
     */
    private void executeFromNode(
            Flow flow,
            FlowNode currentNode,
            Long organizationId,
            String customerPhoneNumber,
            String messageText,
            String messageType,
            Set<Long> visitedNodes
    ) {

        if (currentNode == null ||
                currentNode.getId() == null) {
            return;
        }

        /*
         * Prevent accidental infinite loops.
         */
        if (!visitedNodes.add(currentNode.getId())) {
            return;
        }

        List<FlowEdge> outgoingEdges =
                flowEdgeRepository
                        .findBySourceNodeId(
                                currentNode.getId()
                        );

        if (outgoingEdges.isEmpty()) {

            System.out.println(
                    "Flow execution ended at node ID = "
                            + currentNode.getId()
            );

            return;
        }

        for (FlowEdge edge : outgoingEdges) {

            FlowNode nextNode =
                    flowNodeRepository
                            .findByIdAndFlowId(
                                    edge.getTargetNodeId(),
                                    flow.getId()
                            )
                            .orElse(null);

            if (nextNode == null) {
                continue;
            }

            executeNode(
                    flow,
                    nextNode,
                    organizationId,
                    customerPhoneNumber,
                    messageText,
                    messageType
            );

            /*
             * Continue through the flow.
             */
            executeFromNode(
                    flow,
                    nextNode,
                    organizationId,
                    customerPhoneNumber,
                    messageText,
                    messageType,
                    visitedNodes
            );
        }
    }

    /**
     * Executes a single supported node.
     */
    private void executeNode(
            Flow flow,
            FlowNode node,
            Long organizationId,
            String customerPhoneNumber,
            String messageText,
            String messageType
    ) {

        if (node.getNodeType() ==
                FlowNode.NodeType.TEXT_MESSAGE) {

            sendTextMessage(
                    node,
                    organizationId,
                    customerPhoneNumber
            );

            return;
        }

        /*
         * Existing MESSAGE node support.
         */
        if (node.getNodeType() ==
                FlowNode.NodeType.MESSAGE) {

            sendTextMessage(
                    node,
                    organizationId,
                    customerPhoneNumber
            );

            return;
        }

        /*
         * Other node types will be implemented
         * after the basic trigger → message flow
         * is working.
         */
        if (node.getNodeType() ==
                FlowNode.NodeType.END) {

            System.out.println(
                    "=== FLOW END NODE REACHED ==="
            );
        }
    }

    /**
     * Sends TEXT_MESSAGE / MESSAGE node content.
     *
     * Supported config:
     *
     * {
     *   "text": "Hello! 👋 Welcome."
     * }
     */
    private void sendTextMessage(
            FlowNode node,
            Long organizationId,
            String customerPhoneNumber
    ) {

        try {

            JsonNode config =
                    objectMapper.readTree(
                            node.getConfig()
                    );

            JsonNode textNode =
                    config.get("text");

            if (textNode == null) {

                System.err.println(
                        "Text message node has no text. Node ID = "
                                + node.getId()
                );

                return;
            }

            String text =
                    textNode.asText();

            if (text == null ||
                    text.isBlank()) {
                return;
            }

            System.out.println(
                    "=== FLOW SENDING TEXT ==="
            );

            System.out.println(
                    "Node ID = " + node.getId()
            );

            System.out.println(
                    "Message = " + text
            );

            whatsAppCloudApiService.sendTextMessage(
                    organizationId,
                    customerPhoneNumber,
                    text
            );

        } catch (Exception exception) {

            System.err.println(
                    "Flow text message execution failed: "
                            + exception.getMessage()
            );
        }
    }
}