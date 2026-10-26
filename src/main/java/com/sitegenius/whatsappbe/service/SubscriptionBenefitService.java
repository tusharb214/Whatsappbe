package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.CustomerSubscription;
import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.OrderItem;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.entity.SubscriptionRule;
import com.sitegenius.whatsappbe.repository.CustomerSubscriptionRepository;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import com.sitegenius.whatsappbe.repository.ProductRepository;
import com.sitegenius.whatsappbe.repository.SubscriptionRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBenefitService {

    public record CartItem(Long productId, Long categoryId, int qty) {
    }

    private record Used(Long productId, Long categoryId, int qty, LocalDateTime at) {
    }

    private final CustomerSubscriptionRepository subscriptionRepository;
    private final SubscriptionRuleRepository ruleRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final com.sitegenius.whatsappbe.repository.SubscriptionUsageRepository usageRepository;

    /** productId -> quantity covered by the customer's active plan (empty if none). */
    public Map<Long, Integer> covered(Long orgId, String phone, List<CartItem> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        try {
            LocalDateTime now = LocalDateTime.now();

            CustomerSubscription sub = subscriptionRepository
                    .findByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(orgId, phone)
                    .stream()
                    .filter(s -> s.getStatus() == CustomerSubscription.Status.ACTIVE
                            && s.getEndDate() != null && s.getEndDate().isAfter(now))
                    .findFirst().orElse(null);
            if (sub == null) {
                return result;
            }

            List<SubscriptionRule> rules = ruleRepository
                    .findByOrganizationIdAndPlanIdOrderByIdAsc(orgId, sub.getPlanId());
            if (rules.isEmpty()) {
                return result;
            }

            LocalDateTime planStart = sub.getStartDate() != null ? sub.getStartDate() : sub.getCreatedAt();
            List<Used> used = usedBenefits(orgId, phone, planStart, now);

            LocalDateTime dayStart = LocalDate.now().atStartOfDay();
            LocalDateTime weekStart = LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();

            int[] remaining = new int[rules.size()];
            for (int i = 0; i < rules.size(); i++) {
                SubscriptionRule r = rules.get(i);
                LocalDateTime from = switch (r.getPeriod()) {
                    case DAY -> dayStart;
                    case WEEK -> weekStart;
                    case PLAN -> planStart;
                };
                int taken = 0;
                for (Used u : used) {
                    if (!u.at().isBefore(from) && matches(r, u.productId(), u.categoryId())) {
                        taken += u.qty();
                    }
                }
                remaining[i] = Math.max(0, r.getQuantity() - taken);
            }

            // cart order = priority: first added eligible item is covered first
            for (CartItem item : items) {
                int left = item.qty();
                for (int i = 0; i < rules.size() && left > 0; i++) {
                    if (remaining[i] <= 0 || !matches(rules.get(i), item.productId(), item.categoryId())) {
                        continue;
                    }
                    int take = Math.min(left, remaining[i]);
                    remaining[i] -= take;
                    left -= take;
                    result.merge(item.productId(), take, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.error("Subscription benefit check failed: {}", e.getMessage(), e);
            result.clear();
        }
        return result;
    }

    private boolean matches(SubscriptionRule r, Long productId, Long categoryId) {
        return switch (r.getAppliesTo()) {
            case ANY -> true;
            case PRODUCT -> r.getTargetId() != null && r.getTargetId().equals(productId);
            case CATEGORY -> r.getTargetId() != null && r.getTargetId().equals(categoryId);
        };
    }

    // benefits already given (saved in subscription_usages), cancelled orders ignored
    private List<Used> usedBenefits(Long orgId, String phone, LocalDateTime from, LocalDateTime to) {
        List<com.sitegenius.whatsappbe.entity.SubscriptionUsage> rows = usageRepository
                .findByOrganizationIdAndCustomerPhoneAndUsedAtBetweenOrderByUsedAtDesc(orgId, phone, from, to);

        Set<Long> orderIds = new HashSet<>();
        for (com.sitegenius.whatsappbe.entity.SubscriptionUsage u : rows) {
            orderIds.add(u.getOrderId());
        }
        Set<Long> cancelled = new HashSet<>();
        for (Order o : orderRepository.findAllById(orderIds)) {
            if (o.getStatus() == Order.OrderStatus.CANCELLED) {
                cancelled.add(o.getId());
            }
        }

        List<Used> used = new ArrayList<>();
        for (com.sitegenius.whatsappbe.entity.SubscriptionUsage u : rows) {
            if (!cancelled.contains(u.getOrderId())) {
                used.add(new Used(u.getProductId(),
                        u.getCategoryId() == null ? 0L : u.getCategoryId(),
                        u.getQuantity(), u.getUsedAt()));
            }
        }
        return used;
    }

    /** Saves the benefits given in a confirmed order. */
    public void record(Long orgId, String phone, Order order, Map<Long, Integer> covered) {
        if (covered == null || covered.isEmpty()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            CustomerSubscription sub = subscriptionRepository
                    .findByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(orgId, phone)
                    .stream()
                    .filter(x -> x.getStatus() == CustomerSubscription.Status.ACTIVE
                            && x.getEndDate() != null && x.getEndDate().isAfter(now))
                    .findFirst().orElse(null);
            if (sub == null) {
                return;
            }
            for (Map.Entry<Long, Integer> e : covered.entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) {
                    continue;
                }
                Product p = productRepository.findById(e.getKey()).orElse(null);
                usageRepository.save(com.sitegenius.whatsappbe.entity.SubscriptionUsage.builder()
                        .organizationId(orgId)
                        .subscriptionId(sub.getId())
                        .customerPhone(phone)
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .productId(e.getKey())
                        .productName(p == null ? "Item" : p.getName())
                        .categoryId(p == null || p.getCategory() == null ? 0L : p.getCategory().getId())
                        .quantity(e.getValue())
                        .usedAt(now)
                        .build());
            }
        } catch (Exception ex) {
            log.error("Saving subscription usage failed: {}", ex.getMessage(), ex);
        }
    }
}
