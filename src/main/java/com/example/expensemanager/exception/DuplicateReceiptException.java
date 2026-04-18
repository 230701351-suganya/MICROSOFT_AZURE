package com.example.expensemanager.exception;

public class DuplicateReceiptException extends RuntimeException {
    public DuplicateReceiptException(String message) {
        super(message);
    }
}