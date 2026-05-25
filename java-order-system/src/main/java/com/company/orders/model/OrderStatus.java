package com.company.orders.model;

/**
 * Represents the lifecycle state of an order as it moves through the processing pipeline.
 *
 * State machine transitions:
 *   PENDING -> VALIDATED -> PRICED -> INVENTORY_CHECKED -> FRAUD_CHECKED -> PROCESSED
 *                                                                        -> REJECTED
 *
 * An order can be REJECTED at any stage after VALIDATED.
 * REJECTED orders do NOT have inventory reserved.
 */
public enum OrderStatus {
    PENDING,
    VALIDATED,
    PRICED,
    INVENTORY_CHECKED,
    FRAUD_CHECKED,
    PROCESSED,
    REJECTED
}
