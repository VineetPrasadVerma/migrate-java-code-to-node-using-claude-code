package com.company.orders.model;

/**
 * Result returned by PromotionEngine after attempting to apply a promo code.
 *
 * If the promo code is invalid or ineligible, valid=false and discountAmount=0.
 * The invalidReason is included in the OrderResult warnings list.
 *
 * freeShipping=true means ShippingCalculator should return $0.00 for this order.
 */
public class PromotionResult {
    private String appliedCode;
    private double discountAmount;
    private boolean freeShipping;
    private boolean valid;
    private String invalidReason;

    public static PromotionResult noPromo() {
        PromotionResult r = new PromotionResult();
        r.valid = true;
        r.discountAmount = 0.0;
        r.freeShipping = false;
        return r;
    }

    public static PromotionResult invalid(String code, String reason) {
        PromotionResult r = new PromotionResult();
        r.appliedCode = code;
        r.valid = false;
        r.discountAmount = 0.0;
        r.freeShipping = false;
        r.invalidReason = reason;
        return r;
    }

    public static PromotionResult discount(String code, double amount) {
        PromotionResult r = new PromotionResult();
        r.appliedCode = code;
        r.valid = true;
        r.discountAmount = amount;
        r.freeShipping = false;
        return r;
    }

    public static PromotionResult freeShipping(String code) {
        PromotionResult r = new PromotionResult();
        r.appliedCode = code;
        r.valid = true;
        r.discountAmount = 0.0;
        r.freeShipping = true;
        return r;
    }

    public String getAppliedCode() { return appliedCode; }
    public void setAppliedCode(String appliedCode) { this.appliedCode = appliedCode; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public boolean isFreeShipping() { return freeShipping; }
    public void setFreeShipping(boolean freeShipping) { this.freeShipping = freeShipping; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
}
