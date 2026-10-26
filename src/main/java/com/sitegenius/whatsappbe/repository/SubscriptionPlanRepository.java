package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByOrganizationIdOrderBySortOrderAscIdAsc(Long organizationId);

    List<SubscriptionPlan> findByOrganizationIdAndActiveTrueOrderBySortOrderAscIdAsc(Long organizationId);

    Optional<SubscriptionPlan> findByIdAndOrganizationId(Long id, Long organizationId);
}
