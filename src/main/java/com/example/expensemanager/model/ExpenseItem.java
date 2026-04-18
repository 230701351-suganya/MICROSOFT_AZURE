package com.example.expensemanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItem {
    private String name;
    private Double price;
    private Double quantity;
    private Double totalPrice;
}