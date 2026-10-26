package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.SubscriptionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {

    List<SubscriptionUsage> findByOrganizationIdAndCustomerPhoneAndUsedAtBetweenOrderByUsedAtDesc(
            Long organizationId, String customerPhone, LocalDateTime from, LocalDateTime to);

    List<SubscriptionUsage> findBySubscriptionIdOrderByUsedAtDesc(Long subscriptionId);
}
