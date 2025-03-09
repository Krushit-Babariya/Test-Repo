package Entity;

import ExceptionPkg.StockUnavailableException;

import java.util.Objects;

/**
 * Product.java
 * This class represents information about product, which contains
 * productID, productName,productPrice, productQuantity
 *
 */
public class Product {
    private int productID;
    private String productName;
    private double productPrice;
    private int productQuantity;

    public Product(int productID, String productName, double productPrice, int productQuantity) {
        this.productID = productID;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productQuantity = productQuantity;
    }

    public int getProductID() {
        return productID;
    }

    public void setProductID(int productID) {
        this.productID = productID;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(double productPrice) {
        this.productPrice = productPrice;
    }

    public int getProductQuantity() {
        return productQuantity;
    }

    public void setProductQuantity(int productQuantity) {
        this.productQuantity = productQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return productID == product.productID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(productID);
    }

    @Override
    public String toString() {
        return "Product{" +
                "productID=" + productID +
                ", productName='" + productName + '\'' +
                ", productPrice=" + productPrice +
                ", productQuantity=" + productQuantity +
                '}';
    }

    public void reduceStock(int quantityToReduce) throws StockUnavailableException {
        if (quantityToReduce > productQuantity) {
            throw new StockUnavailableException("Insufficient Stock");
        }
        this.productQuantity -= quantityToReduce;
    }
}
