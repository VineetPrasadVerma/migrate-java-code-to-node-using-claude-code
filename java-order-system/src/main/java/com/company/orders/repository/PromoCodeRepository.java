package com.company.orders.repository;

import com.company.orders.model.CustomerTier;
import com.company.orders.model.PromoCode;

import java.util.*;

/**
 * In-memory promotional code store.
 *
 * Available codes (for test reference):
 *   SAVE10   - 10% off subtotal, all tiers, expires 2026-12-31
 *   SAVE20   - 20% off subtotal, all tiers, expires 2026-06-30
 *   FREESHIP - Free shipping,    all tiers, expires 2026-12-31
 *   BULK15   - 15% off if subtotal >= $200, all tiers, expires 2026-12-31
 *   NEWCUST  - 8% off, Bronze tier only, newCustomerOnly (<30 days), expires 2026-12-31
 *   GOLD20   - 20% off, Gold/Platinum only, expires 2026-12-31
 *   EXPIRED  - 5% off, EXPIRED (2026-01-01) — for negative testing only
 */
public class PromoCodeRepository {

    private final Map<String, PromoCode> store = new HashMap<>();

    public PromoCodeRepository() {
        seed();
    }

    private void seed() {
        add(new PromoCode(
            "SAVE10", "PERCENTAGE_SUBTOTAL", 10.0, 0.0,
            "2026-12-31", false, null
        ));
        add(new PromoCode(
            "SAVE20", "PERCENTAGE_SUBTOTAL", 20.0, 0.0,
            "2026-06-30", false, null
        ));
        add(new PromoCode(
            "FREESHIP", "FREE_SHIPPING", 0.0, 0.0,
            "2026-12-31", false, null
        ));
        add(new PromoCode(
            "BULK15", "PERCENTAGE_IF_ABOVE", 15.0, 200.0,
            "2026-12-31", false, null
        ));
        add(new PromoCode(
            "NEWCUST", "PERCENTAGE_SUBTOTAL", 8.0, 0.0,
            "2026-12-31", true,
            Arrays.asList(CustomerTier.BRONZE)
        ));
        add(new PromoCode(
            "GOLD20", "PERCENTAGE_SUBTOTAL", 20.0, 0.0,
            "2026-12-31", false,
            Arrays.asList(CustomerTier.GOLD, CustomerTier.PLATINUM)
        ));
        add(new PromoCode(
            "EXPIRED", "PERCENTAGE_SUBTOTAL", 5.0, 0.0,
            "2026-01-01", false, null          // expired
        ));
    }

    private void add(PromoCode p) {
        store.put(p.getCode(), p);
    }

    public PromoCode findByCode(String code) {
        return store.get(code);
    }

    public boolean exists(String code) {
        return store.containsKey(code);
    }
}
