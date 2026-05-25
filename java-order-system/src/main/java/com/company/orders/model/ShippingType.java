package com.company.orders.model;

/**
 * Shipping speed option. Affects shipping cost multiplier.
 *
 * Multipliers applied in ShippingCalculator:
 *   STANDARD  -> 1.0x base rate
 *   EXPRESS   -> 1.5x base rate
 *   OVERNIGHT -> 2.5x base rate
 *
 * PLATINUM customers receive free STANDARD shipping unless dangerous goods are present.
 */
public enum ShippingType {
    STANDARD,
    EXPRESS,
    OVERNIGHT
}
