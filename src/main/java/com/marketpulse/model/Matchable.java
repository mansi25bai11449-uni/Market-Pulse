package com.marketpulse.model;

public interface Matchable {
    boolean canMatchWith(Order other);
    double getPrice();
    int getRemainingQuantity();
    void fill(int quantity);
}
