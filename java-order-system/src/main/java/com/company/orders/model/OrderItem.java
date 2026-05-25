package com.company.orders.model;

/**
 * A single line in an incoming order request.
 * Quantity must be between 1 and 100 inclusive (validated by ValidationService).
 */
public class OrderItem {
    private String productId;
    private int quantity;

    public OrderItem() {}

    public OrderItem(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
