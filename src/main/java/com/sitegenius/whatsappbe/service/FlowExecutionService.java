package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowEdge;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.entity.FlowExecutionSession;
import com.sitegenius.whatsappbe.repository.FlowEdgeRepository;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowExecutionSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.repository.ProductRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowExecutionService {

    private final FlowNodeRepository flowNodeRepository;
    private final FlowExecutionSessionRepository flowExecutionSessionRepository;
    private final FlowEdgeRepository flowEdgeRepository;
    private final FlowRepository flowRepository;
    private final ProductRepository productRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final ObjectMapper objectMapper;
    private final OrderingService orderingService;

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

        // Route a customer's LIST selection before checking normal triggers.
        if (routePendingInput(organizationId, customerPhoneNumber, messageText, messageType)) {
            return;
        }

        if (routeListSelection(
                organizationId,
                customerPhoneNumber,
                messageText,
                messageType
        )) {
            return;
        }

        List<Long> publishedFlowIds = flowRepository
                .findByOrganizationIdAndStatus(
                        organizationId,
                        Flow.FlowStatus.PUBLISHED
                )
                .stream()
                .map(Flow::getId)
                .toList();

        if (publishedFlowIds.isEmpty()) {
            return;
        }

        List<FlowNode> allNodes =
                flowNodeRepository.findByFlowIdInAndNodeTypeInOrderByIdAsc(
                        publishedFlowIds,
                        List.of(
                                FlowNode.NodeType.KEYWORD_TRIGGER,
                                FlowNode.NodeType.MESSAGE_TRIGGER
                        )
                );

        if (allNodes.isEmpty()) {
            return;
        }

        for (FlowNode triggerNode : allNodes) {

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
     * Routes an interactive LIST selection to the edge whose sourceHandle
     * matches the selected option ID.
     */
    private boolean routeListSelection(
            Long organizationId,
            String customerPhoneNumber,
            String selectedOptionId,
            String messageType
    ) {
        if (!"interactive".equalsIgnoreCase(messageType)
                || selectedOptionId == null
                || selectedOptionId.isBlank()) {
            return false;
        }

        FlowExecutionSession session = flowExecutionSessionRepository
                .findByOrganizationIdAndCustomerPhoneNumber(
                        organizationId,
                        customerPhoneNumber
                )
                .orElse(null);

        if (session == null || session.getPendingNodeId() == null) {
            return false;
        }

        Flow flow = findPublishedFlow(session.getFlowId(), organizationId);
        if (flow == null) {
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            return false;
        }

        FlowNode listNode = flowNodeRepository
                .findByIdAndFlowId(session.getPendingNodeId(), flow.getId())
                .orElse(null);

        if (listNode == null || listNode.getNodeType() != FlowNode.NodeType.LIST) {
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            return false;
        }

        boolean optionExists = false;
        try {
            JsonNode options = objectMapper
                    .readTree(listNode.getConfig())
                    .path("options");

            if (options.isArray()) {
                for (JsonNode option : options) {
                    if (selectedOptionId.equals(option.path("id").asText())) {
                        optionExists = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read LIST options: " + e.getMessage());
            return true;
        }

        if (!optionExists) {
            return true;
        }

        boolean matchingEdgeExists = flowEdgeRepository
                .findBySourceNodeId(listNode.getId())
                .stream()
                .anyMatch(edge ->
                        selectedOptionId.equals(edge.getSourceHandle())
                        && flowNodeRepository.findByIdAndFlowId(
                                edge.getTargetNodeId(), flow.getId()
                        ).isPresent()
                );

        if (!matchingEdgeExists) {
            System.out.println(
                    "No connected edge for LIST option ID: " + selectedOptionId
            );
            return true;
        }

        // Consume the pending selection before executing its branch.
        session.setPendingNodeId(null);
        flowExecutionSessionRepository.save(session);

        executeFromNode(
                flow,
                listNode,
                organizationId,
                customerPhoneNumber,
                selectedOptionId,
                messageType,
                new HashSet<>()
        );

        return true;
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

        if ((currentNode.getNodeType() == FlowNode.NodeType.LIST
                || currentNode.getNodeType() == FlowNode.NodeType.BUTTON)
                && messageText != null
                && !messageText.isBlank()) {
            outgoingEdges = outgoingEdges.stream()
                    .filter(edge -> messageText.equals(edge.getSourceHandle()))
                    .toList();
        }

        if (currentNode.getNodeType() == FlowNode.NodeType.CONDITION) {
            final String branch = evaluateCondition(
                    currentNode, organizationId, customerPhoneNumber)
                    ? "true" : "false";
            outgoingEdges = outgoingEdges.stream()
                    .filter(edge -> branch.equals(edge.getSourceHandle()))
                    .toList();
        }

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

            /*
             * Persist the pending LIST node for this organization/customer.
             */
            if (isWaitingNode(nextNode.getNodeType())) {
                FlowExecutionSession session = flowExecutionSessionRepository
                        .findByOrganizationIdAndCustomerPhoneNumber(
                                organizationId,
                                customerPhoneNumber
                        )
                        .orElseGet(FlowExecutionSession::new);

                session.setOrganizationId(organizationId);
                session.setCustomerPhoneNumber(customerPhoneNumber);
                session.setFlowId(flow.getId());
                session.setPendingNodeId(nextNode.getId());
                flowExecutionSessionRepository.save(session);

                executeNode(
                        flow,
                        nextNode,
                        organizationId,
                        customerPhoneNumber,
                        messageText,
                        messageType
                );

                // Wait for the customer's interactive selection.
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
             * Continue through the flow for non-interactive nodes.
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
         * Dynamic WhatsApp LIST node support.
         * Config format:
         * {
         *   "text": "Choose an option",
         *   "buttonText": "View options",
         *   "sectionTitle": "Menu",
         *   "options": [
         *     {"id": "meal", "title": "Order Today's Meal", "description": "Order today"}
         *   ]
         * }
         */
        if (node.getNodeType() == FlowNode.NodeType.BUTTON) {
            sendButtonNode(node, organizationId, customerPhoneNumber);
            return;
        }

        if (isInputNode(node.getNodeType())) {
            askQuestion(node, organizationId, customerPhoneNumber);
            return;
        }

        if (node.getNodeType() == FlowNode.NodeType.SET_VARIABLE) {
            runSetVariable(flow, node, organizationId, customerPhoneNumber);
            return;
        }

        if (node.getNodeType() == FlowNode.NodeType.IMAGE) {
            sendImageNode(node, organizationId, customerPhoneNumber);
            return;
        }

        if (node.getNodeType() == FlowNode.NodeType.LIST) {
            sendListMessage(node, organizationId, customerPhoneNumber);
            return;
        }

        if (node.getNodeType() == FlowNode.NodeType.PRODUCT_MENU) {
            orderingService.start(flow, node, organizationId, customerPhoneNumber);
            return;
        }

        if (node.getNodeType() == FlowNode.NodeType.PRODUCT_MENU) {
            FlowExecutionSession session = flowExecutionSessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(
                            organizationId,
                            customerPhoneNumber
                    )
                    .orElseGet(() -> {
                        FlowExecutionSession newSession =
                                new FlowExecutionSession();
                        newSession.setOrganizationId(organizationId);
                        newSession.setCustomerPhoneNumber(customerPhoneNumber);
                        newSession.setFlowId(flow.getId());
                        return flowExecutionSessionRepository.save(newSession);
                    });

            sendProductMenu(node, session);
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
     * Sends a configured WhatsApp interactive list.
     */

    private void sendImageNode(
            FlowNode node,
            Long organizationId,
            String customerPhoneNumber) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            String mediaUrl = config.path("mediaUrl").asText("");
            String caption = config.path("caption").asText("");

            if (mediaUrl.isBlank()) {
                System.err.println("IMAGE node has no mediaUrl. Node ID = " + node.getId());
                return;
            }

            whatsAppCloudApiService.sendImageUrlMessage(
                    organizationId, customerPhoneNumber, mediaUrl, caption);
        } catch (Exception exception) {
            System.err.println("Flow IMAGE node execution failed: "
                    + exception.getMessage());
        }
    }

    private void sendListMessage(
            FlowNode node,
            Long organizationId,
            String customerPhoneNumber
    ) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());

            String message = config.path("text").asText(
                    config.path("message").asText("Please select an option")
            );
            String buttonText = config.path("buttonText").asText("View options");
            String sectionTitle = config.path("sectionTitle").asText("Options");

            JsonNode options = config.path("options");
            if (!options.isArray() || options.isEmpty()) {
                System.err.println(
                        "LIST node has no options configured. Node ID = " + node.getId()
                );
                return;
            }

            List<java.util.Map<String, String>> rows = new java.util.ArrayList<>();

            for (JsonNode option : options) {
                String id = option.path("id").asText("");
                String title = option.path("title").asText(
                        option.path("text").asText("")
                );
                String description = option.path("description").asText("");

                if (id.isBlank() || title.isBlank()) {
                    System.err.println(
                            "Skipping LIST option without id/title. Node ID = " + node.getId()
                    );
                    continue;
                }

                java.util.Map<String, String> row = new java.util.HashMap<>();
                row.put("id", id);
                row.put("title", title);
                if (!description.isBlank()) {
                    row.put("description", description);
                }
                rows.add(row);
            }

            if (rows.isEmpty()) {
                System.err.println(
                        "LIST node has no valid options. Node ID = " + node.getId()
                );
                return;
            }

            whatsAppCloudApiService.sendListMessage(
                    organizationId,
                    customerPhoneNumber,
                    message,
                    buttonText,
                    sectionTitle,
                    rows
            );

        } catch (Exception exception) {
            System.err.println(
                    "Flow LIST node execution failed: " + exception.getMessage()
            );
        }
    }



    private void sendProductMenu(
            FlowNode node,
            FlowExecutionSession session) {

        try {
            JsonNode config = objectMapper.readTree(
                    node.getConfig() == null
                            ? "{}"
                            : node.getConfig()
            );

            String selectionMode = config.hasNonNull("selectionMode")
                    ? config.get("selectionMode").asText()
                    : "ALL_ACTIVE_CATEGORIES";

            String message = config.hasNonNull("message")
                    ? config.get("message").asText()
                    : "Please select your products";

            Long organizationId = session.getOrganizationId();

            List<Product> products =
                    productRepository
                            .findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                                    organizationId, "ACTIVE");

            if ("SELECTED_PRODUCTS".equals(selectionMode)) {
                Set<Long> selectedIds = new HashSet<>();

                JsonNode ids = config.get("productIds");
                if (ids != null && ids.isArray()) {
                    for (JsonNode id : ids) {
                        if (id.canConvertToLong()) {
                            selectedIds.add(id.asLong());
                        }
                    }
                }

                products.removeIf(
                        product -> product.getId() == null
                                || !selectedIds.contains(product.getId()));
            }

            else if ("SELECTED_CATEGORIES".equals(selectionMode)) {
                Set<Long> selectedCategoryIds = new HashSet<>();

                JsonNode ids = config.get("categoryIds");
                if (ids != null && ids.isArray()) {
                    for (JsonNode id : ids) {
                        if (id.canConvertToLong()) {
                            selectedCategoryIds.add(id.asLong());
                        }
                    }
                }

                products.removeIf(product ->
                        product.getCategory() == null
                                || product.getCategory().getId() == null
                                || !selectedCategoryIds.contains(
                                product.getCategory().getId()));
            }

            if (products.isEmpty()) {
                throw new RuntimeException(
                        "No active products found for this Product Menu");
            }

            whatsAppCloudApiService.sendProductListMessage(
                    organizationId,
                    session.getCustomerPhoneNumber(),
                    message,
                    products
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to execute PRODUCT_MENU node: "
                            + e.getMessage(), e);
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

            JsonNode textNode = config.get("text");

            if (textNode == null || textNode.isNull()
                    || textNode.asText().isBlank()) {
                textNode = config.get("message");
            }

            if (textNode == null || textNode.isNull()) {
                System.err.println(
                        "Text message node has no text/message. Node ID = "
                                + node.getId()
                );
                return;
            }

            String text = applyVariables(textNode.asText(), organizationId, customerPhoneNumber);

            if (text.isBlank()) {
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

    // =========================================================
    // STAGE 1: buttons, inputs, variables, conditions
    // =========================================================

    private boolean isInputNode(FlowNode.NodeType type) {
        return type == FlowNode.NodeType.TEXT_INPUT
                || type == FlowNode.NodeType.PHONE_INPUT
                || type == FlowNode.NodeType.EMAIL_INPUT
                || type == FlowNode.NodeType.NUMBER_INPUT
                || type == FlowNode.NodeType.DATE_INPUT;
    }

    private boolean isWaitingNode(FlowNode.NodeType type) {
        return type == FlowNode.NodeType.LIST
                || type == FlowNode.NodeType.BUTTON
                || isInputNode(type);
    }

    private JsonNode configOf(FlowNode node) throws Exception {
        return objectMapper.readTree(
                node.getConfig() == null ? "{}" : node.getConfig());
    }

    private java.util.Map<String, String> readVariables(FlowExecutionSession session) {
        try {
            if (session != null
                    && session.getVariablesJson() != null
                    && !session.getVariablesJson().isBlank()) {
                return objectMapper.readValue(
                        session.getVariablesJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, String>>() {
                        });
            }
        } catch (Exception e) {
            System.err.println("Bad variables json: " + e.getMessage());
        }
        return new java.util.LinkedHashMap<>();
    }

    private void putVariable(FlowExecutionSession session, String name, String value) {
        if (name == null || name.isBlank()) {
            return;
        }
        java.util.Map<String, String> vars = readVariables(session);
        vars.put(name.trim(), value == null ? "" : value);
        try {
            session.setVariablesJson(objectMapper.writeValueAsString(vars));
        } catch (Exception e) {
            System.err.println("Could not save variable: " + e.getMessage());
        }
    }

    private String applyVariables(String text, Long organizationId, String phone) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        FlowExecutionSession session = flowExecutionSessionRepository
                .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                .orElse(null);
        if (session == null) {
            return text;
        }
        for (java.util.Map.Entry<String, String> e : readVariables(session).entrySet()) {
            text = text.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return text;
    }

    private void sendButtonNode(FlowNode node, Long organizationId, String phone) {
        try {
            JsonNode config = configOf(node);
            String text = applyVariables(
                    config.path("text").asText("Choose an option"), organizationId, phone);

            List<String[]> buttons = new ArrayList<>();
            JsonNode array = config.path("buttons");
            if (array.isArray()) {
                for (JsonNode b : array) {
                    String id = b.path("id").asText("");
                    String title = b.path("text").asText("").trim();
                    if (id.isBlank() || title.isBlank() || buttons.size() >= 3) {
                        continue;
                    }
                    buttons.add(new String[]{id, title.length() > 20 ? title.substring(0, 20) : title});
                }
            }

            if (buttons.isEmpty()) {
                System.err.println("BUTTON node has no buttons. Node ID = " + node.getId());
                return;
            }

            whatsAppCloudApiService.sendButtonsMessage(organizationId, phone, text, buttons);
        } catch (Exception e) {
            System.err.println("Flow BUTTON node failed: " + e.getMessage());
        }
    }

    private void askQuestion(FlowNode node, Long organizationId, String phone) {
        try {
            JsonNode config = configOf(node);
            String question = applyVariables(
                    config.path("question").asText("Please reply"), organizationId, phone);
            whatsAppCloudApiService.sendTextMessage(organizationId, phone, question);
        } catch (Exception e) {
            System.err.println("Flow INPUT node failed: " + e.getMessage());
        }
    }

    private void runSetVariable(Flow flow, FlowNode node, Long organizationId, String phone) {
        try {
            JsonNode config = configOf(node);
            String variable = config.path("variable").asText("");
            String value = applyVariables(config.path("value").asText(""), organizationId, phone);

            FlowExecutionSession session = flowExecutionSessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                    .orElseGet(FlowExecutionSession::new);
            session.setOrganizationId(organizationId);
            session.setCustomerPhoneNumber(phone);
            session.setFlowId(flow.getId());
            putVariable(session, variable, value);
            flowExecutionSessionRepository.save(session);
        } catch (Exception e) {
            System.err.println("Flow SET_VARIABLE failed: " + e.getMessage());
        }
    }

    private boolean evaluateCondition(FlowNode node, Long organizationId, String phone) {
        try {
            JsonNode config = configOf(node);
            String variable = config.path("variable").asText("");
            String operator = config.path("operator").asText("equals");
            String expected = applyVariables(
                    config.path("value").asText(""), organizationId, phone).trim();

            FlowExecutionSession session = flowExecutionSessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                    .orElse(null);
            String raw = session == null ? null : readVariables(session).get(variable);
            boolean exists = raw != null && !raw.isBlank();
            String actual = raw == null ? "" : raw.trim();

            switch (operator) {
                case "equals":
                    return actual.equalsIgnoreCase(expected);
                case "not_equals":
                    return !actual.equalsIgnoreCase(expected);
                case "contains":
                    return actual.toLowerCase().contains(expected.toLowerCase());
                case "not_contains":
                    return !actual.toLowerCase().contains(expected.toLowerCase());
                case "greater_than":
                case "less_than":
                    try {
                        int c = new java.math.BigDecimal(actual)
                                .compareTo(new java.math.BigDecimal(expected));
                        return "greater_than".equals(operator) ? c > 0 : c < 0;
                    } catch (Exception ex) {
                        return false;
                    }
                case "exists":
                    return exists;
                case "not_exists":
                    return !exists;
                default:
                    return false;
            }
        } catch (Exception e) {
            System.err.println("Flow CONDITION failed: " + e.getMessage());
            return false;
        }
    }

    private String validateInput(FlowNode.NodeType type, String answer) {
        switch (type) {
            case PHONE_INPUT: {
                int digits = answer.replaceAll("[^0-9]", "").length();
                return digits >= 10 && digits <= 15 ? null : "Please enter a valid phone number.";
            }
            case EMAIL_INPUT:
                return answer.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                        ? null : "Please enter a valid email address.";
            case NUMBER_INPUT:
                try {
                    new java.math.BigDecimal(answer);
                    return null;
                } catch (Exception e) {
                    return "Please enter a number.";
                }
            default:
                return null;
        }
    }

    /**
     * Resumes a flow that is waiting for a BUTTON tap or an INPUT answer.
     * LIST is handled by routeListSelection.
     */
    private boolean routePendingInput(
            Long organizationId,
            String phone,
            String messageText,
            String messageType
    ) {
        if (messageText == null || messageText.isBlank()) {
            return false;
        }

        FlowExecutionSession session = flowExecutionSessionRepository
                .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                .orElse(null);
        if (session == null || session.getPendingNodeId() == null) {
            return false;
        }

        Flow flow = findPublishedFlow(session.getFlowId(), organizationId);
        FlowNode node = flow == null ? null : flowNodeRepository
                .findByIdAndFlowId(session.getPendingNodeId(), flow.getId())
                .orElse(null);

        if (node == null) {
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            return false;
        }

        FlowNode.NodeType type = node.getNodeType();
        boolean interactive = "interactive".equalsIgnoreCase(messageType);

        if (type == FlowNode.NodeType.LIST) {
            return false;
        }

        if (type == FlowNode.NodeType.BUTTON) {
            if (!interactive) {
                return false;
            }
            boolean edgeExists = flowEdgeRepository
                    .findBySourceNodeId(node.getId())
                    .stream()
                    .anyMatch(edge -> messageText.equals(edge.getSourceHandle()));
            if (!edgeExists) {
                return true;
            }
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            executeFromNode(flow, node, organizationId, phone,
                    messageText, messageType, new HashSet<>());
            return true;
        }

        if (!isInputNode(type)) {
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            return false;
        }

        if (interactive) {
            return false;
        }

        String answer = messageText.trim();

        if ("cancel".equalsIgnoreCase(answer)) {
            session.setPendingNodeId(null);
            flowExecutionSessionRepository.save(session);
            whatsAppCloudApiService.sendTextMessage(organizationId, phone, "Cancelled.");
            return true;
        }

        String error = validateInput(type, answer);
        if (error != null) {
            whatsAppCloudApiService.sendTextMessage(organizationId, phone, error);
            askQuestion(node, organizationId, phone);
            return true;
        }

        try {
            String variable = configOf(node).path("variable").asText("");
            putVariable(session, variable, answer);
        } catch (Exception e) {
            System.err.println("Could not read INPUT config: " + e.getMessage());
        }

        session.setPendingNodeId(null);
        flowExecutionSessionRepository.save(session);

        executeFromNode(flow, node, organizationId, phone,
                messageText, messageType, new HashSet<>());
        return true;
    }
}
