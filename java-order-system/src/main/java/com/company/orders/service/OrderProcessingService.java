package com.company.orders.service;

import com.company.orders.model.*;
import com.company.orders.repository.CustomerRepository;
import com.company.orders.repository.OrderRepository;
import com.company.orders.repository.ProductRepository;
import com.company.orders.util.MoneyUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the complete order processing pipeline.
 *
 * Pipeline stages (in order):
 *   1. VALIDATE     — structural and referential validation (ValidationService)
 *   2. PRICE        — per-line discount and tax calculation (PricingEngine)
 *   3. PROMOTE      — apply promo code if present (PromotionEngine)
 *   4. SHIP         — calculate shipping cost (ShippingCalculator)
 *   5. INVENTORY    — check stock and reserve (InventoryService)
 *   6. FRAUD CHECK  — evaluate fraud rules (FraudDetectionService)
 *   7. FINALISE     — determine status, persist, return result
 *
 * Failure handling:
 *   - Stage 1 failure → REJECTED immediately (no pricing computed, errors populated)
 *   - Stage 5 failure → REJECTED with inventory errors (pricing shown, no stock reserved)
 *   - Stage 6 HIGH flag → REJECTED, stock reservation released
 *   - Stage 6 MEDIUM/LOW flags only → PROCESSED with fraud flags in warnings
 *
 * Grand total formula:
 *   grandTotal = subtotal - promoDiscount + totalTax + shippingCost
 *
 *   Where subtotal  = sum of lineItem.lineSubtotal (pre-tax, post tier/volume discount)
 *         totalTax  = sum of lineItem.taxAmount    (computed on pre-promo prices)
 *         promoDiscount = promo code discount on subtotal (not on tax)
 *
 * Note: allowPartialFulfillment on the order controls InventoryService behaviour.
 * If true, out-of-stock items are removed and the order proceeds with available items.
 * If false (default), any shortage causes a full REJECTION.
 */
public class OrderProcessingService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ValidationService validationService;
    private final PricingEngine pricingEngine;
    private final PromotionEngine promotionEngine;
    private final ShippingCalculator shippingCalculator;
    private final InventoryService inventoryService;
    private final FraudDetectionService fraudDetectionService;
    private final OrderRepository orderRepository;

    public OrderProcessingService(CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   ValidationService validationService,
                                   PricingEngine pricingEngine,
                                   PromotionEngine promotionEngine,
                                   ShippingCalculator shippingCalculator,
                                   InventoryService inventoryService,
                                   FraudDetectionService fraudDetectionService,
                                   OrderRepository orderRepository) {
        this.customerRepository   = customerRepository;
        this.productRepository    = productRepository;
        this.validationService    = validationService;
        this.pricingEngine        = pricingEngine;
        this.promotionEngine      = promotionEngine;
        this.shippingCalculator   = shippingCalculator;
        this.inventoryService     = inventoryService;
        this.fraudDetectionService = fraudDetectionService;
        this.orderRepository      = orderRepository;
    }

    public OrderResult processOrder(Order order) {
        String now = LocalDateTime.now().format(TIMESTAMP_FMT);

        // ----------------------------------------------------------------
        // Stage 1: Validate
        // ----------------------------------------------------------------
        ValidationService.ValidationResult validation = validationService.validate(order);
        if (!validation.isValid()) {
            OrderResult result = buildRejected(order.getOrderId(), validation.getErrors(),
                "Order rejected: failed validation", now);
            orderRepository.save(result);
            return result;
        }

        // ----------------------------------------------------------------
        // Stage 2: Resolve entities
        // ----------------------------------------------------------------
        Customer customer = customerRepository.findById(order.getCustomerId());
        List<String> productIds = order.getItems().stream()
            .map(OrderItem::getProductId)
            .collect(Collectors.toList());
        Map<String, Product> products = productRepository.findByIds(productIds);

        // ----------------------------------------------------------------
        // Stage 3: Price line items
        // ----------------------------------------------------------------
        List<ProcessedOrderItem> lineItems =
            pricingEngine.calculateLineItems(order.getItems(), customer, products);

        double subtotal = MoneyUtils.round(
            lineItems.stream().mapToDouble(ProcessedOrderItem::getLineSubtotal).sum()
        );
        double totalTax = MoneyUtils.round(
            lineItems.stream().mapToDouble(ProcessedOrderItem::getTaxAmount).sum()
        );

        // ----------------------------------------------------------------
        // Stage 4: Apply promo code
        // ----------------------------------------------------------------
        PromotionResult promoResult = promotionEngine.applyPromotion(
            order.getPromoCode(), subtotal, customer, order.getOrderDate()
        );

        List<String> warnings = new ArrayList<>();
        if (!promoResult.isValid()) {
            warnings.add("Promo code not applied: " + promoResult.getInvalidReason());
        }

        double promoDiscount = promoResult.isValid() ? promoResult.getDiscountAmount() : 0.0;
        String appliedPromoCode = promoResult.isValid() ? promoResult.getAppliedCode() : null;

        // ----------------------------------------------------------------
        // Stage 5: Calculate shipping
        // ----------------------------------------------------------------
        double shippingCost = shippingCalculator.calculate(
            order, products, customer, promoResult.isValid() && promoResult.isFreeShipping()
        );

        // ----------------------------------------------------------------
        // Stage 6: Grand total (needed before fraud check)
        // ----------------------------------------------------------------
        double grandTotal = MoneyUtils.round(subtotal - promoDiscount + totalTax + shippingCost);

        // ----------------------------------------------------------------
        // Stage 7: Inventory check and reservation
        // ----------------------------------------------------------------
        InventoryService.InventoryResult inventory =
            inventoryService.checkAndReserve(order.getItems(), order.isAllowPartialFulfillment());

        if (!inventory.isAvailable()) {
            OrderResult result = new OrderResult();
            result.setOrderId(order.getOrderId());
            result.setStatus(OrderStatus.REJECTED);
            result.setLineItems(lineItems);
            result.setSubtotal(subtotal);
            result.setTotalTax(totalTax);
            result.setPromoCode(appliedPromoCode);
            result.setPromoDiscount(promoDiscount);
            result.setShippingCost(shippingCost);
            result.setGrandTotal(grandTotal);
            result.setErrors(inventory.getUnavailableMessages());
            result.setWarnings(warnings);
            result.setMessage("Order rejected: insufficient inventory");
            result.setProcessedAt(now);
            orderRepository.save(result);
            return result;
        }

        // Handle partial fulfillment: recalculate for reduced item set
        List<OrderItem> fulfilledItems = inventory.getFulfilledItems();
        List<ProcessedOrderItem> fulfilledLineItems = lineItems;
        double finalSubtotal = subtotal;
        double finalTax = totalTax;
        double finalPromoDiscount = promoDiscount;
        double finalShippingCost = shippingCost;

        if (fulfilledItems.size() < order.getItems().size()) {
            // Recalculate for the items that were actually fulfilled
            fulfilledLineItems = pricingEngine.calculateLineItems(fulfilledItems, customer, products);
            finalSubtotal = MoneyUtils.round(
                fulfilledLineItems.stream().mapToDouble(ProcessedOrderItem::getLineSubtotal).sum()
            );
            finalTax = MoneyUtils.round(
                fulfilledLineItems.stream().mapToDouble(ProcessedOrderItem::getTaxAmount).sum()
            );
            PromotionResult recalcPromo = promotionEngine.applyPromotion(
                order.getPromoCode(), finalSubtotal, customer, order.getOrderDate()
            );
            finalPromoDiscount = recalcPromo.isValid() ? recalcPromo.getDiscountAmount() : 0.0;
            finalShippingCost = shippingCalculator.calculate(
                order, productRepository.findByIds(
                    fulfilledItems.stream().map(OrderItem::getProductId).collect(Collectors.toList())
                ), customer, promoResult.isValid() && promoResult.isFreeShipping()
            );
            inventory.getUnavailableMessages()
                .forEach(msg -> warnings.add("Partial fulfillment — item dropped: " + msg));
        }

        double finalGrandTotal = MoneyUtils.round(
            finalSubtotal - finalPromoDiscount + finalTax + finalShippingCost
        );

        // ----------------------------------------------------------------
        // Stage 8: Fraud detection
        // ----------------------------------------------------------------
        List<OrderResult.FraudFlagResult> fraudFlags = fraudDetectionService.evaluate(
            order, customer, finalGrandTotal, order.getOrderDate()
        );

        boolean fraudRejected = FraudDetectionService.hasHighSeverityFlag(fraudFlags);

        if (fraudRejected) {
            // Release inventory reservation — order will not be fulfilled
            inventoryService.releaseReservation(fulfilledItems);

            OrderResult result = new OrderResult();
            result.setOrderId(order.getOrderId());
            result.setStatus(OrderStatus.REJECTED);
            result.setLineItems(fulfilledLineItems);
            result.setSubtotal(finalSubtotal);
            result.setTotalTax(finalTax);
            result.setPromoCode(appliedPromoCode);
            result.setPromoDiscount(finalPromoDiscount);
            result.setShippingCost(finalShippingCost);
            result.setGrandTotal(finalGrandTotal);
            result.setFraudFlags(fraudFlags);
            result.setWarnings(warnings);
            result.setMessage("Order rejected due to high-severity fraud detection");
            result.setProcessedAt(now);
            orderRepository.save(result);
            return result;
        }

        // ----------------------------------------------------------------
        // Stage 9: Success — build PROCESSED result
        // ----------------------------------------------------------------
        // Convert MEDIUM/LOW fraud flags to warnings
        fraudFlags.forEach(f ->
            warnings.add(String.format("[FRAUD:%s] %s", f.getSeverity(), f.getDescription()))
        );

        OrderResult result = new OrderResult();
        result.setOrderId(order.getOrderId());
        result.setStatus(OrderStatus.PROCESSED);
        result.setLineItems(fulfilledLineItems);
        result.setSubtotal(finalSubtotal);
        result.setTotalTax(finalTax);
        result.setPromoCode(appliedPromoCode);
        result.setPromoDiscount(finalPromoDiscount);
        result.setShippingCost(finalShippingCost);
        result.setGrandTotal(finalGrandTotal);
        result.setFraudFlags(fraudFlags);
        result.setWarnings(warnings);
        result.setMessage(fraudFlags.isEmpty()
            ? "Order processed successfully"
            : "Order processed with fraud review flags — see fraudFlags and warnings");
        result.setProcessedAt(now);
        orderRepository.save(result);
        return result;
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private OrderResult buildRejected(String orderId, List<String> errors, String message, String now) {
        OrderResult result = new OrderResult();
        result.setOrderId(orderId);
        result.setStatus(OrderStatus.REJECTED);
        result.setErrors(errors);
        result.setMessage(message);
        result.setProcessedAt(now);
        return result;
    }
}
