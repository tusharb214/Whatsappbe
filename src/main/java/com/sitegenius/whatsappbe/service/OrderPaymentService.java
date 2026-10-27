package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.OrderItem;
import com.sitegenius.whatsappbe.entity.OrganizationPaymentSettings;
import com.sitegenius.whatsappbe.repository.FlowNodeRepository;
import com.sitegenius.whatsappbe.repository.FlowRepository;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import com.sitegenius.whatsappbe.repository.OrganizationPaymentSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Online payment (Razorpay payment links, using the business's own keys) or cash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderPaymentService {

    private static final String PREFIX = "ord:";
    private static final int LINK_VALID_MINUTES = 60;

    private final OrganizationPaymentSettingsRepository settingsRepository;
    private final OrderRepository orderRepository;
    private final SecretCrypto crypto;
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final FlowRepository flowRepository;
    private final FlowNodeRepository flowNodeRepository;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().build();

    // =========================================================
    // CHOICE: online or cash
    // =========================================================

    public boolean onlineEnabled(Long orgId) {
        return settingsRepository.findByOrganizationId(orgId)
                .map(s -> s.isRazorpayEnabled()
                        && notBlank(s.getRazorpayKeyId())
                        && notBlank(s.getRazorpayKeySecretEnc())
                        && notBlank(s.getRazorpayWebhookSecretEnc())
                        && crypto.isReady())
                .orElse(false);
    }

    /** Returns true when the payment choice was sent to the customer. */
    public boolean offerChoice(Long orgId, String phone, Order order) {
        if (!onlineEnabled(orgId)) {
            return false;
        }

        String text = "Order " + order.getOrderNumber() + " placed.\nTotal: " + orderMoney(order)
                + "\n\nHow would you like to pay?";

        List<String[]> buttons = List.of(
                new String[]{PREFIX + "payonline:" + order.getId(), "Pay online"},
                new String[]{PREFIX + "paycash:" + order.getId(), "Cash"}
        );

        whatsAppCloudApiService.sendButtonsMessage(orgId, phone, text, buttons);
        return true;
    }

    public void payCash(Long orgId, String phone, Long orderId) {
        Order order = ownOrder(orgId, phone, orderId);
        if (order == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Order not found.");
            return;
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "Order " + order.getOrderNumber() + " is already paid.");
            return;
        }

        order.setPaymentMethod("CASH");
        orderRepository.save(order);

        whatsAppCloudApiService.sendTextMessage(orgId, phone,
                "Order confirmed. Order no: " + order.getOrderNumber()
                        + "\nTotal: " + orderMoney(order)
                        + "\nPayment: cash at pickup/delivery."
                        + "\nSend hi for the menu.");

        notifyStaff(orgId, order, "New order " + order.getOrderNumber() + " (CASH)\n" + orderSummary(order));
    }

    public void payOnline(Long orgId, String phone, Long orderId) {
        Order order = ownOrder(orgId, phone, orderId);
        if (order == null) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "Order not found.");
            return;
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "Order " + order.getOrderNumber() + " is already paid.");
            return;
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, "This order was cancelled.");
            return;
        }

        try {
            String link = createPaymentLink(orgId, order);

            order.setPaymentMethod("ONLINE");
            orderRepository.save(order);

            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "Pay " + orderMoney(order) + " for order " + order.getOrderNumber()
                            + " using this secure link:\n" + link
                            + "\n\nThe link is valid for " + LINK_VALID_MINUTES
                            + " minutes. We will confirm here as soon as the payment is received.");
        } catch (Exception e) {
            log.warn("Payment link failed for org {}: {}", orgId, e.getMessage());
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "Online payment is not available right now. You can pay cash at pickup/delivery.");
            payCash(orgId, phone, orderId);
        }
    }

    private String createPaymentLink(Long orgId, Order order) throws Exception {
        OrganizationPaymentSettings settings = settingsRepository.findByOrganizationId(orgId)
                .orElseThrow(() -> new RuntimeException("Payment settings not found"));

        String keyId = settings.getRazorpayKeyId();
        String keySecret = crypto.decrypt(settings.getRazorpayKeySecretEnc());

        long amountInPaise = order.getTotalAmount().movePointRight(2).longValueExact();

        Map<String, Object> notes = new HashMap<>();
        notes.put("order_id", String.valueOf(order.getId()));
        notes.put("order_number", order.getOrderNumber());

        Map<String, Object> notify = new HashMap<>();
        notify.put("sms", false);
        notify.put("email", false);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountInPaise);
        body.put("currency", order.getCurrency() == null ? "INR" : order.getCurrency());
        body.put("accept_partial", false);
        body.put("description", "Order " + order.getOrderNumber());
        // reference id must be unique for every payment link
        body.put("reference_id", order.getOrderNumber() + "_p" + (System.currentTimeMillis() % 1000000));
        body.put("expire_by", Instant.now().getEpochSecond() + LINK_VALID_MINUTES * 60L);
        body.put("notes", notes);
        body.put("notify", notify);
        body.put("reminder_enable", false);

        String basic = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        String response = restClient.post()
                .uri("https://api.razorpay.com/v1/payment_links")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        String url = objectMapper.readTree(response).path("short_url").asText("");
        if (url.isBlank()) {
            throw new RuntimeException("Razorpay did not return a payment link");
        }
        return url;
    }

    // =========================================================
    // RAZORPAY WEBHOOK (payment_link.paid)
    // =========================================================

    /** Returns false when the signature is not valid. */
    public boolean handleWebhook(Long orgId, String rawBody, String signature) throws Exception {
        log.info("Razorpay webhook received for org {}", orgId);
        OrganizationPaymentSettings settings = settingsRepository.findByOrganizationId(orgId).orElse(null);
        if (settings == null || !notBlank(settings.getRazorpayWebhookSecretEnc()) || !crypto.isReady()) {
            return false;
        }

        String secret = crypto.decrypt(settings.getRazorpayWebhookSecretEnc());
        if (!signatureValid(rawBody, signature, secret)) {
            log.warn("Razorpay webhook signature invalid for org {}", orgId);
            return false;
        }

        JsonNode root = objectMapper.readTree(rawBody);
        if (!"payment_link.paid".equals(root.path("event").asText())) {
            return true;
        }

        JsonNode link = root.path("payload").path("payment_link").path("entity");
        JsonNode payment = root.path("payload").path("payment").path("entity");

        Long orderId = parseLong(link.path("notes").path("order_id").asText(""));
        Order order = orderId == null ? null : orderRepository.findById(orderId)
                .filter(o -> orgId.equals(o.getOrganizationId()))
                .orElse(null);

        if (order == null || order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            log.warn("Razorpay webhook: order not found or already paid, orderId={}", orderId);
            return true;
        }

        long expected = order.getTotalAmount().movePointRight(2).longValue();
        long paid = link.path("amount_paid").asLong(expected);
        if (paid < expected) {
            log.warn("Payment amount mismatch for order {}: paid {} expected {}", order.getId(), paid, expected);
            return true;
        }

        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setPaymentMethod("ONLINE");
        order.setPaymentId(payment.path("id").asText(null));
        orderRepository.save(order);

        try {
        if (notBlank(order.getCustomerPhone())) {
            whatsAppCloudApiService.sendTextMessage(orgId, order.getCustomerPhone(),
                    "Payment received for order " + order.getOrderNumber() + ". Thank you!\n"
                            + "Total paid: " + orderMoney(order));
        }

        } catch (Exception e) {
            log.warn("Customer payment message failed: {}", e.getMessage());
        }

        notifyStaff(orgId, order,
                "New PAID order " + order.getOrderNumber() + " (ONLINE)\n" + orderSummary(order));

        return true;
    }

    private boolean signatureValid(String body, String header, String secret) {
        if (header == null || header.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    header.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void notifyStaff(Long orgId, Order order, String text) {
        String staff = staffNumber(orgId);
        if (staff.isBlank()) {
            return;
        }
        try {
            whatsAppCloudApiService.sendTextMessage(orgId, staff, text);
        } catch (Exception e) {
            // WhatsApp only allows free-form text within 24h of the recipient's last message
            log.warn("Staff notification failed: {}", e.getMessage());
        }
    }

    private String staffNumber(Long orgId) {
        try {
            List<Long> flowIds = flowRepository
                    .findByOrganizationIdAndStatus(orgId, Flow.FlowStatus.PUBLISHED)
                    .stream()
                    .map(Flow::getId)
                    .toList();
            if (flowIds.isEmpty()) {
                return "";
            }
            for (FlowNode node : flowNodeRepository.findByFlowIdInAndNodeTypeInOrderByIdAsc(
                    flowIds, List.of(FlowNode.NodeType.PRODUCT_MENU))) {
                String number = objectMapper.readTree(node.getConfig())
                        .path("staffNotifyNumber").asText("").replaceAll("[^0-9]", "");
                if (!number.isBlank()) {
                    return number;
                }
            }
        } catch (Exception e) {
            log.warn("Could not read staff number: {}", e.getMessage());
        }
        return "";
    }

    private String orderSummary(Order order) {
        StringBuilder text = new StringBuilder();
        text.append("Customer: ").append(order.getCustomerName()).append(" ")
                .append(order.getCustomerPhone()).append("\n");
        for (OrderItem item : order.getItems()) {
            text.append(item.getProductName()).append(" x ").append(item.getQuantity()).append("\n");
        }
        text.append("Total: ").append(orderMoney(order));
        return text.toString();
    }

    private Order ownOrder(Long orgId, String phone, Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderRepository.findById(orderId)
                .filter(o -> orgId.equals(o.getOrganizationId()) && phone.equals(o.getCustomerPhone()))
                .orElse(null);
    }

    private String orderMoney(Order order) {
        String currency = order.getCurrency() == null ? "INR" : order.getCurrency();
        String label = "INR".equalsIgnoreCase(currency) ? "Rs " : currency + " ";
        return label + order.getTotalAmount().stripTrailingZeros().toPlainString();
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
