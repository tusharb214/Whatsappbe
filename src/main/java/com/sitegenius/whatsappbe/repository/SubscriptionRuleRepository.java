package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.SubscriptionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionRuleRepository extends JpaRepository<SubscriptionRule, Long> {

    List<SubscriptionRule> findByOrganizationIdAndPlanIdOrderByIdAsc(Long organizationId, Long planId);

    @Transactional
    void deleteByOrganizationIdAndPlanId(Long organizationId, Long planId);
}
