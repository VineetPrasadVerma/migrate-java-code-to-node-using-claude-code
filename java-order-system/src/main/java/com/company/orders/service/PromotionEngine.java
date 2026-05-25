package com.company.orders.service;

import com.company.orders.model.*;
import com.company.orders.repository.PromoCodeRepository;
import com.company.orders.util.MoneyUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Applies a promotional code to a priced order and returns the discount result.
 *
 * Eligibility checks (in order):
 *   1. Code must exist (should have been caught by ValidationService, but re-checked here)
 *   2. Code must not be expired: orderDate < promoCode.expiryDate
 *   3. Customer tier must be in promoCode.eligibleTiers (if non-null)
 *   4. If newCustomerOnly: customer account age must be < NEW_CUSTOMER_DAYS (30 days)
 *   5. For PERCENTAGE_IF_ABOVE: subtotal must be >= promoCode.minimumAmount
 *
 * If ANY eligibility check fails, the promo is rejected and a reason is returned.
 * The order still proceeds — the failed promo is added to warnings, not errors.
 *
 * Discount calculation:
 *   PERCENTAGE_SUBTOTAL:  discount = round(subtotal × discountValue / 100)
 *   PERCENTAGE_IF_ABOVE:  same as above if subtotal >= minimumAmount; else rejected
 *   FREE_SHIPPING:        discount = 0.00, freeShipping flag set to true
 *
 * The 'subtotal' parameter must be the sum of all lineItem.lineSubtotal values
 * (pre-tax, post tier/volume discount) from PricingEngine output.
 */
public class PromotionEngine {

    static final int NEW_CUSTOMER_DAYS = 30;

    private final PromoCodeRepository promoCodeRepository;

    public PromotionEngine(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    /**
     * Attempts to apply a promo code to the order subtotal.
     *
     * @param promoCodeStr  raw promo code string from the order (may be null)
     * @param subtotal      sum of lineItem.lineSubtotal values (pre-tax)
     * @param customer      the ordering customer
     * @param orderDate     the date of the order (for expiry checking); defaults to today if null
     * @return PromotionResult with discount amount and/or free shipping flag
     */
    public PromotionResult applyPromotion(String promoCodeStr, double subtotal,
                                           Customer customer, String orderDate) {
        if (promoCodeStr == null || promoCodeStr.trim().isEmpty()) {
            return PromotionResult.noPromo();
        }

        String code = promoCodeStr.trim().toUpperCase();
        PromoCode promo = promoCodeRepository.findByCode(code);

        if (promo == null) {
            return PromotionResult.invalid(code, "Promo code not found: " + code);
        }

        // Check 2: expiry
        LocalDate effectiveOrderDate = (orderDate != null && !orderDate.isEmpty())
            ? LocalDate.parse(orderDate)
            : LocalDate.now();

        LocalDate expiry = LocalDate.parse(promo.getExpiryDate());
        if (!effectiveOrderDate.isBefore(expiry)) {
            return PromotionResult.invalid(code,
                "Promo code '" + code + "' expired on " + promo.getExpiryDate());
        }

        // Check 3: tier eligibility
        List<CustomerTier> eligibleTiers = promo.getEligibleTiers();
        if (eligibleTiers != null && !eligibleTiers.isEmpty()) {
            if (!eligibleTiers.contains(customer.getTier())) {
                return PromotionResult.invalid(code,
                    "Promo code '" + code + "' is not available for " + customer.getTier() + " tier customers");
            }
        }

        // Check 4: new customer restriction
        if (promo.isNewCustomerOnly()) {
            long accountAgeDays = ChronoUnit.DAYS.between(
                LocalDate.parse(customer.getRegistrationDate()),
                effectiveOrderDate
            );
            if (accountAgeDays >= NEW_CUSTOMER_DAYS) {
                return PromotionResult.invalid(code,
                    "Promo code '" + code + "' is restricted to new customers (account < " +
                    NEW_CUSTOMER_DAYS + " days). Account age: " + accountAgeDays + " days");
            }
        }

        // Apply by type
        switch (promo.getType()) {
            case "FREE_SHIPPING":
                return PromotionResult.freeShipping(code);

            case "PERCENTAGE_SUBTOTAL": {
                double discount = MoneyUtils.round(subtotal * promo.getDiscountValue() / 100.0);
                return PromotionResult.discount(code, discount);
            }

            case "PERCENTAGE_IF_ABOVE": {
                // Check 5: minimum subtotal
                if (subtotal < promo.getMinimumAmount()) {
                    return PromotionResult.invalid(code,
                        "Promo code '" + code + "' requires a subtotal of $" +
                        String.format("%.2f", promo.getMinimumAmount()) +
                        " (order subtotal: $" + String.format("%.2f", subtotal) + ")");
                }
                double discount = MoneyUtils.round(subtotal * promo.getDiscountValue() / 100.0);
                return PromotionResult.discount(code, discount);
            }

            default:
                return PromotionResult.invalid(code,
                    "Unknown promo code type '" + promo.getType() + "' for code: " + code);
        }
    }
}
