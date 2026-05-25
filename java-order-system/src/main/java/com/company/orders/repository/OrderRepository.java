package com.company.orders.repository;

import com.company.orders.model.OrderResult;
import com.company.orders.model.OrderStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory order store.
 *
 * Stores completed OrderResult records (both PROCESSED and REJECTED outcomes).
 * Used by FraudDetectionService to check order velocity per customer.
 *
 * LEGACY NOTE: This store uses a synchronized HashMap, which was the idiomatic
 * concurrency pattern when this module was first written (~2017). A ConcurrentHashMap
 * or proper database-backed store would be more appropriate today.
 */
public class OrderRepository {

    // orderId -> OrderResult
    private final Map<String, OrderResult> store = Collections.synchronizedMap(new HashMap<>());

    public void save(OrderResult result) {
        store.put(result.getOrderId(), result);
    }

    public OrderResult findById(String orderId) {
        return store.get(orderId);
    }

    public boolean exists(String orderId) {
        return store.containsKey(orderId);
    }

    /**
     * Returns all orders for a given customer, optionally filtered by status.
     * Used by FraudDetectionService for velocity checks.
     *
     * @param customerId  customer to query
     * @param statusFilter only include orders with this status; null means all statuses
     */
    public List<OrderResult> findByCustomer(String customerId, OrderStatus statusFilter) {
        return store.values().stream()
            .filter(o -> {
                // OrderResult doesn't carry customerId — this is a known gap.
                // In the real system this query hits an indexed database column.
                // Here we rely on orderId convention: orderId starts with customerId.
                // e.g. "C001-ORD-001"
                // For now, velocity check is stubbed via orderId prefix matching.
                return o.getOrderId() != null && o.getOrderId().startsWith(customerId + "-");
            })
            .filter(o -> statusFilter == null || o.getStatus() == statusFilter)
            .collect(Collectors.toList());
    }

    public Collection<OrderResult> findAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    public int count() {
        return store.size();
    }
}
