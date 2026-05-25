package com.company.orders.model;

/**
 * Fraud detection flags raised by FraudDetectionService.
 *
 * Severity levels determine order outcome:
 *   HIGH   -> Order is automatically REJECTED
 *   MEDIUM -> Order PROCESSES but is flagged for manual review
 *   LOW    -> Order PROCESSES with informational note
 *
 * Multiple MEDIUM flags on a single order do NOT escalate to HIGH.
 * Manual review queue receives all flagged orders regardless of outcome.
 */
public enum FraudFlag {

    HIGH_VALUE_NEW_CUSTOMER(
        "HIGH",
        "Order total exceeds $1,000 threshold for accounts under 90 days old"
    ),
    PROMO_ABUSE(
        "MEDIUM",
        "Promotional code applied to high-value order from account under 30 days old"
    ),
    SUSPICIOUS_ZONE(
        "MEDIUM",
        "High-value order shipping to zone 4+ from a Bronze-tier account"
    ),
    BULK_PURCHASE(
        "LOW",
        "Single product quantity >= 10 units in order from Bronze-tier account"
    );

    private final String severity;
    private final String defaultDescription;

    FraudFlag(String severity, String defaultDescription) {
        this.severity = severity;
        this.defaultDescription = defaultDescription;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }
}
