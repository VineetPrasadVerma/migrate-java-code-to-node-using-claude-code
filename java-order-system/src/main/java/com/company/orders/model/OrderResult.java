package com.company.orders.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete output of processing an order through the pipeline.
 *
 * Grand total formula:
 *   grandTotal = subtotal - promoDiscount + totalTax + shippingCost
 *
 * Where:
 *   subtotal     = sum of all lineItem.lineSubtotal (pre-tax, post tier/volume discount)
 *   totalTax     = sum of all lineItem.taxAmount    (computed pre-promo, on item-level discounted prices)
 *   promoDiscount = promo code discount applied on subtotal (NOT on tax)
 *   shippingCost  = 0.00 if FREESHIP promo or PLATINUM + STANDARD with no dangerous goods
 *
 * Note: tax is computed on discounted unit prices BEFORE promo code deduction.
 * Promo codes reduce the pre-tax subtotal, not the tax liability.
 *
 * fraudFlags: present in both PROCESSED (with warnings) and REJECTED outcomes.
 * errors: validation or inventory failure messages that caused REJECTED status.
 */
public class OrderResult {

    /** Nested representation of a fraud flag raised against this order. */
    public static class FraudFlagResult {
        private FraudFlag flag;
        private String severity;
        private String description;

        public FraudFlagResult() {}

        public FraudFlagResult(FraudFlag flag, String description) {
            this.flag = flag;
            this.severity = flag.getSeverity();
            this.description = description;
        }

        public FraudFlag getFlag() { return flag; }
        public void setFlag(FraudFlag flag) { this.flag = flag; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private String orderId;
    private OrderStatus status;
    private List<ProcessedOrderItem> lineItems;
    private double subtotal;
    private double totalTax;
    private String promoCode;
    private double promoDiscount;
    private double shippingCost;
    private double grandTotal;
    private List<FraudFlagResult> fraudFlags;
    private List<String> warnings;
    private List<String> errors;
    private String message;
    private String processedAt;

    public OrderResult() {
        this.lineItems = new ArrayList<>();
        this.fraudFlags = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public List<ProcessedOrderItem> getLineItems() { return lineItems; }
    public void setLineItems(List<ProcessedOrderItem> lineItems) { this.lineItems = lineItems; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getTotalTax() { return totalTax; }
    public void setTotalTax(double totalTax) { this.totalTax = totalTax; }

    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

    public double getPromoDiscount() { return promoDiscount; }
    public void setPromoDiscount(double promoDiscount) { this.promoDiscount = promoDiscount; }

    public double getShippingCost() { return shippingCost; }
    public void setShippingCost(double shippingCost) { this.shippingCost = shippingCost; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public List<FraudFlagResult> getFraudFlags() { return fraudFlags; }
    public void setFraudFlags(List<FraudFlagResult> fraudFlags) { this.fraudFlags = fraudFlags; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }
}
