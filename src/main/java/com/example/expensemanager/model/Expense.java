package com.example.expensemanager.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "expenses")
public class Expense {

    @Id
    private String id;

    private String userEmail;
    private String merchantName;
    private Double amount;
    private String date;
    private String tax;
    private String category;
    private String blobUrl;
    private String status;

    // Advanced fields
    private String receiptType;
    private String taxRate;
    private String countryRegion;
    private String netAmount;
    private List<ExpenseItem> items;

    private String fileHash;
}