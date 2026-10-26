package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.CustomerSubscription;
import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.SubscriptionPlan;
import com.sitegenius.whatsappbe.repository.CustomerSubscriptionRepository;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import com.sitegenius.whatsappbe.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final CustomerSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final OrderRepository orderRepository;

    public record MembershipRow(
            Long id,
            String customerName,
            String customerPhone,
            Long planId,
            String planName,
            Integer durationDays,
            String paymentMethod,
            String status,
            BigDecimal amount,
            String currency,
            LocalDateTime requestedAt,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long daysLeft
    ) {
    }

    public record MembershipOrder(
            Long id,
            String orderNumber,
            String status,
            String paymentStatus,
            BigDecimal totalAmount,
            String currency,
            int itemCount,
            LocalDateTime createdAt
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<MembershipRow>> list(Authentication authentication) {
        Long orgId = organizationId(authentication);

        Map<Long, SubscriptionPlan> plans = planRepository
                .findByOrganizationIdOrderBySortOrderAscIdAsc(orgId)
                .stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        List<MembershipRow> rows = subscriptionRepository
                .findByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(s -> toRow(s, plans.get(s.getPlanId()), now))
                .toList();

        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication authentication) {
        Long orgId = organizationId(authentication);
        CustomerSubscription sub = find(id, orgId);
        if (sub == null) {
            return ResponseEntity.notFound().build();
        }
        if (sub.getStatus() != CustomerSubscription.Status.PENDING_APPROVAL) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only pending requests can be approved."));
        }
        SubscriptionPlan plan = planRepository.findByIdAndOrganizationId(sub.getPlanId(), orgId).orElse(null);
        if (plan == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Plan not found."));
        }

        LocalDateTime now = LocalDateTime.now();
        sub.setStatus(CustomerSubscription.Status.ACTIVE);
        sub.setStartDate(now);
        sub.setEndDate(now.plusDays(plan.getDurationDays()));
        sub.setApprovedAt(now);
        subscriptionRepository.save(sub);
        return ResponseEntity.ok(toRow(sub, plan, now));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication authentication) {
        Long orgId = organizationId(authentication);
        CustomerSubscription sub = find(id, orgId);
        if (sub == null) {
            return ResponseEntity.notFound().build();
        }
        if (sub.getStatus() != CustomerSubscription.Status.PENDING_APPROVAL
                && sub.getStatus() != CustomerSubscription.Status.PENDING_PAYMENT) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only pending requests can be rejected."));
        }
        sub.setStatus(CustomerSubscription.Status.REJECTED);
        subscriptionRepository.save(sub);
        return ResponseEntity.ok(Map.of("message", "Rejected"));
    }

    // orders placed by this customer during the membership period
    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<MembershipOrder>> orders(@PathVariable Long id, Authentication authentication) {
        Long orgId = organizationId(authentication);
        CustomerSubscription sub = find(id, orgId);
        if (sub == null) {
            return ResponseEntity.notFound().build();
        }

        LocalDateTime from = sub.getStartDate() != null ? sub.getStartDate() : sub.getCreatedAt();
        LocalDateTime to = sub.getEndDate() != null ? sub.getEndDate() : LocalDateTime.now();
        if (to.isAfter(LocalDateTime.now())) {
            to = LocalDateTime.now();
        }

        List<MembershipOrder> result = orderRepository
                .findByOrganizationIdAndCustomerPhoneAndCreatedAtBetweenOrderByCreatedAtDesc(
                        orgId, sub.getCustomerPhone(), from, to)
                .stream()
                .map(o -> new MembershipOrder(
                        o.getId(),
                        o.getOrderNumber(),
                        o.getStatus().name(),
                        o.getPaymentStatus().name(),
                        o.getTotalAmount(),
                        o.getCurrency(),
                        o.getItems() == null ? 0 : o.getItems().size(),
                        o.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(result);
    }

    private CustomerSubscription find(Long id, Long orgId) {
        return subscriptionRepository.findById(id)
                .filter(s -> orgId.equals(s.getOrganizationId()))
                .orElse(null);
    }

    private MembershipRow toRow(CustomerSubscription s, SubscriptionPlan plan, LocalDateTime now) {
        String status = s.getStatus().name();
        Long daysLeft = null;
        if (s.getStatus() == CustomerSubscription.Status.ACTIVE && s.getEndDate() != null) {
            if (s.getEndDate().isBefore(now)) {
                status = "EXPIRED";
                daysLeft = 0L;
            } else {
                daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), s.getEndDate().toLocalDate());
            }
        }
        return new MembershipRow(
                s.getId(),
                s.getCustomerName(),
                s.getCustomerPhone(),
                s.getPlanId(),
                plan == null ? "Deleted plan" : plan.getName(),
                plan == null ? null : plan.getDurationDays(),
                s.getPaymentMethod().name(),
                status,
                s.getAmount(),
                s.getCurrency(),
                s.getCreatedAt(),
                s.getStartDate(),
                s.getEndDate(),
                daysLeft);
    }

    private Long organizationId(Authentication authentication) {
        try {
            Object details = authentication.getDetails();
            Object value = details.getClass().getMethod("getOrganizationId").invoke(details);
            return Long.valueOf(value.toString());
        } catch (Exception e) {
            throw new RuntimeException("Unable to determine organization from authentication", e);
        }
    }
}
