package com.company.orders.repository;

import com.company.orders.model.Product;
import com.company.orders.model.ProductCategory;

import java.util.*;

/**
 * In-memory product catalog seeded with test data.
 *
 * Product catalog (for test reference):
 *   P001 - Laptop Pro           ELECTRONICS  $1200.00  2.5kg   stock:50
 *   P002 - USB Cable            ELECTRONICS  $15.00    0.1kg   stock:200
 *   P003 - Office Chair         GENERAL      $350.00   15.0kg  stock:10
 *   P004 - Blue Jeans           CLOTHING     $60.00    0.5kg   stock:100
 *   P005 - Protein Shake        FOOD         $45.00    0.8kg   stock:5   (LOW STOCK)
 *   P006 - Blood Pressure Mon.  MEDICAL      $120.00   0.3kg   stock:30
 *   P007 - Wireless Headphones  ELECTRONICS  $200.00   0.3kg   stock:0   (OUT OF STOCK)
 *   P008 - Running Shoes        CLOTHING     $90.00    0.7kg   stock:75
 *   P009 - Vitamin C Suppl.     MEDICAL      $25.00    0.2kg   stock:150
 *   P010 - Lithium Battery Pack ELECTRONICS  $180.00   1.2kg   stock:20  DANGEROUS GOOD
 *
 * LEGACY NOTE: legacyCategoryCode is the raw string from the v1 catalog API.
 * LEGACY_CODE_MAP provides the reverse mapping for any system still consuming it.
 */
public class ProductRepository {

    public static final Map<String, ProductCategory> LEGACY_CODE_MAP;

    static {
        Map<String, ProductCategory> m = new HashMap<>();
        m.put("ELEC", ProductCategory.ELECTRONICS);
        m.put("CLTH", ProductCategory.CLOTHING);
        m.put("FOOD", ProductCategory.FOOD);
        m.put("MEDI", ProductCategory.MEDICAL);
        m.put("GEN",  ProductCategory.GENERAL);
        LEGACY_CODE_MAP = Collections.unmodifiableMap(m);
    }

    // Stock levels are mutable — InventoryService modifies them directly.
    private final Map<String, Product> store = new HashMap<>();

    public ProductRepository() {
        seed();
    }

    private void seed() {
        add(new Product("P001", "Laptop Pro",             ProductCategory.ELECTRONICS, "ELEC", 1200.00, 2.5,  false, 50));
        add(new Product("P002", "USB Cable",              ProductCategory.ELECTRONICS, "ELEC", 15.00,   0.1,  false, 200));
        add(new Product("P003", "Office Chair",           ProductCategory.GENERAL,     "GEN",  350.00,  15.0, false, 10));
        add(new Product("P004", "Blue Jeans",             ProductCategory.CLOTHING,    "CLTH", 60.00,   0.5,  false, 100));
        add(new Product("P005", "Protein Shake",          ProductCategory.FOOD,        "FOOD", 45.00,   0.8,  false, 5));
        add(new Product("P006", "Blood Pressure Monitor", ProductCategory.MEDICAL,     "MEDI", 120.00,  0.3,  false, 30));
        add(new Product("P007", "Wireless Headphones",    ProductCategory.ELECTRONICS, "ELEC", 200.00,  0.3,  false, 0));
        add(new Product("P008", "Running Shoes",          ProductCategory.CLOTHING,    "CLTH", 90.00,   0.7,  false, 75));
        add(new Product("P009", "Vitamin C Supplements",  ProductCategory.MEDICAL,     "MEDI", 25.00,   0.2,  false, 150));
        add(new Product("P010", "Lithium Battery Pack",   ProductCategory.ELECTRONICS, "ELEC", 180.00,  1.2,  true,  20));
    }

    private void add(Product p) {
        store.put(p.getId(), p);
    }

    public Product findById(String id) {
        return store.get(id);
    }

    public boolean exists(String id) {
        return store.containsKey(id);
    }

    public Map<String, Product> findByIds(Collection<String> ids) {
        Map<String, Product> result = new HashMap<>();
        for (String id : ids) {
            Product p = store.get(id);
            if (p != null) {
                result.put(id, p);
            }
        }
        return result;
    }

    /**
     * Decrements stock. Called by InventoryService after all checks pass.
     * NOT thread-safe — see Product class LEGACY NOTE.
     */
    public synchronized void decrementStock(String productId, int qty) {
        Product p = store.get(productId);
        if (p == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        if (p.getStockLevel() < qty) {
            throw new IllegalStateException(
                "Cannot decrement stock for " + productId + ": requested " + qty +
                " but only " + p.getStockLevel() + " available"
            );
        }
        p.setStockLevel(p.getStockLevel() - qty);
    }

    /**
     * Restores stock for a reservation that was cancelled (e.g. after fraud rejection).
     */
    public synchronized void restoreStock(String productId, int qty) {
        Product p = store.get(productId);
        if (p != null) {
            p.setStockLevel(p.getStockLevel() + qty);
        }
    }
}
