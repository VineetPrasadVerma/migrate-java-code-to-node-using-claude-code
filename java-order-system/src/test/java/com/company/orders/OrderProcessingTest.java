package com.company.orders;

import com.company.orders.model.*;
import com.company.orders.repository.*;
import com.company.orders.service.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Integration-style unit tests for OrderProcessingService.
 *
 * These tests use the real in-memory repositories (no mocks), so they verify
 * the complete pipeline including validation, pricing, promo, shipping, inventory,
 * and fraud detection working together.
 *
 * All monetary assertions use a delta of 0.01 to account for double rounding.
 *
 * Test scenarios:
 *   1. Happy path — Gold customer, SAVE10 promo, electronics order
 *   2. Fraud rejection — Bronze new customer, high-value order (>$1000)
 *   3. Inventory rejection — order includes out-of-stock product (P007)
 *   4. Platinum with FREESHIP — mixed categories, express shipping, free ship promo
 *   5. Multiple fraud warnings — new Bronze customer, medium+low flags, order still processes
 *   6. Partial fulfillment — allowPartialFulfillment=true, one item out of stock
 *   7. Expired promo code — order proceeds without discount, warning added
 *   8. Tier-restricted promo — Gold customer tries NEWCUST code (Bronze-only), rejected
 */
public class OrderProcessingTest {

    private OrderProcessingService orderService;

    @Before
    public void setUp() {
        ProductRepository productRepo   = new ProductRepository();
        CustomerRepository customerRepo = new CustomerRepository();
        PromoCodeRepository promoRepo   = new PromoCodeRepository();
        OrderRepository orderRepo       = new OrderRepository();

        ValidationService validationService   = new ValidationService(customerRepo, productRepo, promoRepo);
        PricingEngine pricingEngine           = new PricingEngine();
        PromotionEngine promotionEngine       = new PromotionEngine(promoRepo);
        ShippingCalculator shippingCalculator = new ShippingCalculator();
        InventoryService inventoryService     = new InventoryService(productRepo);
        FraudDetectionService fraudService    = new FraudDetectionService(orderRepo);

        orderService = new OrderProcessingService(
            customerRepo, productRepo, validationService, pricingEngine,
            promotionEngine, shippingCalculator, inventoryService, fraudService, orderRepo
        );
    }

    // ------------------------------------------------------------------
    // Test 1: Happy path
    // ------------------------------------------------------------------
    @Test
    public void test1_happyPath_goldCustomer_save10() {
        // C002 = Gold (10% tier discount)
        // P001 = Laptop Pro $1200, ELECTRONICS, qty=1 → discounted $1080, tax 18%=$194.40
        // P002 = USB Cable $15, ELECTRONICS, qty=3 → discounted $13.50 each, subtotal $40.50, tax 18%=$7.29
        // Subtotal = $1120.50, SAVE10 = -$112.05, tax = $201.69
        // Shipping: zone 2, standard, 2.8kg → $8.00
        // Grand total = $1120.50 - $112.05 + $201.69 + $8.00 = $1218.14
        Order order = order("ORD-001", "C002", ShippingType.STANDARD, 2, false, "SAVE10",
            item("P001", 1), item("P002", 3));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertEquals(1120.50, result.getSubtotal(),      0.01);
        assertEquals(112.05,  result.getPromoDiscount(), 0.01);
        assertEquals(201.69,  result.getTotalTax(),      0.01);
        assertEquals(8.00,    result.getShippingCost(),  0.01);
        assertEquals(1218.14, result.getGrandTotal(),    0.01);
        assertTrue(result.getFraudFlags().isEmpty());
        assertEquals("SAVE10", result.getPromoCode());
        assertEquals(2, result.getLineItems().size());
    }

    // ------------------------------------------------------------------
    // Test 2: Fraud rejection — HIGH_VALUE_NEW_CUSTOMER
    // ------------------------------------------------------------------
    @Test
    public void test2_fraudRejection_highValueNewCustomer() {
        // C005 = Bronze, registered 2026-04-25 (25 days ago from 2026-05-20)
        // P001 = Laptop Pro $1200, qty=2 → subtotal $2400, tax $432
        // Grand total = $2837.00 > $1000 AND account age 25 days < 90 → HIGH_VALUE_NEW_CUSTOMER
        Order order = order("ORD-002", "C005", ShippingType.STANDARD, 1, false, null,
            item("P001", 2));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertFalse(result.getFraudFlags().isEmpty());
        assertTrue(result.getFraudFlags().stream()
            .anyMatch(f -> f.getFlag() == FraudFlag.HIGH_VALUE_NEW_CUSTOMER));
        assertTrue(result.getFraudFlags().stream()
            .anyMatch(f -> "HIGH".equals(f.getSeverity())));
    }

    // ------------------------------------------------------------------
    // Test 3: Inventory rejection
    // ------------------------------------------------------------------
    @Test
    public void test3_inventoryRejection_outOfStockProduct() {
        // P007 = Wireless Headphones, stock=0 → rejection
        Order order = order("ORD-003", "C001", ShippingType.STANDARD, 1, false, null,
            item("P007", 1), item("P002", 5));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("P007")));
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.toLowerCase().contains("stock") ||
                           e.toLowerCase().contains("insufficient")));
    }

    // ------------------------------------------------------------------
    // Test 4: Platinum + FREESHIP + mixed categories
    // ------------------------------------------------------------------
    @Test
    public void test4_platinumCustomer_freeship_mixedCategories() {
        // C001 = Platinum (15% tier discount)
        // P004 = Blue Jeans $60 CLOTHING qty=5  → 15%+3%=18%, price $49.20, sub $246, tax 5%=$12.30
        // P006 = BP Monitor $120 MEDICAL qty=3  → 15%,        price $102,   sub $306, tax 12%=$36.72
        // P009 = Vitamin C $25 MEDICAL qty=10   → 15%+7%=22%, price $19.50, sub $195, tax 12%=$23.40
        // Subtotal = $747.00, FREESHIP → promoDiscount $0, shippingCost $0
        // Tax = $72.42
        // Grand total = $747.00 + $72.42 = $819.42
        Order order = order("ORD-004", "C001", ShippingType.EXPRESS, 3, false, "FREESHIP",
            item("P004", 5), item("P006", 3), item("P009", 10));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertEquals(747.00, result.getSubtotal(),      0.01);
        assertEquals(0.00,   result.getPromoDiscount(), 0.01);
        assertEquals(72.42,  result.getTotalTax(),      0.01);
        assertEquals(0.00,   result.getShippingCost(),  0.01);
        assertEquals(819.42, result.getGrandTotal(),    0.01);
        assertEquals("FREESHIP", result.getPromoCode());
        assertTrue(result.getFraudFlags().isEmpty());
    }

    // ------------------------------------------------------------------
    // Test 5: Multiple fraud warnings — order still PROCESSES
    // ------------------------------------------------------------------
    @Test
    public void test5_multipleFraudWarnings_orderStillProcesses() {
        // C005 = Bronze, 25 days old
        // P004 = Blue Jeans, qty=10  → BULK_PURCHASE (LOW)
        // P009 = Vitamin C, qty=5
        // SAVE10 promo applied
        // Shipping zone=4 → SUSPICIOUS_ZONE (MEDIUM) [total >$300]
        // SAVE10 + age<30 + total>$500 → PROMO_ABUSE (MEDIUM)
        // NO HIGH flag → order PROCESSES
        Order order = order("ORD-005", "C005", ShippingType.STANDARD, 4, false, "SAVE10",
            item("P004", 10), item("P009", 5));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertTrue("Expected at least 2 fraud flags",
            result.getFraudFlags().size() >= 2);
        assertTrue("Should have no HIGH severity flags",
            result.getFraudFlags().stream().noneMatch(f -> "HIGH".equals(f.getSeverity())));
        assertEquals(679.25, result.getSubtotal(),      0.01);
        assertEquals(67.93,  result.getPromoDiscount(), 0.01);
        assertEquals(42.45,  result.getTotalTax(),      0.01);
        assertEquals(18.50,  result.getShippingCost(),  0.01);
        assertEquals(672.27, result.getGrandTotal(),    0.01);
    }

    // ------------------------------------------------------------------
    // Test 6: Partial fulfillment allowed
    // ------------------------------------------------------------------
    @Test
    public void test6_partialFulfillment_allowed() {
        // P007 = out of stock, but allowPartialFulfillment=true → order proceeds without P007
        Order order = order("ORD-006", "C002", ShippingType.STANDARD, 1, true, null,
            item("P001", 1), item("P007", 1));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertEquals(1, result.getLineItems().size());
        assertEquals("P001", result.getLineItems().get(0).getProductId());
        assertTrue(result.getWarnings().stream()
            .anyMatch(w -> w.contains("P007") || w.toLowerCase().contains("partial")));
    }

    // ------------------------------------------------------------------
    // Test 7: Expired promo code — order proceeds without discount
    // ------------------------------------------------------------------
    @Test
    public void test7_expiredPromoCode_orderProceedsWithWarning() {
        Order order = order("ORD-007", "C002", ShippingType.STANDARD, 1, false, "EXPIRED",
            item("P002", 2));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertEquals(0.00, result.getPromoDiscount(), 0.01);
        assertNull(result.getPromoCode());
        assertTrue(result.getWarnings().stream()
            .anyMatch(w -> w.toLowerCase().contains("expir")));
    }

    // ------------------------------------------------------------------
    // Test 8: Tier-restricted promo — NEWCUST for Bronze only, Gold rejected
    // ------------------------------------------------------------------
    @Test
    public void test8_tierRestrictedPromo_goldCustomerCannotUseNewcust() {
        // NEWCUST = Bronze only. C002 = Gold → promo rejected, order proceeds without discount
        Order order = order("ORD-008", "C002", ShippingType.STANDARD, 1, false, "NEWCUST",
            item("P002", 2));

        OrderResult result = orderService.processOrder(order);

        assertEquals(OrderStatus.PROCESSED, result.getStatus());
        assertEquals(0.00, result.getPromoDiscount(), 0.01);
        assertTrue(result.getWarnings().stream()
            .anyMatch(w -> w.toLowerCase().contains("promo")));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Order order(String orderId, String customerId, ShippingType type,
                        int zone, boolean partial, String promoCode, OrderItem... items) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setCustomerId(customerId);
        o.setItems(Arrays.asList(items));
        o.setShippingType(type);
        o.setShippingZone(zone);
        o.setAllowPartialFulfillment(partial);
        o.setPromoCode(promoCode);
        o.setOrderDate("2026-05-20");
        return o;
    }

    private OrderItem item(String productId, int quantity) {
        return new OrderItem(productId, quantity);
    }
}
