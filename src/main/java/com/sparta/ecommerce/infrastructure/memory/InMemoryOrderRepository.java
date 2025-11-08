package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InMemoryOrderRepository implements OrderRepository {

    private final InMemoryDataStore dataStore;

    @Override
    public void save(Order order) {
        dataStore.getOrders().put(order.getOrderId(), order);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(dataStore.getOrders().get(orderId));
    }

    @Override
    public List<Order> findByUserId(String userId) {
        return dataStore.getOrders().values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .toList();
    }

    @Override
    public List<Order> findByUserIdAndStatus(String userId, OrderStatus status) {
        return dataStore.getOrders().values().stream()
                .filter(order -> order.getUserId().equals(userId))
                .filter(order -> order.getStatus() == status)
                .toList();
    }
}
