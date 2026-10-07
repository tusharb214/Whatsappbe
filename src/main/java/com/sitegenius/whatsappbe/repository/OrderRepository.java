package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<Order> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByWhatsappOrderId(String whatsappOrderId);

    List<Order> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            Long organizationId,
            Order.OrderStatus status
    );
}