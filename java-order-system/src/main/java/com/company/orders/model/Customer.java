package com.company.orders.model;

/**
 * Represents a customer in the system.
 *
 * LEGACY NOTE: registrationDate is stored as a plain String in "YYYY-MM-DD" format.
 * This was originally a java.util.Date field in the v1 schema but was converted
 * to String during the 2019 database migration to avoid timezone ambiguity bugs.
 * FraudDetectionService parses this string using LocalDate.parse() for age calculations.
 *
 * LEGACY NOTE: The 'country' field on Customer represents billing country.
 * Shipping destination is determined by shippingZone on the Order, not this field.
 * This mismatch is a known design debt from when international shipping was added.
 */
public class Customer {
    private String id;
    private String name;
    private String email;
    private CustomerTier tier;
    private String registrationDate;   // "YYYY-MM-DD" — legacy string date, not java.util.Date
    private Address billingAddress;
    private String country;

    public Customer() {}

    public Customer(String id, String name, String email, CustomerTier tier,
                    String registrationDate, Address billingAddress, String country) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.registrationDate = registrationDate;
        this.billingAddress = billingAddress;
        this.country = country;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public CustomerTier getTier() { return tier; }
    public void setTier(CustomerTier tier) { this.tier = tier; }

    public String getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }

    public Address getBillingAddress() { return billingAddress; }
    public void setBillingAddress(Address billingAddress) { this.billingAddress = billingAddress; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
