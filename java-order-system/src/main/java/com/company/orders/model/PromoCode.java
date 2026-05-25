package com.company.orders.model;

import java.util.List;

/**
 * Promotional code definition stored in PromoCodeRepository.
 *
 * type values and their behaviour:
 *   PERCENTAGE_SUBTOTAL  - discountValue % off pre-tax order subtotal, no minimum required
 *   PERCENTAGE_IF_ABOVE  - discountValue % off pre-tax subtotal, only if subtotal >= minimumAmount
 *   FREE_SHIPPING        - makes shipping cost $0.00 (discountValue and minimumAmount unused)
 *
 * eligibleTiers: null means all tiers are eligible. Non-null list restricts to listed tiers only.
 *
 * newCustomerOnly: if true, customer's account must be < 30 days old to use this code.
 *
 * expiryDate: "YYYY-MM-DD" string. Code is invalid if orderDate >= expiryDate.
 */
public class PromoCode {
    private String code;
    private String type;                    // PERCENTAGE_SUBTOTAL | PERCENTAGE_IF_ABOVE | FREE_SHIPPING
    private double discountValue;           // percentage (0-100), ignored for FREE_SHIPPING
    private double minimumAmount;           // minimum subtotal for PERCENTAGE_IF_ABOVE
    private String expiryDate;             // "YYYY-MM-DD"
    private boolean newCustomerOnly;        // true = account must be < 30 days old
    private List<CustomerTier> eligibleTiers; // null = all tiers eligible

    public PromoCode() {}

    public PromoCode(String code, String type, double discountValue, double minimumAmount,
                     String expiryDate, boolean newCustomerOnly, List<CustomerTier> eligibleTiers) {
        this.code = code;
        this.type = type;
        this.discountValue = discountValue;
        this.minimumAmount = minimumAmount;
        this.expiryDate = expiryDate;
        this.newCustomerOnly = newCustomerOnly;
        this.eligibleTiers = eligibleTiers;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }

    public double getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(double minimumAmount) { this.minimumAmount = minimumAmount; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public boolean isNewCustomerOnly() { return newCustomerOnly; }
    public void setNewCustomerOnly(boolean newCustomerOnly) { this.newCustomerOnly = newCustomerOnly; }

    public List<CustomerTier> getEligibleTiers() { return eligibleTiers; }
    public void setEligibleTiers(List<CustomerTier> eligibleTiers) { this.eligibleTiers = eligibleTiers; }
}
