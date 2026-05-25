package com.company.orders.model;

/**
 * Customer loyalty tier. Determines base discount rates applied during pricing.
 * Tier is assigned by CRM system based on historical spend and is not computed here.
 *
 * LEGACY NOTE: Prior to v2, tiers were stored as integers (0=BRONZE, 1=SILVER, 2=GOLD, 3=PLATINUM).
 * The integer-to-enum mapping lives in CustomerRepository.fromLegacyCode().
 */
public enum CustomerTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM
}
