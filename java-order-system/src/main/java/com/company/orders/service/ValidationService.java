package com.company.orders.service;

import com.company.orders.model.Order;
import com.company.orders.model.OrderItem;
import com.company.orders.repository.CustomerRepository;
import com.company.orders.repository.ProductRepository;
import com.company.orders.repository.PromoCodeRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an incoming Order before any pricing or inventory work begins.
 *
 * Validation is purely structural and referential — it does NOT check business rules
 * that depend on calculated values (e.g. promo minimum amount). Those are handled
 * by PromotionEngine after pricing.
 *
 * All validation errors are collected before returning (fail-all, not fail-fast),
 * so the caller receives the complete set of problems in one pass.
 *
 * Rules enforced:
 *   1. orderId must be non-null and non-empty
 *   2. customerId must refer to an existing customer
 *   3. items list must be non-null and non-empty
 *   4. each item's productId must refer to an existing product
 *   5. each item's quantity must be between 1 and 100 inclusive
 *   6. no duplicate productIds within the same order (same product can be ordered once)
 *   7. shippingZone must be 1-5 inclusive
 *   8. shippingType must be non-null
 *   9. promoCode, if provided, must exist in the promo code catalog
 *      (expiry and eligibility are validated later in PromotionEngine)
 */
public class ValidationService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;

    public ValidationService(CustomerRepository customerRepository,
                              ProductRepository productRepository,
                              PromoCodeRepository promoCodeRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.promoCodeRepository = promoCodeRepository;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, new ArrayList<>());
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
    }

    public ValidationResult validate(Order order) {
        List<String> errors = new ArrayList<>();

        // Rule 1: orderId
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            errors.add("orderId is required");
        }

        // Rule 2: customer exists
        if (order.getCustomerId() == null || order.getCustomerId().trim().isEmpty()) {
            errors.add("customerId is required");
        } else if (!customerRepository.exists(order.getCustomerId())) {
            errors.add("Customer not found: " + order.getCustomerId());
        }

        // Rule 3: items list
        if (order.getItems() == null || order.getItems().isEmpty()) {
            errors.add("Order must contain at least one item");
        } else {
            Set<String> seenProductIds = new HashSet<>();

            for (int i = 0; i < order.getItems().size(); i++) {
                OrderItem item = order.getItems().get(i);

                // Rule 4: product exists
                if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
                    errors.add("Item[" + i + "]: productId is required");
                } else if (!productRepository.exists(item.getProductId())) {
                    errors.add("Item[" + i + "]: Product not found: " + item.getProductId());
                } else {
                    // Rule 6: no duplicate products
                    if (!seenProductIds.add(item.getProductId())) {
                        errors.add("Item[" + i + "]: Duplicate productId '" + item.getProductId() +
                                   "'. Combine quantities into a single line item.");
                    }
                }

                // Rule 5: quantity range
                if (item.getQuantity() < 1) {
                    errors.add("Item[" + i + "] (" + item.getProductId() + "): quantity must be >= 1");
                } else if (item.getQuantity() > 100) {
                    errors.add("Item[" + i + "] (" + item.getProductId() + "): quantity must be <= 100. " +
                               "Contact sales for bulk orders.");
                }
            }
        }

        // Rule 7: shipping zone
        if (order.getShippingZone() < 1 || order.getShippingZone() > 5) {
            errors.add("shippingZone must be between 1 and 5 (got: " + order.getShippingZone() + ")");
        }

        // Rule 8: shipping type
        if (order.getShippingType() == null) {
            errors.add("shippingType is required (STANDARD, EXPRESS, or OVERNIGHT)");
        }

        // Rule 9: promo code existence (not expiry/eligibility — that's PromotionEngine)
        if (order.getPromoCode() != null && !order.getPromoCode().trim().isEmpty()) {
            if (!promoCodeRepository.exists(order.getPromoCode().trim().toUpperCase())) {
                errors.add("Promo code not recognised: '" + order.getPromoCode() + "'");
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
}
