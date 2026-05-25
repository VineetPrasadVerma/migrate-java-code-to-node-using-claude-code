package com.company.orders.service;

import com.company.orders.model.OrderItem;
import com.company.orders.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks stock availability and reserves inventory for an order.
 *
 * Reservation strategy:
 *   - ALL items are checked before ANY stock is decremented (two-pass approach)
 *   - If allowPartialFulfillment=false and any item is short: return failure, no stock touched
 *   - If allowPartialFulfillment=true: remove short items, decrement available stock
 *
 * Releasing reservations:
 *   - Call releaseReservation() with the ORIGINAL item list if the order is subsequently
 *     rejected (e.g. by FraudDetectionService after inventory was already reserved)
 *   - IMPORTANT: releaseReservation() must be called with the same items that were passed
 *     to checkAndReserve() — it does NOT track which items were actually reserved internally.
 *
 * LEGACY NOTE: Stock decrement is NOT atomic across multiple products. If the JVM crashes
 * between decrements, stock levels will be inconsistent. The original system tolerated this
 * because order volumes were low and discrepancies were corrected by the nightly stock-sync job.
 */
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public static class InventoryResult {
        private final boolean available;
        private final List<String> unavailableMessages;
        private final List<OrderItem> fulfilledItems;

        private InventoryResult(boolean available, List<String> msgs, List<OrderItem> fulfilled) {
            this.available = available;
            this.unavailableMessages = msgs;
            this.fulfilledItems = fulfilled;
        }

        public static InventoryResult ok(List<OrderItem> items) {
            return new InventoryResult(true, new ArrayList<>(), items);
        }

        public static InventoryResult fail(List<String> messages) {
            return new InventoryResult(false, messages, new ArrayList<>());
        }

        public static InventoryResult partial(List<OrderItem> fulfilled, List<String> droppedMessages) {
            return new InventoryResult(true, droppedMessages, fulfilled);
        }

        public boolean isAvailable() { return available; }
        public List<String> getUnavailableMessages() { return unavailableMessages; }
        public List<OrderItem> getFulfilledItems() { return fulfilledItems; }
    }

    /**
     * Checks stock and reserves inventory for all items.
     *
     * @param items                   the order line items to reserve
     * @param allowPartialFulfillment if true, drop out-of-stock items and proceed
     * @return InventoryResult indicating availability and fulfilled items
     */
    public synchronized InventoryResult checkAndReserve(List<OrderItem> items,
                                                         boolean allowPartialFulfillment) {
        // Pass 1: identify stock problems
        List<String> shortages = new ArrayList<>();
        List<OrderItem> fulfilledItems = new ArrayList<>();

        for (OrderItem item : items) {
            var product = productRepository.findById(item.getProductId());
            if (product == null) {
                shortages.add("Product not found during inventory check: " + item.getProductId());
                continue;
            }
            if (product.getStockLevel() < item.getQuantity()) {
                shortages.add(String.format(
                    "Insufficient stock for product %s (%s): requested %d, available %d",
                    product.getId(), product.getName(), item.getQuantity(), product.getStockLevel()
                ));
            } else {
                fulfilledItems.add(item);
            }
        }

        // Fail-fast if partial fulfillment not allowed
        if (!shortages.isEmpty() && !allowPartialFulfillment) {
            return InventoryResult.fail(shortages);
        }

        // If partial fulfillment allowed, proceed with only the available items
        if (!fulfilledItems.isEmpty()) {
            // Pass 2: decrement stock for fulfilled items
            for (OrderItem item : fulfilledItems) {
                productRepository.decrementStock(item.getProductId(), item.getQuantity());
            }
        }

        if (shortages.isEmpty()) {
            return InventoryResult.ok(fulfilledItems);
        } else {
            // Partial fulfillment: some items dropped
            return InventoryResult.partial(fulfilledItems, shortages);
        }
    }

    /**
     * Restores previously-reserved stock. Called when an order is rejected after inventory
     * was already committed (e.g. fraud rejection).
     *
     * @param items the items whose stock should be restored (same list passed to checkAndReserve)
     */
    public synchronized void releaseReservation(List<OrderItem> items) {
        for (OrderItem item : items) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
        }
    }
}
