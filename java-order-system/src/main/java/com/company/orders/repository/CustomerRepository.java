package com.company.orders.repository;

import com.company.orders.model.Address;
import com.company.orders.model.Customer;
import com.company.orders.model.CustomerTier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory customer store seeded with test data.
 *
 * LEGACY NOTE: fromLegacyCode() maps the integer tier codes used in the v1 database
 * (0=BRONZE, 1=SILVER, 2=GOLD, 3=PLATINUM) to the current enum. This method is still
 * called by the legacy CSV import job that runs nightly.
 *
 * Registration dates are deliberately set relative to 2026-05-20 (today in test context):
 *   C001 - PLATINUM,  registered 2023-01-15 (~1220 days ago)
 *   C002 - GOLD,      registered 2023-06-20 (~1065 days ago)
 *   C003 - SILVER,    registered 2024-08-10 (~649 days ago)
 *   C004 - BRONZE,    registered 2025-11-01 (~200 days ago)
 *   C005 - BRONZE,    registered 2026-04-25 (~25 days ago)  ← "new customer" for fraud rules
 */
public class CustomerRepository {

    private final Map<String, Customer> store = new HashMap<>();

    public CustomerRepository() {
        seed();
    }

    private void seed() {
        register(new Customer(
            "C001", "Alice Johnson", "alice@example.com",
            CustomerTier.PLATINUM, "2023-01-15",
            new Address("100 Oak Street", "San Francisco", "CA", "94105", "US"),
            "US"
        ));
        register(new Customer(
            "C002", "Bob Smith", "bob@example.com",
            CustomerTier.GOLD, "2023-06-20",
            new Address("200 Pine Ave", "New York", "NY", "10001", "US"),
            "US"
        ));
        register(new Customer(
            "C003", "Carol Davis", "carol@example.com",
            CustomerTier.SILVER, "2024-08-10",
            new Address("300 Elm Road", "Chicago", "IL", "60601", "US"),
            "US"
        ));
        register(new Customer(
            "C004", "David Wilson", "david@example.com",
            CustomerTier.BRONZE, "2025-11-01",
            new Address("400 Maple Blvd", "Austin", "TX", "73301", "US"),
            "US"
        ));
        register(new Customer(
            "C005", "Eve Martinez", "eve@example.com",
            CustomerTier.BRONZE, "2026-04-25",
            new Address("500 Cedar Lane", "Miami", "FL", "33101", "US"),
            "US"
        ));
    }

    private void register(Customer c) {
        store.put(c.getId(), c);
    }

    public Customer findById(String id) {
        return store.get(id);
    }

    public boolean exists(String id) {
        return store.containsKey(id);
    }

    public Collection<Customer> findAll() {
        return store.values();
    }

    /**
     * Maps legacy integer tier codes to the CustomerTier enum.
     * Called by the nightly CSV import job; do not remove.
     *
     * @param legacyCode integer code from v1 database (0-3)
     * @return CustomerTier enum value
     * @throws IllegalArgumentException if code is outside 0-3
     */
    public static CustomerTier fromLegacyCode(int legacyCode) {
        switch (legacyCode) {
            case 0: return CustomerTier.BRONZE;
            case 1: return CustomerTier.SILVER;
            case 2: return CustomerTier.GOLD;
            case 3: return CustomerTier.PLATINUM;
            default: throw new IllegalArgumentException(
                "Unknown legacy tier code: " + legacyCode + ". Valid range is 0-3."
            );
        }
    }
}
