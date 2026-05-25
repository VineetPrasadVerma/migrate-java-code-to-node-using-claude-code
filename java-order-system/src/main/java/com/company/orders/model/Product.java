package com.company.orders.model;

/**
 * Represents a product in the catalog.
 *
 * LEGACY NOTE: legacyCategoryCode is the raw string code returned by the old catalog API
 * (e.g. "ELEC", "CLTH", "FOOD", "MEDI", "GEN"). ProductRepository maps these to the
 * ProductCategory enum. Both fields are stored for backward compatibility with
 * downstream systems that still consume the legacy code.
 *
 * LEGACY NOTE: stockLevel is managed in-memory and is NOT thread-safe in this implementation.
 * The original system relied on database-level row locking. The InventoryService uses
 * synchronized methods as a partial mitigation, but this is insufficient for multi-JVM deployments.
 */
public class Product {
    private String id;
    private String name;
    private ProductCategory category;
    private String legacyCategoryCode;  // e.g. "ELEC" — legacy field, kept for API compat
    private double basePrice;
    private double weightKg;
    private boolean dangerousGood;
    private int stockLevel;

    public Product() {}

    public Product(String id, String name, ProductCategory category, String legacyCategoryCode,
                   double basePrice, double weightKg, boolean dangerousGood, int stockLevel) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.legacyCategoryCode = legacyCategoryCode;
        this.basePrice = basePrice;
        this.weightKg = weightKg;
        this.dangerousGood = dangerousGood;
        this.stockLevel = stockLevel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public String getLegacyCategoryCode() { return legacyCategoryCode; }
    public void setLegacyCategoryCode(String legacyCategoryCode) { this.legacyCategoryCode = legacyCategoryCode; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public boolean isDangerousGood() { return dangerousGood; }
    public void setDangerousGood(boolean dangerousGood) { this.dangerousGood = dangerousGood; }

    public int getStockLevel() { return stockLevel; }
    public void setStockLevel(int stockLevel) { this.stockLevel = stockLevel; }
}
