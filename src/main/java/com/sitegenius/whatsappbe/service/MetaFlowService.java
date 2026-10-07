package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowEdge;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.repository.FlowEdgeRepository;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetaFlowService {

    private final FlowRepository flowRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final FlowEdgeRepository flowEdgeRepository;

    private final ObjectMapper objectMapper;
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    private final RestClient restClient = RestClient.builder().build();

    @Value("${whatsapp.meta.graph-api-url:https://graph.facebook.com}")
    private String graphApiUrl;

    @Value("${whatsapp.meta.graph-api-version:v23.0}")
    private String graphApiVersion;

    @Value("${whatsapp.meta.waba-id:}")
    private String wabaId;

    /*
     * IMPORTANT:
     * This token must be a Meta WhatsApp Business Management
     * access token with required WABA permissions.
     *
     * Do NOT use the catalog token here unless it has the
     * required WABA management permissions.
     */
    @Value("${META_WHATSAPP_ACCESS_TOKEN:}")
    private String accessToken;


    // =========================================================
    // COMMON
    // =========================================================

    private String base() {
        return graphApiUrl + "/" + graphApiVersion;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Meta returned an empty response");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("Invalid Meta response: " + raw, e);
        }
    }

    private Flow getOwnedFlow(
            Long flowId,
            Authentication authentication
    ) {

        Long organizationId =
                currentOrganizationId(authentication);

        return flowRepository
                .findByIdAndOrganizationId(
                        flowId,
                        organizationId
                )
                .orElseThrow(
                        () -> new RuntimeException(
                                "Flow not found"
                        )
                );
    }

    public JsonNode getMetaFlowJson(
            Long flowId,
            Authentication authentication
    )

    {
        System.out.println(">>> NEW MetaFlowService.getMetaFlowJson called");
        getOwnedFlow(flowId, authentication);

        return buildMetaFlowJson(flowId);
    }

    /*
     * Same organization extraction pattern used by
     * FlowService / JWT authentication details.
     */
    private Long currentOrganizationId(
            Authentication authentication
    ) {

        if (authentication == null) {
            throw new RuntimeException(
                    "Authentication required"
            );
        }

        Object details =
                authentication.getDetails();

        if (details == null) {
            throw new RuntimeException(
                    "Organization information not found"
            );
        }

        try {

            var method =
                    details
                            .getClass()
                            .getMethod(
                                    "getOrganizationId"
                            );

            Object value =
                    method.invoke(details);

            if (value == null) {
                throw new RuntimeException(
                        "Organization information not found"
                );
            }

            if (value instanceof Number number) {
                return number.longValue();
            }

            return Long.parseLong(
                    value.toString()
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to determine organization",
                    e
            );
        }
    }


    // =========================================================
    // CREATE + UPLOAD
    // =========================================================

    public JsonNode syncMeta(
            Long flowId,
            Authentication authentication
    ) {

        Flow flow =
                getOwnedFlow(
                        flowId,
                        authentication
                );

        validateConfiguration();


        // -----------------------------------------------------
        // CREATE META FLOW IF NOT CREATED
        // -----------------------------------------------------

        if (
                flow.getMetaFlowId() == null
                        ||
                        flow.getMetaFlowId().isBlank()
        ) {

            String createdResponse =
                    restClient
                            .post()
                            .uri(
                                    base()
                                            + "/"
                                            + wabaId
                                            + "/flows"
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    Map.of(
                                            "name",
                                            flow.getName(),
                                            "categories",
                                            List.of("OTHER")
                                    )
                            )
                            .retrieve()
                            .body(String.class);

            JsonNode created;

            try {
                created = objectMapper.readTree(createdResponse);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Invalid Meta response: " + createdResponse,
                        e
                );
            }

            if (
                    created == null
                            ||
                            !created.has("id")
            ) {

                throw new RuntimeException(
                        "Meta Flow creation failed"
                );
            }

            flow.setMetaFlowId(
                    created
                            .get("id")
                            .asText()
            );

            flow.setMetaFlowStatus(
                    created.has("status")
                            ? created
                            .get("status")
                            .asText()
                            : "DRAFT"
            );

            flowRepository.save(flow);
        }


        // -----------------------------------------------------
        // BUILD FLOW JSON
        // -----------------------------------------------------

        String json;

        try {

            JsonNode flowJson =
                    buildMetaFlowJson(flowId);

            json =
                    objectMapper
                            .writeValueAsString(
                                    flowJson
                            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Flow JSON build failed",
                    e
            );
        }


        // -----------------------------------------------------
        // UPLOAD JSON TO META
        // -----------------------------------------------------

        ByteArrayResource file =
                new ByteArrayResource(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                ) {

                    @Override
                    public String getFilename() {
                        return "flow.json";
                    }
                };


        MultiValueMap<String, Object> parts =
                new LinkedMultiValueMap<>();

        parts.add(
                "file",
                file
        );

        parts.add(
                "name",
                "flow.json"
        );

        parts.add(
                "asset_type",
                "FLOW_JSON"
        );


        String rawResult =
                restClient
                        .post()
                        .uri(base() + "/" + flow.getMetaFlowId() + "/assets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(parts)
                        .retrieve()
                        .body(String.class);

        JsonNode result = parseJson(rawResult);
        // -----------------------------------------------------
        // SAVE RESULT
        // -----------------------------------------------------

        /*
         * These two fields must exist in Flow entity:
         *
         * private String flowJson;
         * private String metaValidationErrors;
         */

        flow.setFlowJson(json);

        if (
                result != null
                        &&
                        result.has("validation_errors")
                        &&
                        result.get("validation_errors").size() > 0
        ) {

            flow.setMetaValidationErrors(
                    result
                            .get("validation_errors")
                            .toString()
            );

        } else {

            flow.setMetaValidationErrors(null);
        }

        flowRepository.save(flow);

        return result;
    }


    // =========================================================
    // PUBLISH
    // =========================================================

    public void publishMeta(
            Long flowId,
            Authentication authentication
    ) {

        Flow flow =
                getOwnedFlow(
                        flowId,
                        authentication
                );

        validateConfiguration();

        /*
         * If this platform flow was already published,
         * never edit the old Meta Flow.
         *
         * Delete the old Meta Flow and create a fresh
         * Meta Flow for the new version.
         */
        if (
                Flow.FlowStatus.PUBLISHED.equals(flow.getStatus())
                        &&
                        flow.getMetaFlowId() != null
                        &&
                        !flow.getMetaFlowId().isBlank()
        ) {

            String oldMetaFlowId =
                    flow.getMetaFlowId();

            // ---------------------------------------------
            // DELETE OLD META FLOW
            // ---------------------------------------------

            restClient
                    .delete()
                    .uri(
                            base()
                                    + "/"
                                    + oldMetaFlowId
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .retrieve()
                    .toBodilessEntity();

            // Clear old Meta reference
            flow.setMetaFlowId(null);
            flow.setMetaFlowStatus("DELETED");
            flow.setMetaValidationErrors(null);

            // Mark platform flow as draft temporarily
            flow.setStatus(
                    Flow.FlowStatus.DRAFT
            );

            flowRepository.save(flow);
        }

        // ---------------------------------------------
        // CREATE NEW META FLOW + UPLOAD JSON
        // ---------------------------------------------

        syncMeta(
                flowId,
                authentication
        );

        // Reload latest flow because syncMeta() saves it
        flow =
                getOwnedFlow(
                        flowId,
                        authentication
                );

        // ---------------------------------------------
        // VALIDATION
        // ---------------------------------------------

        if (
                flow.getMetaFlowId() == null
                        ||
                        flow.getMetaFlowId().isBlank()
        ) {

            throw new RuntimeException(
                    "New Meta Flow was not created."
            );
        }

        if (
                flow.getMetaValidationErrors() != null
                        &&
                        !flow
                                .getMetaValidationErrors()
                                .isBlank()
        ) {

            throw new RuntimeException(
                    "Fix Meta validation errors before publishing: "
                            + flow.getMetaValidationErrors()
            );
        }

        // ---------------------------------------------
        // PUBLISH NEW META FLOW
        // ---------------------------------------------

        restClient
                .post()
                .uri(
                        base()
                                + "/"
                                + flow.getMetaFlowId()
                                + "/publish"
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                )
                .retrieve()
                .toBodilessEntity();

        // ---------------------------------------------
        // UPDATE PLATFORM STATUS
        // ---------------------------------------------

        flow.setStatus(
                Flow.FlowStatus.PUBLISHED
        );

        flow.setMetaFlowStatus(
                "PUBLISHED"
        );

        flowRepository.save(flow);
    }




    // =========================================================
// TEST / SEND FLOW
// =========================================================

    public String sendTestFlow(
            Long flowId,
            String customerPhoneNumber,
            Authentication authentication
    ) {

        Flow flow = getOwnedFlow(flowId, authentication);

        if (flow.getMetaFlowId() == null
                || flow.getMetaFlowId().isBlank()) {

            throw new RuntimeException(
                    "Meta Flow ID not found. Sync the Flow first."
            );
        }

        if (customerPhoneNumber == null
                || customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        // Build the same Meta JSON used for sync
        JsonNode metaJson = buildMetaFlowJson(flowId);

        JsonNode screens = metaJson.path("screens");

        if (!screens.isArray() || screens.isEmpty()) {
            throw new RuntimeException(
                    "Flow has no screens"
            );
        }

        // First screen is the entry screen
        JsonNode firstScreen = screens.get(0);

        String screenId =
                firstScreen.path("id").asText();

        if (screenId.isBlank()) {
            throw new RuntimeException(
                    "First screen ID not found"
            );
        }

        // Get CTA dynamically from first screen footer
        String flowCta = "Continue";

        JsonNode children =
                firstScreen
                        .path("layout")
                        .path("children");

        if (children.isArray()) {

            for (JsonNode child : children) {

                if ("Footer".equals(
                        child.path("type").asText()
                )) {

                    String label =
                            child.path("label").asText();

                    if (!label.isBlank()) {
                        flowCta = label;
                    }

                    break;
                }
            }
        }

        // Generate a unique token for this Flow session
        String flowToken =
                "test-" + flow.getMetaFlowId()
                        + "-" + System.currentTimeMillis();

        return whatsAppCloudApiService.sendFlowMessage(
                flow.getOrganizationId(),
                customerPhoneNumber,
                flow.getMetaFlowId(),
                flowToken,
                flowCta,
                screenId
        );
    }

    // =========================================================
    // BUILD META FLOW JSON
    // =========================================================

    private JsonNode buildMetaFlowJson(
            Long flowId
    ) {

        List<FlowNode> nodes =
                flowNodeRepository
                        .findByFlowIdOrderByIdAsc(
                                flowId
                        );

        List<FlowEdge> edges =
                flowEdgeRepository
                        .findByFlowIdOrderByIdAsc(
                                flowId
                        );


        FlowNode start =
                nodes.stream()
                        .filter(
                                n ->
                                        n.getNodeType()
                                                ==
                                                FlowNode.NodeType.START
                        )
                        .findFirst()
                        .orElse(null);


        List<FlowNode> screenNodes =
                new ArrayList<>(
                        nodes.stream()
                                .filter(
                                        n ->
                                                n.getNodeType()
                                                        !=
                                                        FlowNode.NodeType.START
                                )
                                .toList()
                );


        if (screenNodes.isEmpty()) {

            throw new RuntimeException(
                    "Flow has no screens"
            );
        }


        // -----------------------------------------------------
        // FIND ENTRY SCREEN
        // -----------------------------------------------------

        if (start != null) {

            edges.stream()
                    .filter(
                            e ->
                                    e.getSourceNodeId()
                                            .equals(
                                                    start.getId()
                                            )
                    )
                    .findFirst()
                    .flatMap(
                            e ->
                                    screenNodes
                                            .stream()
                                            .filter(
                                                    n ->
                                                            n.getId()
                                                                    .equals(
                                                                            e.getTargetNodeId()
                                                                    )
                                            )
                                            .findFirst()
                    )
                    .ifPresent(
                            entry -> {

                                screenNodes.remove(
                                        entry
                                );

                                screenNodes.add(
                                        0,
                                        entry
                                );
                            }
                    );
        }


        ObjectNode root =
                objectMapper.createObjectNode();

        root.put(
                "version",
                "7.2"
        );


        ArrayNode screens =
                root.putArray(
                        "screens"
                );


        for (FlowNode node : screenNodes) {

            ObjectNode screen =
                    screens.addObject();


            screen.put(
                    "id",
                    toScreenId(
                            node.getNodeKey()
                    )
            );


            screen.put(
                    "title",
                    node.getName()
            );


            ObjectNode layout =
                    screen.putObject(
                            "layout"
                    );

            layout.put(
                    "type",
                    "SingleColumnLayout"
            );


            ArrayNode children =
                    layout.putArray(
                            "children"
                    );


            addNodeComponent(
                    children,
                    node,
                    edges,
                    screenNodes
            );


            // -------------------------------------------------
            // FIND NEXT NODE
            // -------------------------------------------------

            List<FlowEdge> outgoing =
                    edges.stream()
                            .filter(
                                    e ->
                                            e.getSourceNodeId()
                                                    .equals(
                                                            node.getId()
                                                    )
                            )
                            .toList();


            FlowEdge defaultEdge =
                    outgoing.stream()
                            .filter(
                                    e ->
                                            e.getSourceHandle()
                                                    == null
                            )
                            .findFirst()
                            .orElse(
                                    outgoing.isEmpty()
                                            ? null
                                            : outgoing.get(0)
                            );


            FlowNode next =
                    defaultEdge == null
                            ? null
                            : screenNodes.stream()
                            .filter(
                                    n ->
                                            n.getId()
                                                    .equals(
                                                            defaultEdge
                                                                    .getTargetNodeId()
                                                    )
                            )
                            .findFirst()
                            .orElse(null);


            // -------------------------------------------------
            // SINGLE FOOTER
            // -------------------------------------------------


            if (node.getNodeType() != FlowNode.NodeType.BUTTON) {

                ObjectNode footer =
                        children.addObject();

                footer.put(
                        "type",
                        "Footer"
                );

                ObjectNode action =
                        footer.putObject(
                                "on-click-action"
                        );

                if (next != null) {

                    footer.put(
                            "label",
                            "Continue"
                    );

                    action.put(
                            "name",
                            "navigate"
                    );

                    ObjectNode nextObject =
                            action.putObject(
                                    "next"
                            );

                    nextObject.put(
                            "type",
                            "screen"
                    );

                    nextObject.put(
                            "name",

                            toScreenId(
                                    next.getNodeKey()
                            )
                    );

                    action.putObject(
                            "payload"
                    );

                } else {

                    screen.put(
                            "terminal",
                            true
                    );

                    screen.put(
                            "success",
                            true
                    );

                    footer.put(
                            "label",
                            "Submit"
                    );

                    action.put(
                            "name",
                            "complete"
                    );

                    action.putObject(
                            "payload"
                    );
                }
            }
        }
        return root;
    }

    // =========================================================
    // NODE → META COMPONENT
    // =========================================================

    private void addNodeComponent(
            ArrayNode children,
            FlowNode node,
            List<FlowEdge> edges,
            List<FlowNode> screenNodes
    ) {

        try {

            JsonNode config =
                    node.getConfig() == null
                            || node.getConfig().isBlank()
                            ? objectMapper.createObjectNode()
                            : objectMapper.readTree(node.getConfig());


            switch (node.getNodeType()) {

                // =====================================================
                // MESSAGE
                // =====================================================

                case MESSAGE -> {

                    ObjectNode text =
                            children.addObject();

                    text.put(
                            "type",
                            "TextBody"
                    );

                    text.put(
                            "text",
                            config
                                    .path("text")
                                    .asText(node.getName())
                    );
                }

                case IMAGE -> {

                    String imageUrl = config.path("imageUrl").asText();

                    if (imageUrl.isBlank()) {
                        throw new RuntimeException(
                                "Image URL is required in node: " + node.getNodeKey()
                        );
                    }

                    ObjectNode image = children.addObject();
                    image.put("type", "Image");
                    image.put("src", imageUrl);

                    String description = config.path("description").asText();

                    if (!description.isBlank()) {
                        ObjectNode text = children.addObject();
                        text.put("type", "TextBody");
                        text.put("text", description);
                    }
                }

                // =====================================================
                // BUTTON
                // =====================================================

                case BUTTON -> {

                    ObjectNode nav = children.addObject();
                    nav.put("type", "NavigationList");
                    nav.put("name", "nav_" + toScreenId(node.getNodeKey()).toLowerCase());
                    nav.put("label", config.path("text").asText(node.getName()));
                    ArrayNode items = nav.putArray("list-items");

                    JsonNode buttons = config.path("buttons");

                    if (!buttons.isArray() || buttons.isEmpty()) {
                        throw new RuntimeException(
                                "BUTTON node must contain at least one button: " + node.getNodeKey());
                    }



                    for (JsonNode button : buttons) {

                        String buttonId = button.path("id").asText();
                        String label = button.path("label").asText();

                        if (buttonId.isBlank()) {
                            throw new RuntimeException("Button id is required in node: " + node.getNodeKey());
                        }
                        if (label.isBlank()) {
                            throw new RuntimeException("Button label is required in node: " + node.getNodeKey());
                        }

                        FlowEdge matchingEdge = edges.stream()
                                .filter(e -> e.getSourceNodeId().equals(node.getId()))
                                .filter(e -> buttonId.equals(e.getSourceHandle()))
                                .findFirst()
                                .orElse(null);

                        if (matchingEdge == null) {
                            throw new RuntimeException("No edge configured for button '" + buttonId
                                    + "' in node '" + node.getNodeKey() + "'");
                        }

                        FlowNode targetNode = screenNodes.stream()
                                .filter(n -> n.getId().equals(matchingEdge.getTargetNodeId()))
                                .findFirst()
                                .orElse(null);

                        if (targetNode == null) {
                            throw new RuntimeException("Target node not found for button '" + buttonId
                                    + "' in node '" + node.getNodeKey() + "'");
                        }

                        ObjectNode item = items.addObject();
                        item.put("id", buttonId);
                        item.putObject("main-content").put("title", label);

                        ObjectNode action = item.putObject("on-click-action");
                        action.put("name", "navigate");
                        ObjectNode next = action.putObject("next");
                        next.put("type", "screen");
                        next.put("name", toScreenId(targetNode.getNodeKey()));
                        action.putObject("payload");
                    }
                }


                // =====================================================
                // DEFAULT
                // =====================================================

                default -> {

                    ObjectNode text =
                            children.addObject();

                    text.put(
                            "type",
                            "TextBody"
                    );

                    text.put(
                            "text",
                            node.getName()
                    );
                }
            }


        } catch (Exception e) {

            throw new RuntimeException(
                    "Invalid node config for: "
                            + node.getNodeKey(),
                    e
            );
        }
    }


    // =========================================================
    // SCREEN ID
    // =========================================================

    private String toScreenId(
            String nodeKey
    ) {

        StringBuilder result =
                new StringBuilder();


        for (
                char c :
                nodeKey.toCharArray()
        ) {

            if (
                    c >= '0'
                            &&
                            c <= '9'
            ) {

                result.append(
                        (char)
                                ('A' + (c - '0'))
                );

            } else if (
                    (c >= 'a' && c <= 'z')
                            ||
                            (c >= 'A' && c <= 'Z')
            ) {

                result.append(
                        Character.toUpperCase(c)
                );

            } else {

                result.append('_');
            }
        }


        String id =
                result.toString();


        if (id.isBlank()) {

            throw new RuntimeException(
                    "Invalid screen id for node: "
                            + nodeKey
            );
        }


        /*
         * Do not silently truncate because two node keys
         * could become the same ID.
         */

        if (id.length() > 20) {

            throw new RuntimeException(
                    "Node key creates a screen ID longer than 20 characters: "
                            + nodeKey
            );
        }


        return id;
    }


    // =========================================================
    // CONFIG VALIDATION
    // =========================================================

    private void validateConfiguration() {

        if (
                wabaId == null
                        ||
                        wabaId.isBlank()
        ) {

            throw new RuntimeException(
                    "META WABA ID is not configured"
            );
        }


        if (
                accessToken == null
                        ||
                        accessToken.isBlank()
        ) {

            throw new RuntimeException(
                    "META_WHATSAPP_ACCESS_TOKEN is not configured"
            );
        }
    }
}