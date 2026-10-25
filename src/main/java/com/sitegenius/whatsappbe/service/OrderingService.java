package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Contact;
import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowExecutionSession;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.OrderItem;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.repository.FlowExecutionSessionRepository;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import com.sitegenius.whatsappbe.repository.ProductRepository;
import com.sitegenius.whatsappbe.repository.WhatsAppNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic ordering journey for any organization:
 * categories > products > cart > summary > order.
 * Data always comes live from the organization's own catalogue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderingService {

    private static final String PREFIX = "ord:";
    private static final int ROW_LIMIT = 10;
    private static final int MAX_QTY = 20;
    private static final int GALLERY_LIMIT = 8;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final FlowExecutionSessionRepository sessionRepository;
    private final WhatsAppNumberRepository whatsAppNumberRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().build();

    @Value("${whatsapp.meta.graph-api-url:https://graph.facebook.com}")
    private String graphApiUrl;

    @Value("${whatsapp.meta.graph-api-version:v23.0}")
    private String graphApiVersion;

    private record Line(Product product, int qty, BigDecimal total) {
    }

    // =========================================================
    // ENTRY 1: called by the flow engine when an ordering node is reached
    // =========================================================

    public void start(Flow flow, FlowNode node, Long organizationId, String phone) {
        try {
            FlowExecutionSession session = sessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                    .orElseGet(FlowExecutionSession::new);

            session.setOrganizationId(organizationId);
            session.setCustomerPhoneNumber(phone);
            session.setFlowId(flow.getId());
            session.setPendingNodeId(null);
            session.setStateJson(node.getConfig());
            sessionRepository.save(session);

            sendCategories(organizationId, phone, readConfig(session));
        } catch (Exception e) {
            log.error("Ordering start failed: {}", e.getMessage());
        }
    }

    // =========================================================
    // ENTRY 2: called by the webhook for every incoming message
    // returns true when the message was consumed by ordering
    // =========================================================

    public boolean handle(
            Long organizationId,
            Contact contact,
            Conversation conversation,
            String phone,
            String input
    ) {
        if (input == null || !input.startsWith(PREFIX)) {
            return false;
        }

        try {
            FlowExecutionSession session = sessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                    .orElse(null);

            if (session == null) {
                whatsAppCloudApiService.sendTextMessage(
                        organizationId, phone, "Session expired. Send hi to start again.");
                return true;
            }

            String[] parts = input.split(":");
            String action = parts.length > 1 ? parts[1] : "";
            Long id = parts.length > 2 ? parseLong(parts[2]) : null;
            JsonNode config = readConfig(session);

            switch (action) {
                case "menu" -> sendCategories(organizationId, phone, config);
                case "cat" -> sendProducts(organizationId, phone, config, id == null ? 0L : id);
                case "p" -> sendProductDetail(organizationId, phone, id);
                case "add" -> addToCart(organizationId, phone, session, id);
                case "checkout" -> sendSummary(organizationId, phone, session, config);
                case "confirm" -> confirmOrder(organizationId, contact, conversation, phone, session, config);
                case "cancel" -> cancelOrder(organizationId, phone, session);
                default -> sendCategories(organizationId, phone, config);
            }
        } catch (Exception e) {
            log.error("Ordering step failed: {}", e.getMessage());
            try {
                whatsAppCloudApiService.sendTextMessage(
                        organizationId, phone, "Something went wrong. Send hi to start again.");
            } catch (Exception ignored) {
                // nothing more we can do
            }
        }
        return true;
    }

    // =========================================================
    // STEPS
    // =========================================================

    private void sendCategories(Long orgId, String phone, JsonNode config) {
        List<Product> products = productsFor(orgId, config);

        if (products.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "The menu is not available right now.");
            return;
        }

        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Integer> counts = new HashMap<>();

        for (Product p : products) {
            Long cid = catIdOf(p);
            names.putIfAbsent(cid, cid == 0L ? "Other" : p.getCategory().getName());
            counts.merge(cid, 1, Integer::sum);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Map.Entry<Long, String> e : names.entrySet()) {
            if (rows.size() >= ROW_LIMIT) {
                break;
            }
            Map<String, String> row = new HashMap<>();
            row.put("id", PREFIX + "cat:" + e.getKey());
            row.put("title", cut(e.getValue(), 24));
            row.put("description", counts.get(e.getKey()) + " items");
            rows.add(row);
        }

        String message = config.path("message").asText("");
        if (message.isBlank()) {
            message = "Choose a category";
        }

        whatsAppCloudApiService.sendListMessage(orgId, phone, message, "Categories", "Menu", rows);
    }

    private void sendProducts(Long orgId, String phone, JsonNode config, long categoryId) {
        List<Product> products = productsFor(orgId, config).stream()
                .filter(p -> catIdOf(p) == categoryId)
                .toList();

        if (products.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "No items in this category.");
            return;
        }

        String categoryName = categoryId == 0L ? "Other" : products.get(0).getCategory().getName();

        boolean hasImages = products.stream().anyMatch(p ->
                p.getImageUrl() != null && p.getImageUrl().startsWith("https://"));
        if (hasImages) {
            sendGallery(orgId, phone, categoryName, products);
            return;
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Product p : products) {
            if (rows.size() >= ROW_LIMIT) {
                break;
            }
            String desc = money(p, p.getPrice())
                    + (p.getDescription() == null || p.getDescription().isBlank() ? "" : " | " + p.getDescription());
            Map<String, String> row = new HashMap<>();
            row.put("id", PREFIX + "p:" + p.getId());
            row.put("title", cut(p.getName(), 24));
            row.put("description", cut(desc, 72));
            rows.add(row);
        }

        whatsAppCloudApiService.sendListMessage(
                orgId, phone, categoryName + " - choose " + config.path("itemLabel").asText("an item"),
                cut(config.path("itemsButton").asText("Items"), 20), cut(categoryName, 24), rows);
    }

    private void sendGallery(Long orgId, String phone, String categoryName, List<Product> products) {
        int sent = 0;
        for (Product p : products) {
            if (sent >= GALLERY_LIMIT) {
                break;
            }
            StringBuilder body = new StringBuilder(p.getName()).append("\n").append(money(p, p.getPrice()));
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                body.append("\n\n").append(p.getDescription());
            }
            List<String[]> buttons = List.of(
                    new String[]{PREFIX + "add:" + p.getId(), "Add to cart"},
                    new String[]{PREFIX + "checkout", "View cart"}
            );
            sendButtons(orgId, phone, body.toString(), p.getImageUrl(), buttons);
            sent++;
        }

        String footer = categoryName + ": showing " + sent + " of " + products.size() + " items";
        sendButtons(orgId, phone, footer, null, List.of(
                new String[]{PREFIX + "menu", "Back to menu"},
                new String[]{PREFIX + "checkout", "View cart"}
        ));
    }

    private void sendProductDetail(Long orgId, String phone, Long productId) {
        Product p = activeProduct(orgId, productId);
        if (p == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "This item is not available.");
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append(p.getName()).append("\n").append(money(p, p.getPrice()));
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            body.append("\n\n").append(p.getDescription());
        }

        List<String[]> buttons = List.of(
                new String[]{PREFIX + "add:" + p.getId(), "Add to cart"},
                new String[]{PREFIX + "cat:" + catIdOf(p), "Back"},
                new String[]{PREFIX + "checkout", "View cart"}
        );

        sendButtons(orgId, phone, body.toString(), p.getImageUrl(), buttons);
    }

    private void addToCart(Long orgId, String phone, FlowExecutionSession session, Long productId) {
        Product p = activeProduct(orgId, productId);
        if (p == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "This item is not available.");
            return;
        }

        Map<String, Integer> cart = readCart(session);
        int qty = Math.min(MAX_QTY, cart.getOrDefault(String.valueOf(p.getId()), 0) + 1);
        cart.put(String.valueOf(p.getId()), qty);
        writeCart(session, cart);

        BigDecimal total = BigDecimal.ZERO;
        for (Line line : cartLines(orgId, cart)) {
            total = total.add(line.total());
        }

        List<String[]> buttons = List.of(
                new String[]{PREFIX + "menu", "Add more"},
                new String[]{PREFIX + "checkout", "Checkout"},
                new String[]{PREFIX + "cancel", "Cancel"}
        );

        sendButtons(orgId, phone,
                "Added: " + p.getName() + " (qty " + qty + ")\nCart total: " + money(p, total),
                null, buttons);
    }

    private void sendSummary(Long orgId, String phone, FlowExecutionSession session, JsonNode config) {
        List<Line> lines = cartLines(orgId, readCart(session));

        if (lines.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Your cart is empty.");
            sendCategories(orgId, phone, config);
            return;
        }

        StringBuilder text = new StringBuilder("Your order\n\n");
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            text.append(line.product().getName())
                    .append(" x ").append(line.qty())
                    .append(" = ").append(money(line.product(), line.total()))
                    .append("\n");
            total = total.add(line.total());
        }
        text.append("\nTotal: ").append(money(lines.get(0).product(), total));

        List<String[]> buttons = List.of(
                new String[]{PREFIX + "confirm", "Confirm order"},
                new String[]{PREFIX + "menu", "Add more"},
                new String[]{PREFIX + "cancel", "Cancel"}
        );

        sendButtons(orgId, phone, text.toString(), null, buttons);
    }

    private void confirmOrder(
            Long orgId,
            Contact contact,
            Conversation conversation,
            String phone,
            FlowExecutionSession session,
            JsonNode config
    ) {
        List<Line> lines = cartLines(orgId, readCart(session));

        if (lines.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Your cart is empty.");
            return;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            total = total.add(line.total());
        }

        String prefix = config.path("orderPrefix").asText("");
        if (prefix.isBlank()) {
            prefix = "ORD";
        }
        String orderNumber = prefix + "-" + String.valueOf(System.currentTimeMillis()).substring(5);

        Order order = Order.builder()
                .organizationId(orgId)
                .conversationId(conversation.getId())
                .contactId(contact.getId())
                .orderNumber(orderNumber)
                .subtotal(total)
                .totalAmount(total)
                .currency(lines.get(0).product().getCurrency())
                .customerPhone(phone)
                .customerName(contact.getName())
                .build();

        for (Line line : lines) {
            Product p = line.product();
            order.addItem(OrderItem.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .sku(p.getSku())
                    .unitPrice(p.getPrice())
                    .quantity(line.qty())
                    .currency(p.getCurrency())
                    .build());
        }

        orderRepository.save(order);

        writeCart(session, new LinkedHashMap<>());

        whatsAppCloudApiService.sendTextMessage(orgId, phone,
                "Order confirmed. Order no: " + orderNumber
                        + "\nTotal: " + money(lines.get(0).product(), total)
                        + "\n" + config.path("paymentNote").asText("Payment: pay at pickup/delivery.")
                        + "\n" + config.path("restartHint").asText("Send hi for the menu."));

        notifyStaff(orgId, config, order, lines, total);
    }

    private void cancelOrder(Long orgId, String phone, FlowExecutionSession session) {
        writeCart(session, new LinkedHashMap<>());
        whatsAppCloudApiService.sendTextMessage(orgId, phone, "Order cancelled. " + readConfig(session).path("restartHint").asText("Send hi for the menu."));
    }

    private void notifyStaff(Long orgId, JsonNode config, Order order, List<Line> lines, BigDecimal total) {
        String staff = config.path("staffNotifyNumber").asText("").replaceAll("[^0-9]", "");
        if (staff.isBlank()) {
            return;
        }

        StringBuilder text = new StringBuilder("New order " + order.getOrderNumber() + "\n");
        text.append("Customer: ").append(order.getCustomerName()).append(" ").append(order.getCustomerPhone()).append("\n");
        for (Line line : lines) {
            text.append(line.product().getName()).append(" x ").append(line.qty()).append("\n");
        }
        text.append("Total: ").append(money(lines.get(0).product(), total));

        try {
            whatsAppCloudApiService.sendTextMessage(orgId, staff, text.toString());
        } catch (Exception e) {
            // WhatsApp only allows free-form text within 24h of the recipient's last message
            log.warn("Staff notification failed: {}", e.getMessage());
        }
    }

    // =========================================================
    // DATA HELPERS
    // =========================================================

    private List<Product> productsFor(Long orgId, JsonNode config) {
        String mode = config.path("selectionMode").asText("ALL_ACTIVE_CATEGORIES");
        Set<Long> categoryIds = idSet(config.path("categoryIds"));
        Set<Long> productIds = idSet(config.path("productIds"));

        return productRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, "ACTIVE")
                .stream()
                .filter(p -> p.getCategory() == null
                        || "ACTIVE".equalsIgnoreCase(p.getCategory().getStatus()))
                .filter(p -> {
                    if ("SELECTED_CATEGORIES".equals(mode) && !categoryIds.isEmpty()) {
                        return categoryIds.contains(catIdOf(p));
                    }
                    if ("SELECTED_PRODUCTS".equals(mode) && !productIds.isEmpty()) {
                        return productIds.contains(p.getId());
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Product::getId))
                .toList();
    }

    private Product activeProduct(Long orgId, Long productId) {
        if (productId == null) {
            return null;
        }
        Product p = productRepository.findByIdAndOrganizationId(productId, orgId).orElse(null);
        if (p == null || !"ACTIVE".equalsIgnoreCase(p.getStatus())) {
            return null;
        }
        return p;
    }

    private List<Line> cartLines(Long orgId, Map<String, Integer> cart) {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = activeProduct(orgId, parseLong(e.getKey()));
            if (p == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            lines.add(new Line(p, e.getValue(), p.getPrice().multiply(BigDecimal.valueOf(e.getValue()))));
        }
        return lines;
    }

    private JsonNode readConfig(FlowExecutionSession session) {
        try {
            if (session.getStateJson() != null && !session.getStateJson().isBlank()) {
                return objectMapper.readTree(session.getStateJson());
            }
        } catch (Exception e) {
            log.warn("Bad ordering config: {}", e.getMessage());
        }
        return objectMapper.createObjectNode();
    }

    private Map<String, Integer> readCart(FlowExecutionSession session) {
        try {
            if (session.getCartJson() != null && !session.getCartJson().isBlank()) {
                return objectMapper.readValue(
                        session.getCartJson(), new TypeReference<LinkedHashMap<String, Integer>>() {
                        });
            }
        } catch (Exception e) {
            log.warn("Bad cart json: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private void writeCart(FlowExecutionSession session, Map<String, Integer> cart) {
        try {
            session.setCartJson(objectMapper.writeValueAsString(cart));
        } catch (Exception e) {
            session.setCartJson("{}");
        }
        sessionRepository.save(session);
    }

    private Set<Long> idSet(JsonNode array) {
        Set<Long> ids = new HashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                Long v = parseLong(n.asText());
                if (v != null) {
                    ids.add(v);
                }
            }
        }
        return ids;
    }

    private Long catIdOf(Product p) {
        return p.getCategory() == null ? 0L : p.getCategory().getId();
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String money(Product p, BigDecimal amount) {
        String currency = p.getCurrency() == null ? "INR" : p.getCurrency();
        String label = "INR".equalsIgnoreCase(currency) ? "Rs " : currency + " ";
        return label + amount.stripTrailingZeros().toPlainString();
    }

    private String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max - 1) + ".";
    }

    // =========================================================
    // REPLY BUTTONS (max 3) with optional image header
    // =========================================================

    private void sendButtons(
            Long orgId,
            String phone,
            String body,
            String headerImageUrl,
            List<String[]> buttons
    ) {
        WhatsAppNumber number = whatsAppNumberRepository
                .findFirstByOrganizationIdAndStatus(orgId, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("No active WhatsApp number found"));

        boolean useHeader = headerImageUrl != null && headerImageUrl.startsWith("https://");

        try {
            postButtons(number, phone, body, useHeader ? headerImageUrl : null, buttons);
        } catch (Exception e) {
            if (!useHeader) {
                throw new RuntimeException(e);
            }
            log.warn("Button message with image failed, retrying without image: {}", e.getMessage());
            postButtons(number, phone, body, null, buttons);
        }
    }

    private void postButtons(
            WhatsAppNumber number,
            String phone,
            String body,
            String headerImageUrl,
            List<String[]> buttons
    ) {
        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");

        if (headerImageUrl != null) {
            Map<String, String> image = new HashMap<>();
            image.put("link", headerImageUrl);
            Map<String, Object> header = new HashMap<>();
            header.put("type", "image");
            header.put("image", image);
            interactive.put("header", header);
        }

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("text", cut(body, 1024));
        interactive.put("body", bodyMap);

        List<Map<String, Object>> buttonList = new ArrayList<>();
        for (String[] b : buttons) {
            Map<String, String> reply = new HashMap<>();
            reply.put("id", b[0]);
            reply.put("title", cut(b[1], 20));
            Map<String, Object> button = new HashMap<>();
            button.put("type", "reply");
            button.put("reply", reply);
            buttonList.add(button);
        }

        Map<String, Object> action = new HashMap<>();
        action.put("buttons", buttonList);
        interactive.put("action", action);

        Map<String, Object> request = new HashMap<>();
        request.put("messaging_product", "whatsapp");
        request.put("recipient_type", "individual");
        request.put("to", phone.replaceAll("[^0-9]", ""));
        request.put("type", "interactive");
        request.put("interactive", interactive);

        String url = graphApiUrl + "/" + graphApiVersion + "/" + number.getPhoneNumberId() + "/messages";

        restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + number.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
    }
}
