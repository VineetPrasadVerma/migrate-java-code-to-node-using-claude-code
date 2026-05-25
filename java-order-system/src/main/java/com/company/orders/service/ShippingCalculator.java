package com.company.orders.service;

import com.company.orders.model.*;
import com.company.orders.util.MoneyUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates the shipping cost for an order.
 *
 * Formula:
 *   baseRate        = ZONE_BASE_RATES[shippingZone]
 *   speedMultiplier = getSpeedMultiplier(shippingType)
 *   totalWeightKg   = sum of (product.weightKg × quantity) for all items
 *   weightSurcharge = max(0, totalWeightKg - WEIGHT_THRESHOLD_KG) × WEIGHT_SURCHARGE_PER_KG
 *   dangerousSurcharge = hasDangerousGood ? DANGEROUS_GOODS_SURCHARGE : 0
 *
 *   shippingCost = round((baseRate × speedMultiplier) + weightSurcharge + dangerousSurcharge)
 *
 * Free shipping conditions (in priority order):
 *   1. FREESHIP promo code → shipping = $0.00 (freeShipping parameter = true)
 *   2. PLATINUM tier + STANDARD type + NO dangerous goods → shipping = $0.00
 *   Both conditions reduce (baseRate × speedMultiplier + weightSurcharge) to $0.
 *   The dangerous goods surcharge is NOT waived under any free-shipping condition.
 *
 * Zone base rates:
 *   Zone 1 → $5.00    Zone 2 → $8.00    Zone 3 → $12.00
 *   Zone 4 → $18.00   Zone 5 → $25.00
 *
 * Speed multipliers:
 *   STANDARD → 1.0x    EXPRESS → 1.5x    OVERNIGHT → 2.5x
 *
 * Weight threshold: 5 kg.  Surcharge: $0.50/kg over threshold.
 * Dangerous goods flat surcharge: $15.00 per order (not per item).
 *
 * LEGACY NOTE: Zone base rates and surcharge amounts are hardcoded here.
 * The original spec documented them in a separate rate card spreadsheet (rate-card-v3.xlsx)
 * that no longer exists. These values were reconstructed from unit test assertions in 2021.
 */
public class ShippingCalculator {

    private static final double WEIGHT_THRESHOLD_KG       = 5.0;
    private static final double WEIGHT_SURCHARGE_PER_KG   = 0.50;
    private static final double DANGEROUS_GOODS_SURCHARGE = 15.00;

    private static final Map<Integer, Double> ZONE_BASE_RATES;

    static {
        Map<Integer, Double> zones = new HashMap<>();
        zones.put(1, 5.00);
        zones.put(2, 8.00);
        zones.put(3, 12.00);
        zones.put(4, 18.00);
        zones.put(5, 25.00);
        ZONE_BASE_RATES = Collections.unmodifiableMap(zones);
    }

    /**
     * Calculates the shipping cost for the order.
     *
     * @param order         the incoming order (for zone and type)
     * @param lineItems     priced line items (used to look up product weights and dangerous good flags)
     * @param products      product map keyed by productId (for weight and dangerous good lookup)
     * @param customer      ordering customer (PLATINUM gets free STANDARD shipping)
     * @param freeShipping  true if a FREESHIP promo has been applied
     * @return shipping cost in dollars, rounded to 2 decimal places
     */
    public double calculate(Order order, Map<String, Product> products,
                            Customer customer, boolean freeShipping) {

        List<OrderItem> items = order.getItems();

        // Determine if any item is a dangerous good
        boolean hasDangerousGood = items.stream()
            .map(item -> products.get(item.getProductId()))
            .anyMatch(p -> p != null && p.isDangerousGood());

        // Calculate total weight
        double totalWeightKg = items.stream()
            .mapToDouble(item -> {
                Product p = products.get(item.getProductId());
                return p == null ? 0.0 : p.getWeightKg() * item.getQuantity();
            })
            .sum();

        // Weight surcharge: $0.50 per kg above 5 kg threshold
        double excessWeightKg = Math.max(0.0, totalWeightKg - WEIGHT_THRESHOLD_KG);
        double weightSurcharge = MoneyUtils.round(excessWeightKg * WEIGHT_SURCHARGE_PER_KG);

        // Dangerous goods surcharge (never waived)
        double dangerousSurcharge = hasDangerousGood ? DANGEROUS_GOODS_SURCHARGE : 0.0;

        // Check for free shipping eligibility
        boolean platinumFreeStandard =
            customer.getTier() == CustomerTier.PLATINUM &&
            order.getShippingType() == ShippingType.STANDARD &&
            !hasDangerousGood;

        if (freeShipping || platinumFreeStandard) {
            // Base + weight are free; dangerous goods surcharge still applies
            return MoneyUtils.round(dangerousSurcharge);
        }

        // Standard calculation
        double baseRate = ZONE_BASE_RATES.getOrDefault(order.getShippingZone(), 25.00);
        double speedMultiplier = getSpeedMultiplier(order.getShippingType());
        double baseCharge = MoneyUtils.round(baseRate * speedMultiplier);

        return MoneyUtils.round(baseCharge + weightSurcharge + dangerousSurcharge);
    }

    private double getSpeedMultiplier(ShippingType type) {
        if (type == null) return 1.0;
        switch (type) {
            case EXPRESS:   return 1.5;
            case OVERNIGHT: return 2.5;
            case STANDARD:
            default:        return 1.0;
        }
    }
}
