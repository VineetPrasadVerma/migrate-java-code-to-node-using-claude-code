package com.company.orders;

import com.company.orders.model.Order;
import com.company.orders.model.OrderResult;
import com.company.orders.repository.*;
import com.company.orders.service.*;
import com.company.orders.util.JsonUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * CLI entry point for the Order Processing System.
 *
 * Usage:
 *   java -jar order-processing-system-1.0.0-jar-with-dependencies.jar <order.json>
 *   java -jar order-processing-system-1.0.0-jar-with-dependencies.jar < order.json
 *   echo '{...}' | java -jar order-processing-system-1.0.0-jar-with-dependencies.jar
 *
 * Output: JSON-formatted OrderResult printed to stdout.
 * Errors (parsing, system): printed to stderr, exit code 1.
 * Business rejections (REJECTED status) still exit 0 — rejection is a valid outcome.
 *
 * Dependency wiring:
 *   All services are constructed manually here (no DI framework).
 *   This makes the dependency graph explicit — each service's dependencies
 *   are visible at construction time.
 */
public class Main {

    public static void main(String[] args) {
        try {
            // 1. Read input JSON from file argument or stdin
            String json;
            if (args.length > 0) {
                json = null; // will use file path
            } else {
                // Read from stdin
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    json = reader.lines().collect(Collectors.joining("\n"));
                }
            }

            Order order;
            if (args.length > 0) {
                order = JsonUtils.fromFile(args[0], Order.class);
            } else {
                order = JsonUtils.fromJson(json, Order.class);
            }

            // 2. Wire dependencies
            ProductRepository productRepo    = new ProductRepository();
            CustomerRepository customerRepo  = new CustomerRepository();
            PromoCodeRepository promoRepo    = new PromoCodeRepository();
            OrderRepository orderRepo        = new OrderRepository();

            ValidationService validationService   = new ValidationService(customerRepo, productRepo, promoRepo);
            PricingEngine pricingEngine           = new PricingEngine();
            PromotionEngine promotionEngine       = new PromotionEngine(promoRepo);
            ShippingCalculator shippingCalculator = new ShippingCalculator();
            InventoryService inventoryService     = new InventoryService(productRepo);
            FraudDetectionService fraudService    = new FraudDetectionService(orderRepo);

            OrderProcessingService orderService = new OrderProcessingService(
                customerRepo, productRepo, validationService, pricingEngine,
                promotionEngine, shippingCalculator, inventoryService, fraudService, orderRepo
            );

            // 3. Process the order
            OrderResult result = orderService.processOrder(order);

            // 4. Output JSON result to stdout
            System.out.println(JsonUtils.toJson(result));

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
