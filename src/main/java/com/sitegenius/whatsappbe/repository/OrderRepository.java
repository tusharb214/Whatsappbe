package com.sitegenius.whatsappbe.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.sitegenius.whatsappbe.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<Order> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByOrganizationIdAndCustomerPhoneAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long organizationId,
            String customerPhone,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to
    );

    Optional<Order> findByWhatsappOrderId(String whatsappOrderId);

    List<Order> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            Long organizationId,
            Order.OrderStatus status
    );

    List<Order> findTop10ByOrganizationIdAndCustomerPhoneOrderByCreatedAtDesc(
            Long organizationId,
            String customerPhone
    );

    @Query(
            value = "select * from orders where last_notified_status is not null and last_notified_status <> status",
            nativeQuery = true
    )
    List<Order> findPendingStatusNotifications();

    @Modifying
    @Transactional
    @Query(
            value = "update orders set last_notified_status = :status where id = :id",
            nativeQuery = true
    )
    int markStatusNotified(@Param("id") Long id, @Param("status") String status);
}
