package com.reviewer.model;

public class Review {
    private String marketplace;
    private String productName;
    private String productId;
    private String author;
    private int rating;
    private String text;
    private String date;

    public Review(String marketplace, String productName, String productId,
                  String author, int rating, String text, String date) {
        this.marketplace = marketplace;
        this.productName = productName;
        this.productId = productId;
        this.author = author;
        this.rating = rating;
        this.text = text;
        this.date = date;
    }

    public String getMarketplace() { return marketplace; }
    public String getProductName() { return productName; }
    public String getProductId()  { return productId; }
    public String getAuthor() { return author; }
    public int    getRating() { return rating; }
    public String getText() { return text; }
    public String getDate() { return date; }

    @Override
    public String toString() {
        return String.format("[%d★] %s: %s", rating, author, text);
    }
}
