package Entity;

import ExceptionPkg.InvalidOrderException;
import ExceptionPkg.StockUnavailableException;

/**
 * Order.java
 * This class represents information about order placed by Customer, which contains
 * orderID unique for all orders, Customer who placed order, Product which customer is order & quantity of the product
 */
public class Order {
    private int orderId;
    private Customer customer;
    private Product product;
    private int quantity;

    /**
     * @param orderId
     * @param customer
     * @param product
     * @param quantity
     * @throws InvalidOrderException if Order Quantity is less than 0
     * @throws StockUnavailableException if Order Quantity is more than available stock of product
     */
    public Order(int orderId, Customer customer, Product product, int quantity) throws InvalidOrderException, StockUnavailableException {
        if (quantity < 1) {
            throw new InvalidOrderException("Invalid Order Quantity");
        }
        product.reduceStock(quantity);
        this.orderId = orderId;
        this.customer = customer;
        this.product = product;
        this.quantity = quantity;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * calculateTotal() : calculate total of particular order placed by customer
     * @return calculated order amount
     */
    public double calculateTotal() {
        return product.getProductPrice() * quantity;
    }

    @Override
    public String toString() {
        return "Order{" +
               "orderId=" + orderId +
               ", customer=" + customer.getCustomerName() +
               ", product=" + product.getProductName() +
               ", quantity=" + quantity +
               '}';
    }
}
