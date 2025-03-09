package ExceptionPkg;

/**
 * InvalidOrderException for negative order Quantity
 */
public class InvalidOrderException extends  Exception{
    public InvalidOrderException(String str){
        System.err.println("Please Enter Valid Order Quantity");
    }
}
