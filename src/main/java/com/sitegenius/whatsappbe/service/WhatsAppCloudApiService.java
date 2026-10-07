package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.repository.WhatsAppNumberRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppCloudApiService {

    private final WhatsAppNumberRepository whatsAppNumberRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${whatsapp.meta.graph-api-url:https://graph.facebook.com}")
    private String graphApiUrl;

    @Value("${whatsapp.meta.graph-api-version:v23.0}")
    private String graphApiVersion;

    @Value("${whatsapp.meta.catalog-id:}")
    private String catalogId;



    @Value("${whatsapp.meta.product-base-url:https://example.com/products}")
    private String productBaseUrl;

    @Value("${whatsapp.meta.catalog-access-token:}")
    private String catalogAccessToken;

    public WhatsAppCloudApiService(
            WhatsAppNumberRepository whatsAppNumberRepository,
            ObjectMapper objectMapper) {

        this.whatsAppNumberRepository = whatsAppNumberRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }




    // =========================================================
    // SEND TEXT MESSAGE
    // =========================================================

    public String sendTextMessage(
            Long organizationId,
            String customerPhoneNumber,
            String messageText) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (messageText == null ||
                messageText.isBlank()) {

            throw new RuntimeException(
                    "Message text is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        Map<String, Object> text =
                new HashMap<>();

        text.put(
                "preview_url",
                false
        );

        text.put(
                "body",
                messageText
        );

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "text"
        );

        requestBody.put(
                "text",
                text
        );

        try {

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    // =========================================================
// UPLOAD MEDIA TO WHATSAPP
// =========================================================

    public String uploadMedia(
            Long organizationId,
            byte[] fileBytes,
            String fileName,
            String mimeType) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (fileBytes == null ||
                fileBytes.length == 0) {

            throw new RuntimeException(
                    "File is required"
            );
        }

        if (mimeType == null ||
                mimeType.isBlank()) {

            throw new RuntimeException(
                    "File MIME type is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/media";

        try {

            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(
                            fileBytes
                    ) {
                        @Override
                        public String getFilename() {
                            return fileName;
                        }
                    };

            MultiValueMap<String, Object> form =
                    new LinkedMultiValueMap<>();

            form.add(
                    "messaging_product",
                    "whatsapp"
            );

            form.add(
                    "file",
                    resource
            );

            form.add(
                    "type",
                    mimeType
            );

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.MULTIPART_FORM_DATA
                            )
                            .body(form)
                            .retrieve()
                            .body(String.class);

            if (response == null ||
                    response.isBlank()) {

                throw new RuntimeException(
                        "Meta returned an empty media response"
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            String mediaId =
                    root.path("id")
                            .asText(null);

            if (mediaId == null ||
                    mediaId.isBlank()) {

                throw new RuntimeException(
                        "WhatsApp media ID was not returned"
                );
            }

            return mediaId;

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to upload media to WhatsApp: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // =========================================================
    // CREATE PRODUCT IN META CATALOG
    // =========================================================


    public String createMetaCatalogProduct(Product product) {

        if (product == null) {
            throw new RuntimeException("Product is required");
        }
        if (catalogId == null || catalogId.isBlank()) {
            throw new RuntimeException("Meta Catalog ID is missing");
        }
        if (catalogAccessToken == null || catalogAccessToken.isBlank()) {
            throw new RuntimeException("Meta Catalog access token is missing");
        }
        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new RuntimeException("Product SKU is required for Meta Catalog");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new RuntimeException("Product name is required for Meta Catalog");
        }
        if (product.getImageUrl() == null || product.getImageUrl().isBlank()) {
            throw new RuntimeException("Product image URL is required for Meta Catalog");
        }
        if (product.getPrice() == null) {
            throw new RuntimeException("Product price is required for Meta Catalog");
        }

        String url = graphApiUrl + "/" + graphApiVersion + "/" + catalogId + "/products";

        String currency = (product.getCurrency() == null || product.getCurrency().isBlank())
                ? "INR"
                : product.getCurrency().toUpperCase();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("retailer_id", product.getSku());
        form.add("name", product.getName());
        form.add("description",
                product.getDescription() == null ? "" : product.getDescription());
        form.add("price", String.valueOf(product.getPrice().movePointRight(2).longValue()));
        form.add("currency", currency);
        form.add("image_url", product.getImageUrl());
        form.add("url", productBaseUrl + "/" + product.getId());
        form.add("availability", "in stock");
        form.add("condition", "new");

        try {
            String response = restClient
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + catalogAccessToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            return extractMetaProductId(response);

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to create Meta Catalog product: " + exception.getMessage(),
                    exception);
        }
    }


    // =========================================================
// SEND IMAGE MESSAGE
// =========================================================

    public String sendImageMessage(
            Long organizationId,
            String customerPhoneNumber,
            String mediaId,
            String caption) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (mediaId == null ||
                mediaId.isBlank()) {

            throw new RuntimeException(
                    "WhatsApp media ID is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        // =====================================================
        // IMAGE OBJECT
        // =====================================================

        Map<String, Object> image =
                new HashMap<>();

        image.put(
                "id",
                mediaId
        );

        if (caption != null &&
                !caption.isBlank()) {

            image.put(
                    "caption",
                    caption
            );
        }

        // =====================================================
        // REQUEST BODY
        // =====================================================

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "image"
        );

        requestBody.put(
                "image",
                image
        );

        try {

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp image message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // =========================================================
// SEND DOCUMENT MESSAGE
// =========================================================

    public String sendDocumentMessage(
            Long organizationId,
            String customerPhoneNumber,
            String mediaId,
            String fileName,
            String caption) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (mediaId == null ||
                mediaId.isBlank()) {

            throw new RuntimeException(
                    "WhatsApp media ID is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        // =====================================================
        // DOCUMENT OBJECT
        // =====================================================

        Map<String, Object> document =
                new HashMap<>();

        document.put(
                "id",
                mediaId
        );

        if (fileName != null &&
                !fileName.isBlank()) {

            document.put(
                    "filename",
                    fileName
            );
        }

        if (caption != null &&
                !caption.isBlank()) {

            document.put(
                    "caption",
                    caption
            );
        }

        // =====================================================
        // REQUEST BODY
        // =====================================================

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "document"
        );

        requestBody.put(
                "document",
                document
        );

        try {

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp document message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
// =========================================================
// SEND VIDEO MESSAGE
// =========================================================

    public String sendVideoMessage(
            Long organizationId,
            String customerPhoneNumber,
            String mediaId,
            String caption) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (mediaId == null ||
                mediaId.isBlank()) {

            throw new RuntimeException(
                    "WhatsApp media ID is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        Map<String, Object> video =
                new HashMap<>();

        video.put(
                "id",
                mediaId
        );

        if (caption != null &&
                !caption.isBlank()) {

            video.put(
                    "caption",
                    caption
            );
        }

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "video"
        );

        requestBody.put(
                "video",
                video
        );

        try {

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp video message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }


    // =========================================================
// SEND AUDIO MESSAGE
// =========================================================

    public String sendAudioMessage(
            Long organizationId,
            String customerPhoneNumber,
            String mediaId) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (mediaId == null ||
                mediaId.isBlank()) {

            throw new RuntimeException(
                    "WhatsApp media ID is required"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        Map<String, Object> audio =
                new HashMap<>();

        audio.put(
                "id",
                mediaId
        );

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "audio"
        );

        requestBody.put(
                "audio",
                audio
        );

        try {

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp audio message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // =========================================================
// SEND PRODUCT MESSAGE ON WHATSAPP
// =========================================================

    public String sendProductMessage(
            Long organizationId,
            String customerPhoneNumber,
            Product product) {

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required"
            );
        }

        if (product == null) {
            throw new RuntimeException(
                    "Product is required"
            );
        }

        if (catalogId == null ||
                catalogId.isBlank()) {

            throw new RuntimeException(
                    "Meta Catalog ID is missing"
            );
        }

        if (product.getMetaRetailerId() == null ||
                product.getMetaRetailerId().isBlank()) {

            throw new RuntimeException(
                    "Product Meta Retailer ID is missing"
            );
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        // =====================================================
        // PRODUCT INTERACTIVE
        // =====================================================

        Map<String, Object> action =
                new HashMap<>();

        action.put(
                "catalog_id",
                catalogId
        );

        action.put(
                "product_retailer_id",
                product.getMetaRetailerId()
        );

        Map<String, Object> interactive =
                new HashMap<>();

        interactive.put(
                "type",
                "product"
        );

        interactive.put(
                "action",
                action
        );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "text",
                product.getName()
        );

        interactive.put(
                "body",
                body
        );

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "interactive"
        );

        requestBody.put(
                "interactive",
                interactive
        );

        try {

            System.out.println("=== CATALOG SEND DEBUG ===");
            System.out.println("Graph URL = " + url);
            System.out.println("Catalog ID = " + catalogId);
            System.out.println("Retailer ID = " + product.getMetaRetailerId());
            System.out.println("Phone Number ID = " + whatsAppNumber.getPhoneNumberId());
            System.out.println("To = " + normalizePhoneNumber(customerPhoneNumber));

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp product message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }



    // =========================================================
// SEND WHATSAPP FLOW MESSAGE
// =========================================================

    public String sendFlowMessage(
            Long organizationId,
            String customerPhoneNumber,
            String flowId,
            String flowToken,
            String flowCta,
            String screenId) {

        if (organizationId == null) {
            throw new RuntimeException("Organization is required");
        }

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number is required");
        }

        if (flowId == null || flowId.isBlank()) {
            throw new RuntimeException("Flow ID is required");
        }

        if (flowToken == null || flowToken.isBlank()) {
            throw new RuntimeException("Flow token is required");
        }

        if (flowCta == null || flowCta.isBlank()) {
            throw new RuntimeException("Flow CTA is required");
        }

        if (screenId == null || screenId.isBlank()) {
            throw new RuntimeException("Flow screen ID is required");
        }

        WhatsAppNumber whatsAppNumber =
                whatsAppNumberRepository
                        .findFirstByOrganizationIdAndStatus(
                                organizationId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "No active WhatsApp number found"
                                ));

        validateWhatsAppCredentials(
                whatsAppNumber
        );

        String url =
                graphApiUrl
                        + "/"
                        + graphApiVersion
                        + "/"
                        + whatsAppNumber.getPhoneNumberId()
                        + "/messages";

        // =====================================================
        // FLOW ACTION PAYLOAD
        // =====================================================

        Map<String, Object> flowActionPayload =
                new HashMap<>();

        flowActionPayload.put(
                "screen",
                screenId
        );

        // =====================================================
        // FLOW PARAMETERS
        // =====================================================

        Map<String, Object> parameters =
                new HashMap<>();

        parameters.put(
                "flow_message_version",
                "3"
        );

        parameters.put(
                "flow_token",
                flowToken
        );

        parameters.put(
                "flow_id",
                flowId
        );

        parameters.put(
                "flow_cta",
                flowCta
        );

        parameters.put(
                "flow_action",
                "navigate"
        );

        parameters.put(
                "flow_action_payload",
                flowActionPayload
        );

        // =====================================================
        // FLOW ACTION
        // =====================================================

        Map<String, Object> action =
                new HashMap<>();

        action.put(
                "name",
                "flow"
        );

        action.put(
                "parameters",
                parameters
        );

        // =====================================================
        // INTERACTIVE
        // =====================================================

        Map<String, Object> interactive =
                new HashMap<>();

        interactive.put(
                "type",
                "flow"
        );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "text",
                "Please continue by opening the form."
        );

        interactive.put(
                "body",
                body
        );

        interactive.put(
                "action",
                action
        );

        // =====================================================
        // FINAL WHATSAPP REQUEST BODY
        // =====================================================

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "recipient_type",
                "individual"
        );

        requestBody.put(
                "to",
                normalizePhoneNumber(
                        customerPhoneNumber
                )
        );

        requestBody.put(
                "type",
                "interactive"
        );

        requestBody.put(
                "interactive",
                interactive
        );

        try {

            System.out.println("=== FLOW SEND DEBUG ===");
            System.out.println("Graph URL = " + url);
            System.out.println("Flow ID = " + flowId);
            System.out.println("Flow CTA = " + flowCta);
            System.out.println("Screen ID = " + screenId);
            System.out.println(
                    "To = "
                            + normalizePhoneNumber(
                            customerPhoneNumber
                    )
            );

            String response =
                    restClient
                            .post()
                            .uri(url)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer "
                                            + whatsAppNumber
                                            .getAccessToken()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);

            return extractWhatsAppMessageId(
                    response
            );

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to send WhatsApp Flow message: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // =========================================================
    // VALIDATE WHATSAPP CREDENTIALS
    // =========================================================

    private void validateWhatsAppCredentials(
            WhatsAppNumber whatsAppNumber) {

        if (whatsAppNumber.getPhoneNumberId() == null ||
                whatsAppNumber.getPhoneNumberId().isBlank()) {

            throw new RuntimeException(
                    "WhatsApp Phone Number ID is missing"
            );
        }

        if (whatsAppNumber.getAccessToken() == null ||
                whatsAppNumber.getAccessToken().isBlank()) {

            throw new RuntimeException(
                    "WhatsApp access token is missing"
            );
        }
    }

    // =========================================================
    // NORMALIZE PHONE NUMBER
    // =========================================================

    private String normalizePhoneNumber(
            String phoneNumber) {

        return phoneNumber
                .replaceAll("[^0-9]", "");
    }

    // =========================================================
    // EXTRACT WHATSAPP MESSAGE ID
    // =========================================================

    private String extractWhatsAppMessageId(
            String response) {

        if (response == null ||
                response.isBlank()) {

            return null;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            JsonNode messages =
                    root.path("messages");

            if (messages.isArray() &&
                    !messages.isEmpty()) {

                return messages
                        .get(0)
                        .path("id")
                        .asText(null);
            }

        } catch (Exception ignored) {
            // Response ID extraction failed.
        }

        return null;
    }

    // =========================================================
    // EXTRACT META PRODUCT ID
    // =========================================================

    private String extractMetaProductId(
            String response) {

        if (response == null ||
                response.isBlank()) {

            throw new RuntimeException(
                    "Meta returned an empty response"
            );
        }

        try {

            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            String id =
                    root.path("id")
                            .asText(null);

            if (id == null ||
                    id.isBlank()) {

                throw new RuntimeException(
                        "Meta Product ID was not returned"
                );
            }

            return id;

        } catch (RuntimeException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new RuntimeException(
                    "Failed to parse Meta Catalog response",
                    exception
            );
        }
    }
}