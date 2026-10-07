package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.entity.OrderItem;
import com.sitegenius.whatsappbe.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getAllOrders(organizationId)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getOrderById(
                        organizationId,
                        id
                )
        );
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getOrderByNumber(
            @PathVariable String orderNumber,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getOrderByNumber(
                        organizationId,
                        orderNumber
                )
        );
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<Order>> getOrdersByConversation(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getOrdersByConversation(
                        organizationId,
                        conversationId
                )
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getOrdersByStatus(
            @PathVariable Order.OrderStatus status,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getOrdersByStatus(
                        organizationId,
                        status
                )
        );
    }

    @GetMapping("/{orderId}/items")
    public ResponseEntity<List<OrderItem>> getOrderItems(
            @PathVariable Long orderId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.getOrderItems(
                        organizationId,
                        orderId
                )
        );
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @RequestBody Order order,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        // Never trust organizationId coming from frontend
        order.setOrganizationId(organizationId);

        return ResponseEntity.ok(
                orderService.createOrder(order)
        );
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.updateOrderStatus(
                        organizationId,
                        id,
                        status
                )
        );
    }

    @PutMapping("/{id}/payment")
    public ResponseEntity<Order> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam Order.PaymentStatus paymentStatus,
            @RequestParam(required = false) String paymentId,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        return ResponseEntity.ok(
                orderService.updatePaymentStatus(
                        organizationId,
                        id,
                        paymentStatus,
                        paymentId
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {

        Long organizationId = getOrganizationId(authentication);

        orderService.deleteOrder(
                organizationId,
                id
        );

        return ResponseEntity.noContent().build();
    }

    private Long getOrganizationId(Authentication authentication) {

        Object principal = authentication.getPrincipal();

        try {
            var method = principal.getClass()
                    .getMethod("getOrganizationId");

            Object value = method.invoke(principal);

            if (value == null) {
                throw new RuntimeException(
                        "Organization ID not found"
                );
            }

            return Long.valueOf(value.toString());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to determine organization ID",
                    e
            );
        }
    }
}