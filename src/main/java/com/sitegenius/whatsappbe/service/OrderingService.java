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
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowRepository;
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
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final OrderPaymentService paymentService;
    private final FlowRepository flowRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionBenefitService subscriptionBenefitService;

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

        // custom cart buttons (ord:x:...) are handled by the flow engine
        if (input.startsWith(PREFIX + "x:")) {
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
                case "p" -> sendProductDetail(organizationId, phone, config, id);
                case "add" -> addToCart(organizationId, phone, session, id);
                case "checkout" -> sendSummary(organizationId, phone, session, config);
                case "confirm" -> confirmOrder(organizationId, contact, conversation, phone, session, config);
                case "cancel" -> cancelOrder(organizationId, phone, session);
                case "payonline" -> paymentService.payOnline(organizationId, phone, id);
                case "paycash" -> paymentService.payCash(organizationId, phone, id);
                case "account" -> sendAccount(organizationId, phone, session, config);
                case "cart" -> sendSummary(organizationId, phone, session, config);
                case "order" -> sendOrderDetail(organizationId, phone, id, config);
                case "cancelorder" -> cancelCustomerOrder(organizationId, phone, id, config);
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
            sendGallery(orgId, phone, config, categoryName, products);
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

    private void sendGallery(Long orgId, String phone, JsonNode config, String categoryName, List<Product> products) {
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

    private void sendProductDetail(Long orgId, String phone, JsonNode config, Long productId) {
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

        Map<Long, Integer> covered = coveredFor(orgId, phone, lines);
        StringBuilder text = new StringBuilder("Your order\n\n");
        BigDecimal total = BigDecimal.ZERO;
        boolean anyCovered = false;
        for (Line line : lines) {
            int c = covered.getOrDefault(line.product().getId(), 0);
            BigDecimal payable = line.product().getPrice()
                    .multiply(BigDecimal.valueOf(line.qty() - c));
            text.append(line.product().getName())
                    .append(" x ").append(line.qty())
                    .append(" = ").append(money(line.product(), payable));
            if (c > 0) {
                anyCovered = true;
                text.append(" (").append(c).append(" covered by plan)");
            }
            text.append("\n");
            total = total.add(payable);
        }
        text.append("\nTotal to pay: ").append(money(lines.get(0).product(), total));
        if (anyCovered) {
            text.append("\n(Your subscription is applied)");
        }

        List<String[]> buttons = List.of(
                new String[]{PREFIX + "confirm", "Confirm order"},
                new String[]{PREFIX + "menu", "Add more"},
                new String[]{PREFIX + "cancel", "Cancel"}
        );

        List<Map<String, String>> extras = extraRows(config);
        if (extras.isEmpty()) {
            sendButtons(orgId, phone, text.toString(), null, buttons);
            return;
        }

        // reply buttons allow only 3, so with extra buttons the cart actions go in a list
        List<Map<String, String>> rows = new ArrayList<>();
        for (String[] b : buttons) {
            Map<String, String> row = new HashMap<>();
            row.put("id", b[0]);
            row.put("title", cut(b[1], 24));
            rows.add(row);
        }
        rows.addAll(extras);
        whatsAppCloudApiService.sendListMessage(
                orgId, phone, cut(text.toString(), 1024), "Options", "Next step", rows);
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

        Map<Long, Integer> covered = coveredFor(orgId, phone, lines);
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            int c = covered.getOrDefault(line.product().getId(), 0);
            total = total.add(line.product().getPrice()
                    .multiply(BigDecimal.valueOf(line.qty() - c)));
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
                .lastNotifiedStatus(Order.OrderStatus.NEW)
                .build();

        for (Line line : lines) {
            Product p = line.product();
            int c = covered.getOrDefault(p.getId(), 0);
            if (c > 0) {
                order.addItem(OrderItem.builder()
                        .productId(p.getId())
                        .productName(p.getName() + " (Plan)")
                        .sku(p.getSku())
                        .unitPrice(BigDecimal.ZERO)
                        .quantity(c)
                        .currency(p.getCurrency())
                        .build());
            }
            if (line.qty() - c > 0) {
                order.addItem(OrderItem.builder()
                        .productId(p.getId())
                        .productName(p.getName())
                        .sku(p.getSku())
                        .unitPrice(p.getPrice())
                        .quantity(line.qty() - c)
                        .currency(p.getCurrency())
                        .build());
            }
        }

        Order savedOrder = orderRepository.save(order);
        subscriptionBenefitService.record(orgId, phone, savedOrder, covered);

        writeCart(session, new LinkedHashMap<>());

        if ("ONLINE_AND_CASH".equals(config.path("paymentMode").asText("CASH_ONLY"))
                && paymentService.offerChoice(orgId, phone, order)) {
            return;
        }

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

    private Map<Long, Integer> coveredFor(Long orgId, String phone, List<Line> lines) {
        List<SubscriptionBenefitService.CartItem> items = new ArrayList<>();
        for (Line l : lines) {
            items.add(new SubscriptionBenefitService.CartItem(
                    l.product().getId(), catIdOf(l.product()), l.qty()));
        }
        return subscriptionBenefitService.covered(orgId, phone, items);
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

    private String label(JsonNode config, String key, String fallback) {
        String value = config == null ? "" : config.path(key).asText("").trim();
        return cut(value.isBlank() ? fallback : value, 20);
    }

    private List<Map<String, String>> extraRows(JsonNode config) {
        List<Map<String, String>> rows = new ArrayList<>();
        JsonNode array = config == null ? null : config.path("extraButtons");
        if (array != null && array.isArray()) {
            for (JsonNode b : array) {
                String id = b.path("id").asText("");
                String title = b.path("text").asText("").trim();
                if (id.isBlank() || title.isBlank() || rows.size() >= 6) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                row.put("id", PREFIX + "x:" + id);
                row.put("title", cut(title, 24));
                rows.add(row);
            }
        }
        return rows;
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

    public void sendButtons(
            Long orgId,
            String phone,
            String body,
            String headerImageUrl,
            List<String[]> buttons
    ) {
        WhatsAppNumber number = whatsAppNumberRepository
                .findFirstByOrganizationIdAndStatus(orgId, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("No active WhatsApp number found"));

        boolean useHeader = headerImageUrl != null
                && headerImageUrl.startsWith("https://")
                && headerImageUrl.toLowerCase().split("[?#]")[0].matches(".*\\.(jpe?g|png)$");

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

    // =========================================================
    // CART + ORDERS (customer view), CANCEL POLICY, STATUS UPDATES
    // =========================================================

    public void openAccount(Flow flow, Long organizationId, String phone) {
        try {
            FlowExecutionSession session = sessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(organizationId, phone)
                    .orElseGet(FlowExecutionSession::new);

            JsonNode config = orderingConfig(organizationId);

            session.setOrganizationId(organizationId);
            session.setCustomerPhoneNumber(phone);
            session.setFlowId(flow.getId());
            session.setPendingNodeId(null);
            session.setStateJson(config.toString());
            sessionRepository.save(session);

            sendAccount(organizationId, phone, session, config);
        } catch (Exception e) {
            log.error("Open account failed: {}", e.getMessage());
        }
    }

    /** Ordering settings live on the organization's published PRODUCT_MENU node. */
    private JsonNode orderingConfig(Long orgId) {
        List<Long> flowIds = flowRepository
                .findByOrganizationIdAndStatus(orgId, Flow.FlowStatus.PUBLISHED)
                .stream()
                .map(Flow::getId)
                .toList();

        if (flowIds.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        List<FlowNode> nodes = flowNodeRepository.findByFlowIdInAndNodeTypeInOrderByIdAsc(
                flowIds, List.of(FlowNode.NodeType.PRODUCT_MENU, FlowNode.NodeType.MY_ORDERS));

        com.fasterxml.jackson.databind.node.ObjectNode merged = objectMapper.createObjectNode();
        boolean menuLoaded = false;

        // 1) base settings from the Product Menu node
        for (FlowNode node : nodes) {
            if (menuLoaded || node.getNodeType() != FlowNode.NodeType.PRODUCT_MENU) {
                continue;
            }
            try {
                JsonNode menu = objectMapper.readTree(node.getConfig());
                if (menu != null && menu.isObject()) {
                    merged.setAll((com.fasterxml.jackson.databind.node.ObjectNode) menu);
                    menuLoaded = true;
                }
            } catch (Exception ignored) {
                // try the next node
            }
        }

        // 2) cancel / status settings from the Cart and Orders node override
        for (FlowNode node : nodes) {
            if (node.getNodeType() != FlowNode.NodeType.MY_ORDERS) {
                continue;
            }
            try {
                JsonNode mine = objectMapper.readTree(node.getConfig());
                if (mine == null || !mine.isObject()) {
                    continue;
                }
                mine.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    boolean blankText = value.isTextual() && value.asText().isBlank();
                    if (!"buttons".equals(entry.getKey()) && !value.isNull() && !blankText) {
                        merged.set(entry.getKey(), value);
                    }
                });
                break;
            } catch (Exception ignored) {
                // try the next node
            }
        }
        return merged;
    }

    private void sendAccount(Long orgId, String phone, FlowExecutionSession session, JsonNode config) {
        List<Line> lines = cartLines(orgId, readCart(session));
        List<Order> orders = orderRepository
                .findTop10ByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(orgId, phone);

        List<Map<String, String>> rows = new ArrayList<>();

        if (!lines.isEmpty()) {
            int items = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (Line line : lines) {
                items += line.qty();
                total = total.add(line.total());
            }
            Map<String, String> row = new HashMap<>();
            row.put("id", PREFIX + "cart");
            row.put("title", cut("Your cart (" + items + ")", 24));
            row.put("description", cut(money(lines.get(0).product(), total) + " | tap to review", 72));
            rows.add(row);
        }

        for (Order order : orders) {
            if (rows.size() >= ROW_LIMIT) {
                break;
            }
            Map<String, String> row = new HashMap<>();
            row.put("id", PREFIX + "order:" + order.getId());
            row.put("title", cut(order.getOrderNumber(), 24));
            row.put("description", cut(statusLabel(order.getStatus()) + " | " + orderMoney(order), 72));
            rows.add(row);
        }

        if (rows.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "You have no cart or orders yet. Send hi to see the menu.");
            return;
        }

        whatsAppCloudApiService.sendListMessage(
                orgId, phone, "Your cart and orders", "View", "Cart and orders", rows);
    }

    private void sendOrderDetail(Long orgId, String phone, Long orderId, JsonNode config) {
        Order order = ownOrder(orgId, phone, orderId);
        if (order == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Order not found.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Order ").append(order.getOrderNumber()).append("\n");
        text.append("Status: ").append(statusLabel(order.getStatus())).append("\n\n");
        for (OrderItem item : order.getItems()) {
            text.append(item.getProductName()).append(" x ").append(item.getQuantity()).append("\n");
        }
        text.append("\nTotal: ").append(orderMoney(order));

        String denied = cancelDenied(order, config);
        boolean finished = order.getStatus() == Order.OrderStatus.DELIVERED
                || order.getStatus() == Order.OrderStatus.CANCELLED;

        if (denied != null && !finished) {
            text.append("\n\n").append(denied);
        }

        List<String[]> buttons = new ArrayList<>();
        if (denied == null) {
            buttons.add(new String[]{PREFIX + "cancelorder:" + order.getId(), "Cancel order"});
        }
        buttons.add(new String[]{PREFIX + "account", "My orders"});
        buttons.add(new String[]{PREFIX + "menu", "Menu"});

        sendButtons(orgId, phone, text.toString(), null, buttons);
    }

    private void cancelCustomerOrder(Long orgId, String phone, Long orderId, JsonNode config) {
        Order order = ownOrder(orgId, phone, orderId);
        if (order == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Order not found.");
            return;
        }

        String denied = cancelDenied(order, config);
        if (denied != null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, denied);
            return;
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setLastNotifiedStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        whatsAppCloudApiService.sendTextMessage(orgId, phone,
                "Order " + order.getOrderNumber() + " has been cancelled. Send hi for the menu.");

        sendToStaff(orgId, config,
                "Order " + order.getOrderNumber() + " was cancelled by the customer " + phone + ".");
    }

    /** Returns null when the customer may cancel, otherwise the message to show. */
    private String cancelDenied(Order order, JsonNode ignoredConfig) {
        JsonNode config = orderingConfig(order.getOrganizationId());
        String denied = config.path("cancelDeniedMessage").asText("");
        if (denied.isBlank()) {
            denied = "This order can no longer be cancelled. Please contact us.";
        }

        Order.OrderStatus status = order.getStatus();
        boolean open = status == Order.OrderStatus.NEW || status == Order.OrderStatus.CONFIRMED;
        String mode = config.path("cancelMode").asText("UNTIL_ACCEPTED");

        if ("NEVER".equals(mode)) {
            return denied;
        }

        if ("UNTIL_PREPARING".equals(mode)) {
            return open ? null : denied;
        }

        if ("WITHIN_MINUTES".equals(mode)) {
            if (!open || order.getCreatedAt() == null) {
                return denied;
            }
            long minutes = Duration.between(order.getCreatedAt(), LocalDateTime.now()).toMinutes();
            return minutes < config.path("cancelMinutes").asInt(5) ? null : denied;
        }

        // default UNTIL_ACCEPTED: only while the business has not accepted it yet
        return status == Order.OrderStatus.NEW ? null : denied;
    }

    private Order ownOrder(Long orgId, String phone, Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderRepository.findById(orderId)
                .filter(o -> orgId.equals(o.getOrganizationId()) && phone.equals(o.getCustomerPhone()))
                .orElse(null);
    }

    private void sendToStaff(Long orgId, JsonNode config, String text) {
        String staff = config.path("staffNotifyNumber").asText("").replaceAll("[^0-9]", "");
        if (staff.isBlank()) {
            return;
        }
        try {
            whatsAppCloudApiService.sendTextMessage(orgId, staff, text);
        } catch (Exception e) {
            log.warn("Staff notification failed: {}", e.getMessage());
        }
    }

    /** Called by the status notifier when an order's status changed. */
    public void notifyStatusChange(Order order) {
        try {
            if (order.getStatus() == null || order.getCustomerPhone() == null) {
                return;
            }
            JsonNode config = orderingConfig(order.getOrganizationId());
            String custom = config.path("statusMessages").path(order.getStatus().name()).asText("");
            String text = custom.isBlank()
                    ? defaultStatusText(order)
                    : custom.replace("{order}", String.valueOf(order.getOrderNumber()));

            whatsAppCloudApiService.sendTextMessage(
                    order.getOrganizationId(), order.getCustomerPhone(), text);
        } catch (Exception e) {
            log.warn("Status notification failed for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private String defaultStatusText(Order order) {
        String n = String.valueOf(order.getOrderNumber());
        return switch (order.getStatus()) {
            case NEW -> "We received your order " + n + ".";
            case CONFIRMED -> "Good news! Your order " + n + " has been accepted.";
            case PROCESSING -> "Your order " + n + " is being prepared.";
            case READY -> "Your order " + n + " is ready for pickup.";
            case OUT_FOR_DELIVERY -> "Your order " + n + " is out for delivery.";
            case DELIVERED -> "Your order " + n + " is delivered. Thank you!";
            case CANCELLED -> "Your order " + n + " has been cancelled.";
        };
    }

    private String statusLabel(Order.OrderStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case NEW -> "Placed";
            case CONFIRMED -> "Accepted";
            case PROCESSING -> "Preparing";
            case READY -> "Ready";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
        };
    }

    private String orderMoney(Order order) {
        String currency = order.getCurrency() == null ? "INR" : order.getCurrency();
        String label = "INR".equalsIgnoreCase(currency) ? "Rs " : currency + " ";
        return label + order.getTotalAmount().stripTrailingZeros().toPlainString();
    }
}
