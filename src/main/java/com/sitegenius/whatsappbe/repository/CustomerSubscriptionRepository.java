package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.CustomerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, Long> {

    List<CustomerSubscription> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<CustomerSubscription> findByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(
            Long organizationId,
            String customerPhone
    );

    boolean existsByPlanId(Long planId);
}
