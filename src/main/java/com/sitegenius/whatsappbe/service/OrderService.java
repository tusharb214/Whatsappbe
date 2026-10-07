package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.OrderItem;
import com.sitegenius.whatsappbe.repository.OrderItemRepository;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public List<Order> getAllOrders(Long organizationId) {
        return orderRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    public Order getOrderById(Long organizationId, Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new RuntimeException("Order not found")
                );

        if (!order.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Access denied");
        }

        return order;
    }

    public Order getOrderByNumber(Long organizationId, String orderNumber) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() ->
                        new RuntimeException("Order not found")
                );

        if (!order.getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Access denied");
        }

        return order;
    }

    public List<Order> getOrdersByConversation(
            Long organizationId,
            Long conversationId
    ) {
        return orderRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId)
                .stream()
                .filter(order ->
                        order.getOrganizationId().equals(organizationId)
                )
                .toList();
    }

    public List<Order> getOrdersByStatus(
            Long organizationId,
            Order.OrderStatus status
    ) {
        return orderRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        organizationId,
                        status
                );
    }

    public Order createOrder(Order order) {

        if (order.getOrganizationId() == null) {
            throw new RuntimeException("Organization ID is required");
        }

        if (order.getConversationId() == null) {
            throw new RuntimeException("Conversation ID is required");
        }

        if (order.getContactId() == null) {
            throw new RuntimeException("Contact ID is required");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new RuntimeException("Order must contain at least one item");
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {

            if (item.getProductId() == null) {
                throw new RuntimeException("Product ID is required");
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException(
                        "Quantity must be greater than zero"
                );
            }

            if (item.getUnitPrice() == null) {
                throw new RuntimeException("Unit price is required");
            }

            BigDecimal itemTotal = item.getUnitPrice()
                    .multiply(
                            BigDecimal.valueOf(item.getQuantity())
                    );

            item.setTotalPrice(itemTotal);

            subtotal = subtotal.add(itemTotal);

            item.setOrder(order);
        }

        order.setSubtotal(subtotal);

        if (order.getTaxAmount() == null) {
            order.setTaxAmount(BigDecimal.ZERO);
        }

        if (order.getDiscountAmount() == null) {
            order.setDiscountAmount(BigDecimal.ZERO);
        }

        BigDecimal total = subtotal
                .add(order.getTaxAmount())
                .subtract(order.getDiscountAmount());

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        order.setTotalAmount(total);

        if (order.getCurrency() == null ||
                order.getCurrency().isBlank()) {
            order.setCurrency("INR");
        }

        if (order.getStatus() == null) {
            order.setStatus(Order.OrderStatus.NEW);
        }

        if (order.getPaymentStatus() == null) {
            order.setPaymentStatus(Order.PaymentStatus.PENDING);
        }

        return orderRepository.save(order);
    }

    public Order updateOrderStatus(
            Long organizationId,
            Long orderId,
            Order.OrderStatus status
    ) {

        Order order = getOrderById(organizationId, orderId);

        order.setStatus(status);

        return orderRepository.save(order);
    }

    public Order updatePaymentStatus(
            Long organizationId,
            Long orderId,
            Order.PaymentStatus paymentStatus,
            String paymentId
    ) {

        Order order = getOrderById(organizationId, orderId);

        order.setPaymentStatus(paymentStatus);

        if (paymentId != null && !paymentId.isBlank()) {
            order.setPaymentId(paymentId);
        }

        return orderRepository.save(order);
    }

    public void deleteOrder(Long organizationId, Long orderId) {

        Order order = getOrderById(organizationId, orderId);

        orderRepository.delete(order);
    }

    public List<OrderItem> getOrderItems(
            Long organizationId,
            Long orderId
    ) {

        getOrderById(organizationId, orderId);

        return orderItemRepository.findByOrderId(orderId);
    }
}