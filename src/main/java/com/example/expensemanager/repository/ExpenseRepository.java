package com.example.expensemanager.repository;

import com.example.expensemanager.model.Expense;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends MongoRepository<Expense, String> {
    List<Expense> findByUserEmail(String userEmail);

    Optional<Expense> findByUserEmailAndFileHash(String userEmail, String fileHash);

    List<Expense> findByUserEmailAndMerchantNameAndDateAndAmount(
            String userEmail, String merchantName, String date, Double amount);
}