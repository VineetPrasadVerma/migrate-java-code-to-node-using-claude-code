package com.company.orders.service;

import com.company.orders.model.*;
import com.company.orders.repository.OrderRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based fraud detection engine.
 *
 * Evaluates an order against four fraud rules and returns any triggered flags.
 * Rules are evaluated INDEPENDENTLY — one rule triggering does not affect others.
 *
 * Rules and their severity:
 *
 *   Rule 1: HIGH_VALUE_NEW_CUSTOMER (HIGH)
 *     Trigger: grandTotal > $1,000 AND account age < 90 days
 *     Intent: High-value orders from very new accounts are statistically higher risk.
 *     Effect: Order REJECTED.
 *
 *   Rule 2: PROMO_ABUSE (MEDIUM)
 *     Trigger: promoCode was applied AND grandTotal > $500 AND account age < 30 days
 *     Intent: New accounts using promo codes on large orders may be exploiting sign-up incentives.
 *     Effect: Order PROCESSED with warning; flagged for manual review.
 *
 *   Rule 3: SUSPICIOUS_ZONE (MEDIUM)
 *     Trigger: shippingZone >= 4 AND customer.tier == BRONZE AND grandTotal > $300
 *     Intent: Bronze (low-spend) accounts shipping to far zones on significant orders
 *             are a common pattern for address fraud.
 *     Effect: Order PROCESSED with warning; flagged for manual review.
 *
 *   Rule 4: BULK_PURCHASE (LOW)
 *     Trigger: any single item has quantity >= 10 AND customer.tier == BRONZE
 *     Intent: Large bulk purchases from entry-tier accounts may indicate reseller fraud
 *             or account takeover. Gold/Platinum customers are already verified.
 *     Effect: Order PROCESSED with informational note.
 *
 * LEGACY NOTE: This implementation uses a procedural if-else chain. The original design
 * document called for a rule-engine pattern (Strategy + Chain of Responsibility), but
 * this was simplified during a deadline crunch in 2020 and never refactored.
 * Adding new rules requires modifying this class directly.
 *
 * Outcome determination (done in OrderProcessingService, not here):
 *   Any HIGH severity flag  → REJECTED
 *   MEDIUM/LOW flags only   → PROCESSED with warnings
 *   No flags                → PROCESSED cleanly
 */
public class FraudDetectionService {

    private static final double HIGH_VALUE_THRESHOLD    = 1000.00;
    private static final int    NEW_ACCOUNT_DAYS_HIGH   = 90;
    private static final double PROMO_ABUSE_THRESHOLD   = 500.00;
    private static final int    NEW_ACCOUNT_DAYS_PROMO  = 30;
    private static final double SUSPICIOUS_ZONE_THRESHOLD = 300.00;
    private static final int    SUSPICIOUS_ZONE_MINIMUM    = 4;
    private static final int    BULK_PURCHASE_THRESHOLD    = 10;

    private final OrderRepository orderRepository;

    public FraudDetectionService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Evaluates fraud rules against the given order context.
     *
     * @param order        the incoming order (for item quantities, promo code, shipping zone)
     * @param customer     the ordering customer (tier, registration date)
     * @param grandTotal   the calculated grand total BEFORE this method is called
     * @param orderDate    the effective order date for age calculations
     * @return list of FraudFlagResult; empty list means no fraud detected
     */
    public List<OrderResult.FraudFlagResult> evaluate(Order order, Customer customer,
                                                       double grandTotal, String orderDate) {
        List<OrderResult.FraudFlagResult> flags = new ArrayList<>();

        LocalDate effectiveDate = (orderDate != null && !orderDate.isEmpty())
            ? LocalDate.parse(orderDate)
            : LocalDate.now();

        long accountAgeDays = ChronoUnit.DAYS.between(
            LocalDate.parse(customer.getRegistrationDate()),
            effectiveDate
        );

        // ---------------------------------------------------------------
        // Rule 1: HIGH_VALUE_NEW_CUSTOMER
        // ---------------------------------------------------------------
        if (grandTotal > HIGH_VALUE_THRESHOLD && accountAgeDays < NEW_ACCOUNT_DAYS_HIGH) {
            flags.add(new OrderResult.FraudFlagResult(
                FraudFlag.HIGH_VALUE_NEW_CUSTOMER,
                String.format(
                    "Order total $%.2f exceeds $%.2f threshold for new accounts " +
                    "(account age: %d days, threshold: %d days)",
                    grandTotal, HIGH_VALUE_THRESHOLD, accountAgeDays, NEW_ACCOUNT_DAYS_HIGH
                )
            ));
        }

        // ---------------------------------------------------------------
        // Rule 2: PROMO_ABUSE
        // ---------------------------------------------------------------
        boolean promoUsed = order.getPromoCode() != null && !order.getPromoCode().trim().isEmpty();
        if (promoUsed && grandTotal > PROMO_ABUSE_THRESHOLD && accountAgeDays < NEW_ACCOUNT_DAYS_PROMO) {
            flags.add(new OrderResult.FraudFlagResult(
                FraudFlag.PROMO_ABUSE,
                String.format(
                    "Promotional code '%s' applied to $%.2f order from account aged %d days " +
                    "(threshold: %d days, minimum suspicious total: $%.2f)",
                    order.getPromoCode(), grandTotal, accountAgeDays,
                    NEW_ACCOUNT_DAYS_PROMO, PROMO_ABUSE_THRESHOLD
                )
            ));
        }

        // ---------------------------------------------------------------
        // Rule 3: SUSPICIOUS_ZONE
        // ---------------------------------------------------------------
        if (order.getShippingZone() >= SUSPICIOUS_ZONE_MINIMUM &&
            customer.getTier() == CustomerTier.BRONZE &&
            grandTotal > SUSPICIOUS_ZONE_THRESHOLD) {

            flags.add(new OrderResult.FraudFlagResult(
                FraudFlag.SUSPICIOUS_ZONE,
                String.format(
                    "Bronze-tier account placing $%.2f order to zone %d (zone threshold: %d, " +
                    "value threshold: $%.2f)",
                    grandTotal, order.getShippingZone(),
                    SUSPICIOUS_ZONE_MINIMUM, SUSPICIOUS_ZONE_THRESHOLD
                )
            ));
        }

        // ---------------------------------------------------------------
        // Rule 4: BULK_PURCHASE
        // ---------------------------------------------------------------
        if (customer.getTier() == CustomerTier.BRONZE) {
            for (OrderItem item : order.getItems()) {
                if (item.getQuantity() >= BULK_PURCHASE_THRESHOLD) {
                    flags.add(new OrderResult.FraudFlagResult(
                        FraudFlag.BULK_PURCHASE,
                        String.format(
                            "Bronze-tier account ordering %d units of product %s " +
                            "(bulk threshold: %d units)",
                            item.getQuantity(), item.getProductId(), BULK_PURCHASE_THRESHOLD
                        )
                    ));
                    break; // one flag per order regardless of how many items trigger it
                }
            }
        }

        return flags;
    }

    /**
     * Returns true if any flag in the list has HIGH severity, causing order rejection.
     */
    public static boolean hasHighSeverityFlag(List<OrderResult.FraudFlagResult> flags) {
        return flags.stream().anyMatch(f -> "HIGH".equals(f.getSeverity()));
    }
}
