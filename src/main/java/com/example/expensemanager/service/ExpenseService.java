package com.example.expensemanager.service;

import com.example.expensemanager.model.Expense;
import com.example.expensemanager.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BlobStorageService blobStorageService;

    public ExpenseService(ExpenseRepository expenseRepository,BlobStorageService blobStorageService) {
        this.expenseRepository = expenseRepository;
        this.blobStorageService=blobStorageService;
    }

    public Expense saveExpense(Expense expense) {
        return expenseRepository.save(expense);
    }

    public List<Expense> getExpensesByUser(String userEmail) {
        return expenseRepository.findByUserEmail(userEmail);
    }

    public void deleteExpense(String id, String userEmail) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getUserEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        // 🔥 Delete from Azure
        if (expense.getBlobUrl() != null && !expense.getBlobUrl().isEmpty()) {
            blobStorageService.deleteFile(expense.getBlobUrl());
        }

        // 🔥 Delete from DB
        expenseRepository.deleteById(id);
    }
}