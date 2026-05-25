package com.company.orders.service;

import com.company.orders.model.*;
import com.company.orders.util.MoneyUtils;

import java.util.*;

/**
 * Calculates per-line pricing for all items in an order.
 *
 * Pricing formula (applied per line item):
 *
 *   tierDiscount    = TIER_DISCOUNTS[customer.tier]         (0% BRONZE → 15% PLATINUM)
 *   volumeDiscount  = getVolumeDiscount(item.quantity)      (0% for <5 units → 12% for 20+)
 *   combinedDiscount = min(tierDiscount + volumeDiscount, MAX_DISCOUNT_CAP)  [cap: 40%]
 *
 *   discountedUnitPrice = round(basePrice × (1 − combinedDiscount))
 *   lineSubtotal        = round(discountedUnitPrice × quantity)      ← pre-tax
 *   taxRate             = TAX_RATES[product.category]
 *   taxAmount           = round(lineSubtotal × taxRate)
 *   lineTotal           = round(lineSubtotal + taxAmount)
 *
 * KEY DESIGN DECISIONS (document these for migration):
 *   - Tier and volume discounts are ADDITIVE (not compounded): total = tier% + volume%
 *   - Combined discount is CAPPED at MAX_DISCOUNT_CAP (40%) to protect margins
 *   - Tax is computed on the post-discount, PRE-PROMO price
 *     (promo codes do not reduce tax liability — they reduce the pre-tax subtotal only)
 *   - All rounding is HALF_UP to 2 decimal places, applied after EACH multiplication
 *     to prevent floating-point drift accumulation
 *
 * Volume discount brackets:
 *   qty  1-4  → 0%
 *   qty  5-9  → 3%
 *   qty 10-19 → 7%
 *   qty 20+   → 12%
 */
public class PricingEngine {

    private static final double MAX_DISCOUNT_CAP = 0.40;

    private static final Map<CustomerTier, Double> TIER_DISCOUNTS;
    private static final Map<ProductCategory, Double> TAX_RATES;

    static {
        Map<CustomerTier, Double> tiers = new EnumMap<>(CustomerTier.class);
        tiers.put(CustomerTier.BRONZE,   0.00);
        tiers.put(CustomerTier.SILVER,   0.05);
        tiers.put(CustomerTier.GOLD,     0.10);
        tiers.put(CustomerTier.PLATINUM, 0.15);
        TIER_DISCOUNTS = Collections.unmodifiableMap(tiers);

        Map<ProductCategory, Double> taxes = new EnumMap<>(ProductCategory.class);
        taxes.put(ProductCategory.ELECTRONICS, 0.18);
        taxes.put(ProductCategory.CLOTHING,    0.05);
        taxes.put(ProductCategory.FOOD,        0.00);
        taxes.put(ProductCategory.MEDICAL,     0.12);
        taxes.put(ProductCategory.GENERAL,     0.08);
        TAX_RATES = Collections.unmodifiableMap(taxes);
    }

    /**
     * Calculates the fully-priced line items for an order.
     *
     * @param items    raw order lines (validated — all productIds exist)
     * @param customer the ordering customer (tier used for discount lookup)
     * @param products product map keyed by productId
     * @return list of ProcessedOrderItem, one per input item, in the same order
     */
    public List<ProcessedOrderItem> calculateLineItems(
            List<OrderItem> items,
            Customer customer,
            Map<String, Product> products) {

        double tierDiscountRate = TIER_DISCOUNTS.get(customer.getTier());
        List<ProcessedOrderItem> result = new ArrayList<>();

        for (OrderItem item : items) {
            Product product = products.get(item.getProductId());

            double volumeDiscountRate = getVolumeDiscount(item.getQuantity());
            double combinedDiscount = Math.min(tierDiscountRate + volumeDiscountRate, MAX_DISCOUNT_CAP);

            double discountedUnitPrice = MoneyUtils.round(product.getBasePrice() * (1.0 - combinedDiscount));
            double lineSubtotal        = MoneyUtils.round(discountedUnitPrice * item.getQuantity());
            double taxRate             = TAX_RATES.get(product.getCategory());
            double taxAmount           = MoneyUtils.round(lineSubtotal * taxRate);
            double lineTotal           = MoneyUtils.round(lineSubtotal + taxAmount);

            result.add(new ProcessedOrderItem(
                product.getId(),
                product.getName(),
                product.getCategory(),
                item.getQuantity(),
                product.getBasePrice(),
                tierDiscountRate,
                volumeDiscountRate,
                discountedUnitPrice,
                taxRate,
                taxAmount,
                lineSubtotal,
                lineTotal
            ));
        }

        return result;
    }

    /**
     * Returns the volume discount rate for a given quantity.
     * Brackets: <5 → 0%, 5-9 → 3%, 10-19 → 7%, 20+ → 12%.
     */
    private double getVolumeDiscount(int quantity) {
        if (quantity >= 20) return 0.12;
        if (quantity >= 10) return 0.07;
        if (quantity >= 5)  return 0.03;
        return 0.00;
    }

    /** Exposes tier discount lookup for use by other services (e.g. display purposes). */
    public static double getTierDiscountRate(CustomerTier tier) {
        return TIER_DISCOUNTS.getOrDefault(tier, 0.0);
    }

    /** Exposes tax rate lookup for use by other services. */
    public static double getTaxRate(ProductCategory category) {
        return TAX_RATES.getOrDefault(category, 0.0);
    }
}
