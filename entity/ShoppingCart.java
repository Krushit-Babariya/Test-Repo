package Entity;

import ExceptionPkg.OrderNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * ShoppingCart.java : This class contains all information about Customer's shopping information and list of product
 */
public class ShoppingCart {
    private Customer customer;
    private List<Order> orderedItems;

    public ShoppingCart(Customer customer) {
        this.customer = customer;
        this.orderedItems = new ArrayList<>();
    }

    /**
     * addOrder(Order)
     * @param order add placed order in List
     */

    public void addOrder(Order order) {
        orderedItems.add(order);
    }

    /**
     * removeOrder(int orderId)
     * @param orderId remove via product id
     * @throws OrderNotFoundException if order not found for deletion
     */
    public void removeOrder(int orderId) throws OrderNotFoundException {
        //like flag variable
        boolean removed = false;

        //traverse through list
        for(Order order : orderedItems){
            //if order id found
            if(order.getOrderId() == orderId){
                orderedItems.remove(orderId); //remove that order
                removed = true;
            }
        }

        //if order not found
        if (!removed) {
            throw new OrderNotFoundException("Order with ID " + orderId + " not found in your order list");
        }
    }

    /**
     * viewCart()  for customers cart
     */
    public void viewCart() {
        if (orderedItems.isEmpty()) { //if cart is empty
            System.out.println("Cart is empty.");
        } else {
            //System.out.println("Size :: " + orderedItems.size());
            //print cart  items
            System.out.println("======================================");
            for(Order order : orderedItems){
                System.out.println(order);
            }
            System.out.println("======================================");
        }
    }

    /**
     * checkout() : checkout from the cart and generate total bill amount
     */
    public void checkout() {
        Double totalPrice = 0.0;

        //add all prices from cart items
        for(Order order : orderedItems){
            totalPrice += order.calculateTotal();
        }

        //calculate GST
        Double GST = totalPrice * 0.18;

        //display total bill amount
        System.out.println("Bill Amount \t:: \t" + totalPrice);
        System.out.println("GST \t\t :: \t" + GST);
        System.out.println("----------------------------");
        System.out.println("Total Amount :: " + (totalPrice + GST));

        //clear the cart
        orderedItems.clear();
    }
}
