package ExceptionPkg;

/**
 * StockUnavailableException if ordered item stock is not available
 */
public class StockUnavailableException extends Exception {
    public StockUnavailableException(String msg) {
        System.err.println("Sorry..!!, We are not able to place your order due to Insufficient Stock");
    }
}