package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Order;
import com.sitegenius.whatsappbe.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tells the customer on WhatsApp whenever an order's status changes,
 * no matter where the change was made (dashboard, API, SQL).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusNotifier {

    private final OrderRepository orderRepository;
    private final OrderingService orderingService;

    @Scheduled(fixedDelay = 15000, initialDelay = 30000)
    public void run() {
        try {
            for (Order order : orderRepository.findPendingStatusNotifications()) {
                orderingService.notifyStatusChange(order);

                if (order.getStatus() != null) {
                    orderRepository.markStatusNotified(order.getId(), order.getStatus().name());
                }
            }
        } catch (Exception e) {
            log.warn("Order status notifier failed: {}", e.getMessage());
        }
    }
}
