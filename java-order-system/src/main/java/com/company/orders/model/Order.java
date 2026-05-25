package com.company.orders.model;

import java.util.List;

/**
 * The incoming order request submitted by the client.
 *
 * shippingZone: integer 1-5 representing geographic shipping zone.
 *   Zone 1 = closest/cheapest, Zone 5 = furthest/most expensive.
 *   Zone is determined by the caller based on the delivery address — this system
 *   does not perform address-to-zone resolution.
 *
 * allowPartialFulfillment: if true and some items are out of stock, those items
 *   are silently dropped and the order proceeds with available items only.
 *   If false (default), any out-of-stock item causes the entire order to be rejected.
 *
 * orderDate: ISO date string "YYYY-MM-DD". Defaults to today if not provided.
 *   Used for promo code expiry validation.
 */
public class Order {
    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private String promoCode;                      // optional, null if none
    private int shippingZone;                      // 1-5
    private ShippingType shippingType;             // STANDARD, EXPRESS, OVERNIGHT
    private boolean allowPartialFulfillment;       // default false
    private String orderDate;                      // "YYYY-MM-DD", optional

    public Order() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public int getShippingZone() { return shippingZone; }
    public void setShippingZone(int shippingZone) { this.shippingZone = shippingZone; }

    public ShippingType getShippingType() { return shippingType; }
    public void setShippingType(ShippingType shippingType) { this.shippingType = shippingType; }

    public boolean isAllowPartialFulfillment() { return allowPartialFulfillment; }
    public void setAllowPartialFulfillment(boolean allowPartialFulfillment) {
        this.allowPartialFulfillment = allowPartialFulfillment;
    }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }
}
