package com.company.orders.model;

/**
 * Product category used to determine applicable tax rate.
 * Each category has a distinct tax rate defined in PricingEngine.TAX_RATES.
 *
 * LEGACY NOTE: The product catalog API returns categories as string codes
 * (e.g. "ELEC", "CLTH", "FOOD", "MEDI", "GEN"). These are mapped to this
 * enum via Product.legacyCategoryCode. See ProductRepository for the mapping table.
 */
public enum ProductCategory {
    ELECTRONICS,
    CLOTHING,
    FOOD,
    MEDICAL,
    GENERAL
}
