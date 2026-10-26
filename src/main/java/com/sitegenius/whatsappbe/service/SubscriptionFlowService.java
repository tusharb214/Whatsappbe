package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.CustomerSubscription;
import com.sitegenius.whatsappbe.entity.Flow;
import com.sitegenius.whatsappbe.entity.FlowExecutionSession;
import com.sitegenius.whatsappbe.entity.FlowNode;
import com.sitegenius.whatsappbe.entity.SubscriptionPlan;
import com.sitegenius.whatsappbe.repository.CustomerSubscriptionRepository;
import com.sitegenius.whatsappbe.repository.FlowExecutionSessionRepository;
import com.sitegenius.whatsappbe.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionFlowService {

    private static final String PREFIX = "sub:";

    private final SubscriptionPlanRepository planRepository;
    private final CustomerSubscriptionRepository subscriptionRepository;
    private final FlowExecutionSessionRepository sessionRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final OrderingService orderingService;
    private final com.sitegenius.whatsappbe.repository.FlowNodeRepository flowNodeRepository;
    private final ObjectMapper objectMapper;

    // ENTRY 1: flow engine reached the SUBSCRIPTION node
    public void start(Flow flow, FlowNode node, Long orgId, String phone) {
        try {
            FlowExecutionSession session = sessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(orgId, phone)
                    .orElseGet(FlowExecutionSession::new);
            session.setOrganizationId(orgId);
            session.setCustomerPhoneNumber(phone);
            session.setFlowId(flow.getId());
            session.setPendingNodeId(null);
            session.setStateJson(node.getConfig());
            sessionRepository.save(session);

            JsonNode cfg = config(session);
            boolean showBuy = cfg.path("showBuy").asBoolean(true);
            boolean showMy = cfg.path("showMine").asBoolean(true);
            String message = cfg.path("message").asText("Please choose a plan");

            if (showBuy && !showMy) {
                sendPlanList(orgId, phone, cfg);
                return;
            }
            if (showMy && !showBuy) {
                sendMySubscription(orgId, phone);
                return;
            }
            List<String[]> buttons = new ArrayList<>();
            if (showBuy) buttons.add(new String[]{"sub:buy", label(cfg, "buyLabel", "🛒 Buy plan")});
            if (showMy) buttons.add(new String[]{"sub:my", label(cfg, "mineLabel", "💳 My subscription")});
            if (buttons.isEmpty()) {
                whatsAppCloudApiService.sendTextMessage(orgId, phone, message);
                return;
            }
            orderingService.sendButtons(orgId, phone, message, null, buttons);
        } catch (Exception e) {
            log.error("Subscription start failed: {}", e.getMessage(), e);
        }
    }

    // ENTRY 2: every incoming interactive reply; true = consumed
    public boolean handle(Long orgId, String phone, String input) {
        if (input == null || !input.startsWith(PREFIX)) {
            return false;
        }
        try {
            String[] p = input.split(":");
            String action = p.length > 1 ? p[1] : "";

            switch (action) {
                case "buy" -> sendPlanList(orgId, phone, sessionConfig(orgId, phone));
                case "my" -> sendMySubscription(orgId, phone);
                case "plan" -> sendPlanDetail(orgId, phone, parse(p, 2));
                case "pay" -> {
                    String method = p.length > 2 ? p[2] : "";
                    createSubscription(orgId, phone, parse(p, 3), method);
                }
                default -> whatsAppCloudApiService.sendTextMessage(
                        orgId, phone, "This option is not available right now.");
            }
        } catch (Exception e) {
            log.error("Subscription handle failed: {}", e.getMessage(), e);
            whatsAppCloudApiService.sendTextMessage(
                    orgId, phone, "Something went wrong. Please try again.");
        }
        return true;
    }

    // ---------------- plans list ----------------

    private void sendPlanList(Long orgId, String phone, JsonNode cfg) {
        List<SubscriptionPlan> plans = planRepository
                .findByOrganizationIdAndActiveTrueOrderBySortOrderAscIdAsc(orgId);

        if ("SELECTED_PLANS".equals(cfg.path("planMode").asText("ALL_ACTIVE_PLANS"))) {
            Set<Long> ids = new HashSet<>();
            cfg.path("planIds").forEach(n -> ids.add(n.asLong()));
            plans = plans.stream().filter(pl -> ids.contains(pl.getId())).toList();
        }

        if (plans.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(
                    orgId, phone, "No plans are available right now.");
            return;
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (SubscriptionPlan pl : plans.stream().limit(10).toList()) {
            Map<String, String> row = new HashMap<>();
            row.put("id", "sub:plan:" + pl.getId());
            row.put("title", cut(pl.getName(), 24));
            String info = money(pl.getCurrency(), pl.getPrice())
                    + " • " + pl.getDurationDays() + " days";
            if (notBlank(pl.getDescription())) {
                info += " • " + pl.getDescription().trim().replaceAll("\\s+", " ");
            }
            row.put("description", cut(info, 72));
            rows.add(row);
        }
        whatsAppCloudApiService.sendListMessage(
                orgId, phone, "Choose your plan 👇", "View plans", "Plans", rows);
    }

    // ---------------- plan detail ----------------

    private void sendPlanDetail(Long orgId, String phone, Long planId) {
        SubscriptionPlan pl = planId == null ? null
                : planRepository.findByIdAndOrganizationId(planId, orgId).orElse(null);
        if (pl == null || !Boolean.TRUE.equals(pl.getActive())) {
            whatsAppCloudApiService.sendTextMessage(
                    orgId, phone, "This plan is not available anymore.");
            return;
        }

        StringBuilder t = new StringBuilder();
        t.append("*").append(pl.getName()).append("*\n");
        t.append(money(pl.getCurrency(), pl.getPrice()))
                .append(" • ").append(pl.getDurationDays()).append(" days\n");
        if (notBlank(pl.getDescription())) {
            t.append("\n").append(pl.getDescription().trim()).append("\n");
        }
        if (notBlank(pl.getFeatures())) {
            t.append("\n");
            for (String f : pl.getFeatures().split("\\r?\\n")) {
                if (!f.isBlank()) t.append("✅ ").append(f.trim()).append("\n");
            }
        }
        if (notBlank(pl.getInstructions())) {
            t.append("\nℹ️ ").append(pl.getInstructions().trim());
        }

        List<String[]> buttons = new ArrayList<>();
        if (Boolean.TRUE.equals(pl.getAllowOnline()))
            buttons.add(new String[]{"sub:pay:online:" + pl.getId(), "💳 Pay Online"});
        if (Boolean.TRUE.equals(pl.getAllowCash()))
            buttons.add(new String[]{"sub:pay:cash:" + pl.getId(), "💵 Pay Cash"});
        JsonNode nodeCfg = nodeConfig(orgId, phone);
        if (nodeCfg.path("showBack").asBoolean(true) && buttons.size() < 3) {
            buttons.add(new String[]{"sub:buy", label(nodeCfg, "backLabel", "⬅️ Back")});
        }
        if (buttons.isEmpty()) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone, t.toString());
            return;
        }
        orderingService.sendButtons(orgId, phone, t.toString(), pl.getImageUrl(), buttons);
    }

    // ---------------- buy ----------------

    private void createSubscription(Long orgId, String phone, Long planId, String method) {
        SubscriptionPlan pl = planId == null ? null
                : planRepository.findByIdAndOrganizationId(planId, orgId).orElse(null);
        if (pl == null || !Boolean.TRUE.equals(pl.getActive())) {
            whatsAppCloudApiService.sendTextMessage(
                    orgId, phone, "This plan is not available anymore.");
            return;
        }

        boolean cash = "cash".equals(method);
        if (cash && !Boolean.TRUE.equals(pl.getAllowCash())) return;
        if (!cash && !Boolean.TRUE.equals(pl.getAllowOnline())) return;

        CustomerSubscription sub = CustomerSubscription.builder()
                .organizationId(orgId)
                .planId(pl.getId())
                .customerPhone(phone)
                .status(cash ? CustomerSubscription.Status.PENDING_APPROVAL
                        : CustomerSubscription.Status.PENDING_PAYMENT)
                .paymentMethod(cash ? CustomerSubscription.PaymentMethod.CASH
                        : CustomerSubscription.PaymentMethod.ONLINE)
                .amount(pl.getPrice())
                .currency(pl.getCurrency())
                .build();
        subscriptionRepository.save(sub);

        if (cash) {
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "✅ Request received for *" + pl.getName() + "* ("
                            + money(pl.getCurrency(), pl.getPrice()) + ").\n"
                            + "Please pay cash at the counter. Your plan will be "
                            + "activated once our team approves it.");
            notifyStaff(orgId, phone, "New CASH subscription request: "
                    + pl.getName() + " from " + phone);
        } else {
            // TODO: Razorpay payment link (needs existing Razorpay service)
            whatsAppCloudApiService.sendTextMessage(orgId, phone,
                    "Online payment link for *" + pl.getName() + "* is being prepared.");
        }
    }

    // ---------------- my subscription ----------------

    private void sendMySubscription(Long orgId, String phone) {
        List<CustomerSubscription> all = subscriptionRepository
                .findByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(orgId, phone);
        LocalDateTime now = LocalDateTime.now();

        CustomerSubscription active = all.stream()
                .filter(s -> s.getStatus() == CustomerSubscription.Status.ACTIVE
                        && s.getEndDate() != null && s.getEndDate().isAfter(now))
                .findFirst().orElse(null);

        if (active == null) {
            boolean pending = all.stream().anyMatch(s ->
                    s.getStatus() == CustomerSubscription.Status.PENDING_APPROVAL
                            || s.getStatus() == CustomerSubscription.Status.PENDING_PAYMENT);
            whatsAppCloudApiService.sendTextMessage(orgId, phone, pending
                    ? "⏳ Your subscription request is pending approval/payment."
                    : "You don't have an active subscription yet. Choose *Meal Subscription* to buy one.");
            return;
        }

        SubscriptionPlan pl = planRepository
                .findByIdAndOrganizationId(active.getPlanId(), orgId).orElse(null);
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), active.getEndDate().toLocalDate());

        StringBuilder t = new StringBuilder("💳 *Your subscription*\n\n");
        t.append("Plan: *").append(pl != null ? pl.getName() : "Plan").append("*\n");
        t.append("Days left: *").append(Math.max(daysLeft, 0)).append("*\n");
        t.append("Ends on: ").append(active.getEndDate()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).append("\n");
        if (pl != null && notBlank(pl.getFeatures())) {
            t.append("\n");
            for (String f : pl.getFeatures().split("\\r?\\n")) {
                if (!f.isBlank()) t.append("✅ ").append(f.trim()).append("\n");
            }
        }
        whatsAppCloudApiService.sendTextMessage(orgId, phone, t.toString());
    }

    // ---------------- helpers ----------------

    private void notifyStaff(Long orgId, String phone, String text) {
        try {
            String staff = sessionConfig(orgId, phone).path("staffNotifyNumber").asText("");
            if (!staff.isBlank()) {
                whatsAppCloudApiService.sendTextMessage(orgId, staff, text);
            }
        } catch (Exception e) {
            log.warn("Staff notify failed: {}", e.getMessage());
        }
    }

    private JsonNode sessionConfig(Long orgId, String phone) {
        return nodeConfig(orgId, phone);
    }

    // reads the SUBSCRIPTION node config from DB (not the shared session state)
    private JsonNode nodeConfig(Long orgId, String phone) {
        try {
            FlowExecutionSession s = sessionRepository
                    .findByOrganizationIdAndCustomerPhoneNumber(orgId, phone).orElse(null);
            if (s != null && s.getFlowId() != null) {
                FlowNode n = findFirstSubscriptionNode(s.getFlowId());
                if (n != null && n.getConfig() != null) {
                    return objectMapper.readTree(n.getConfig());
                }
            }
        } catch (Exception e) {
            log.warn("Node config read failed: {}", e.getMessage());
        }
        return objectMapper.createObjectNode();
    }

    private FlowNode findFirstSubscriptionNode(Long flowId) {
        return flowNodeRepository.findByFlowIdOrderByIdAsc(flowId).stream()
                .filter(n -> n.getNodeType() == FlowNode.NodeType.SUBSCRIPTION)
                .findFirst().orElse(null);
    }

    private static String label(JsonNode cfg, String key, String def) {
        String v = cfg.path(key).asText("");
        return v.isBlank() ? def : v.trim();
    }

    private JsonNode config(FlowExecutionSession s) {
        try {
            return objectMapper.readTree(
                    s.getStateJson() == null ? "{}" : s.getStateJson());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static Long parse(String[] p, int i) {
        try { return p.length > i ? Long.parseLong(p[i]) : null; }
        catch (NumberFormatException e) { return null; }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String money(String cur, BigDecimal v) {
        String sym = "INR".equalsIgnoreCase(cur) ? "₹" : cur + " ";
        return sym + v.stripTrailingZeros().toPlainString();
    }

    private static String cut(String v, int max) {
        if (v == null) return "";
        v = v.trim();
        return v.length() <= max ? v : v.substring(0, max - 1) + ".";
    }
}
