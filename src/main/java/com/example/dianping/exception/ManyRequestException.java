package com.example.dianping.exception;

public class ManyRequestException extends RuntimeException{
    public ManyRequestException(String message) {
        super(message);
    }
}
