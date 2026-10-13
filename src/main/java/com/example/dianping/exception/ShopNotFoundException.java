package com.example.dianping.exception;

public class ShopNotFoundException extends RuntimeException{
    public ShopNotFoundException(String message) {
        super(message);
    }
}
