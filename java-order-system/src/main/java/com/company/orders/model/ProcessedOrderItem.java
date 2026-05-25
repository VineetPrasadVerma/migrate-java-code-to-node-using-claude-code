package com.company.orders.model;

/**
 * The fully-priced representation of an order line item, produced by PricingEngine.
 *
 * Pricing formula per line:
 *   combinedDiscount     = min(tierDiscountRate + volumeDiscountRate, 0.40)  [capped at 40%]
 *   discountedUnitPrice  = round(baseUnitPrice * (1 - combinedDiscount))
 *   lineSubtotal         = round(discountedUnitPrice * quantity)             [pre-tax]
 *   taxAmount            = round(lineSubtotal * taxRate)
 *   lineTotal            = round(lineSubtotal + taxAmount)
 *
 * Promo code discounts are applied at the ORDER level (not line level) by PromotionEngine.
 * Tax is computed on pre-promo discounted prices — promo codes do NOT reduce tax liability.
 */
public class ProcessedOrderItem {
    private String productId;
    private String productName;
    private ProductCategory category;
    private int quantity;
    private double baseUnitPrice;
    private double tierDiscountRate;    // e.g. 0.10 for Gold
    private double volumeDiscountRate;  // e.g. 0.03 for qty 5-9
    private double discountedUnitPrice;
    private double taxRate;
    private double taxAmount;           // total tax for the line (all units)
    private double lineSubtotal;        // pre-tax line total
    private double lineTotal;           // lineSubtotal + taxAmount

    public ProcessedOrderItem() {}

    public ProcessedOrderItem(String productId, String productName, ProductCategory category,
                               int quantity, double baseUnitPrice, double tierDiscountRate,
                               double volumeDiscountRate, double discountedUnitPrice,
                               double taxRate, double taxAmount, double lineSubtotal, double lineTotal) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.baseUnitPrice = baseUnitPrice;
        this.tierDiscountRate = tierDiscountRate;
        this.volumeDiscountRate = volumeDiscountRate;
        this.discountedUnitPrice = discountedUnitPrice;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.lineSubtotal = lineSubtotal;
        this.lineTotal = lineTotal;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getBaseUnitPrice() { return baseUnitPrice; }
    public void setBaseUnitPrice(double baseUnitPrice) { this.baseUnitPrice = baseUnitPrice; }

    public double getTierDiscountRate() { return tierDiscountRate; }
    public void setTierDiscountRate(double tierDiscountRate) { this.tierDiscountRate = tierDiscountRate; }

    public double getVolumeDiscountRate() { return volumeDiscountRate; }
    public void setVolumeDiscountRate(double volumeDiscountRate) { this.volumeDiscountRate = volumeDiscountRate; }

    public double getDiscountedUnitPrice() { return discountedUnitPrice; }
    public void setDiscountedUnitPrice(double discountedUnitPrice) { this.discountedUnitPrice = discountedUnitPrice; }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }

    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }

    public double getLineSubtotal() { return lineSubtotal; }
    public void setLineSubtotal(double lineSubtotal) { this.lineSubtotal = lineSubtotal; }

    public double getLineTotal() { return lineTotal; }
    public void setLineTotal(double lineTotal) { this.lineTotal = lineTotal; }
}
