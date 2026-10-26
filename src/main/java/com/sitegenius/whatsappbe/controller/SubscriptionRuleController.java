package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.SubscriptionRule;
import com.sitegenius.whatsappbe.repository.SubscriptionPlanRepository;
import com.sitegenius.whatsappbe.repository.SubscriptionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription-plans/{planId}/rules")
@RequiredArgsConstructor
public class SubscriptionRuleController {

    private final SubscriptionRuleRepository ruleRepository;
    private final SubscriptionPlanRepository planRepository;

    public record RuleRequest(
            String appliesTo,
            Long targetId,
            Integer quantity,
            String period
    ) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<?> list(@PathVariable Long planId, Authentication authentication) {
        Long orgId = organizationId(authentication);
        if (planRepository.findByIdAndOrganizationId(planId, orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                ruleRepository.findByOrganizationIdAndPlanIdOrderByIdAsc(orgId, planId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> replace(
            @PathVariable Long planId,
            @RequestBody List<RuleRequest> body,
            Authentication authentication
    ) {
        Long orgId = organizationId(authentication);
        if (planRepository.findByIdAndOrganizationId(planId, orgId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SubscriptionRule> rules = new ArrayList<>();
        for (RuleRequest r : body == null ? List.<RuleRequest>of() : body) {
            SubscriptionRule.AppliesTo appliesTo;
            SubscriptionRule.Period period;
            try {
                appliesTo = SubscriptionRule.AppliesTo.valueOf(String.valueOf(r.appliesTo()).trim().toUpperCase());
                period = SubscriptionRule.Period.valueOf(String.valueOf(r.period()).trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid rule type or period"));
            }

            if (r.quantity() == null || r.quantity() < 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "Quantity must be at least 1"));
            }
            if (appliesTo != SubscriptionRule.AppliesTo.ANY && r.targetId() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Choose a category or product"));
            }

            SubscriptionRule rule = new SubscriptionRule();
            rule.setOrganizationId(orgId);
            rule.setPlanId(planId);
            rule.setAppliesTo(appliesTo);
            rule.setTargetId(appliesTo == SubscriptionRule.AppliesTo.ANY ? null : r.targetId());
            rule.setQuantity(r.quantity());
            rule.setPeriod(period);
            rules.add(rule);
        }

        ruleRepository.deleteByOrganizationIdAndPlanId(orgId, planId);
        return ResponseEntity.ok(ruleRepository.saveAll(rules));
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
