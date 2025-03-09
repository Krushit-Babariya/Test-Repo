package ExceptionPkg;

/**
 * OrderNotFoundException if order not found for deletion in customer's cart
 */
public class OrderNotFoundException extends Exception {
    public OrderNotFoundException(String message) {
        System.err.println("OOPS..!!, Order id not found");
    }
}