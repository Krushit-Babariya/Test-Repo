package Entity;

import java.util.Objects;

/**
 * Customer.java
 * This class represents information about product, which contains
 * customerID, customerName,customerEmail, customerPhone
 *
 */
public class Customer {
    private int customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    public Customer(int customerId, String customerName, String customerEmail, String customerPhone) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    /**
     * equals(Object o) : which check for the if customer already added on shopNow portal, it checks it via
     *                      their email & phone, because two customer do not have same mail & phone number.
     * @param o    takes object as argument
     * @return      return true if product already registered
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return Objects.equals(customerEmail, customer.customerEmail) && Objects.equals(customerPhone, customer.customerPhone); // check only using phone & mail
    }

    /**
     * hashCode() : generate hashcode by containing only one parameter customerEmail, custerPhone for uniqueness
     * @return      generated hashcode
     */
    @Override
    public int hashCode() {
        return Objects.hash(customerEmail, customerPhone);
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId=" + customerId +
                ", customerName='" + customerName + '\'' +
                ", customerEmail='" + customerEmail + '\'' +
                ", customerPhone='" + customerPhone + '\'' +
                '}';
    }
}
