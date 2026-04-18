package com.example.expensemanager.service;

import com.example.expensemanager.model.Expense;
import com.example.expensemanager.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptDuplicateService {

    private final ExpenseRepository expenseRepository;

    public ReceiptDuplicateService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public boolean isExactDuplicate(String userEmail, String fileHash) {
        return expenseRepository.findByUserEmailAndFileHash(userEmail, fileHash).isPresent();
    }

    public boolean isContentDuplicate(String userEmail, String merchantName, String date, Double amount) {
        if (merchantName == null || merchantName.isBlank() || date == null || date.isBlank() || amount == null) {
            return false;
        }

        return !expenseRepository.findByUserEmailAndMerchantNameAndDateAndAmount(
                userEmail, merchantName, date, amount
        ).isEmpty();
    }
}