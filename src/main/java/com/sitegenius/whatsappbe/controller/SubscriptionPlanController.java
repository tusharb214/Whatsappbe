package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.SubscriptionPlan;
import com.sitegenius.whatsappbe.repository.CustomerSubscriptionRepository;
import com.sitegenius.whatsappbe.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanRepository planRepository;
    private final CustomerSubscriptionRepository subscriptionRepository;
    private final com.sitegenius.whatsappbe.repository.SubscriptionRuleRepository ruleRepository;

    public record PlanRequest(
            String name,
            String description,
            Integer durationDays,
            BigDecimal price,
            String currency,
            String features,
            String instructions,
            String imageUrl,
            Boolean allowOnline,
            Boolean allowCash,
            Boolean active,
            Integer sortOrder
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<SubscriptionPlan>> list(Authentication authentication) {
        Long orgId = organizationId(authentication);
        return ResponseEntity.ok(
                planRepository.findByOrganizationIdOrderBySortOrderAscIdAsc(orgId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> create(@RequestBody PlanRequest body, Authentication authentication) {
        Long orgId = organizationId(authentication);

        String error = validate(body);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setOrganizationId(orgId);
        apply(plan, body);
        return ResponseEntity.ok(planRepository.save(plan));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody PlanRequest body,
            Authentication authentication
    ) {
        Long orgId = organizationId(authentication);

        SubscriptionPlan plan = planRepository.findByIdAndOrganizationId(id, orgId).orElse(null);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }

        String error = validate(body);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }

        apply(plan, body);
        return ResponseEntity.ok(planRepository.save(plan));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        Long orgId = organizationId(authentication);

        SubscriptionPlan plan = planRepository.findByIdAndOrganizationId(id, orgId).orElse(null);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }

        if (subscriptionRepository.existsByPlanId(id)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "This plan has subscribers. Deactivate it instead of deleting."));
        }

        ruleRepository.deleteByOrganizationIdAndPlanId(orgId, id);
        planRepository.delete(plan);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    private String validate(PlanRequest b) {
        if (b == null || b.name() == null || b.name().isBlank()) {
            return "Plan name is required";
        }
        if (b.durationDays() == null || b.durationDays() < 1) {
            return "Duration must be at least 1 day";
        }
        if (b.price() == null || b.price().signum() < 0) {
            return "Price is invalid";
        }
        boolean online = b.allowOnline() == null || b.allowOnline();
        boolean cash = b.allowCash() == null || b.allowCash();
        if (!online && !cash) {
            return "Enable at least one payment method";
        }
        return null;
    }

    private void apply(SubscriptionPlan plan, PlanRequest b) {
        plan.setName(b.name().trim());
        plan.setDescription(b.description());
        plan.setDurationDays(b.durationDays());
        plan.setPrice(b.price());
        plan.setCurrency(b.currency() == null || b.currency().isBlank() ? "INR" : b.currency().trim());
        plan.setFeatures(b.features());
        plan.setInstructions(b.instructions());
        plan.setImageUrl(b.imageUrl());
        plan.setAllowOnline(b.allowOnline() == null || b.allowOnline());
        plan.setAllowCash(b.allowCash() == null || b.allowCash());
        plan.setActive(b.active() == null || b.active());
        plan.setSortOrder(b.sortOrder() == null ? 0 : b.sortOrder());
    }

    // same pattern the other controllers use to read the organization from the JWT details
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
