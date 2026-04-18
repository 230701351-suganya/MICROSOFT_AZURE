package com.example.expensemanager.controller;

import com.example.expensemanager.exception.DuplicateReceiptException;
import com.example.expensemanager.model.Expense;
import com.example.expensemanager.model.ExpenseItem;
import com.example.expensemanager.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "*")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final BlobStorageService blobStorageService;
    private final ReceiptExtractionService receiptExtractionService;
    private final ReceiptPostProcessingService receiptPostProcessingService;
    private final FileHashService fileHashService;
    private final ReceiptDuplicateService receiptDuplicateService;

    public ExpenseController(
            ExpenseService expenseService,
            BlobStorageService blobStorageService,
            ReceiptExtractionService receiptExtractionService,
            ReceiptPostProcessingService receiptPostProcessingService,
            FileHashService fileHashService,
            ReceiptDuplicateService receiptDuplicateService
    ) {
        this.expenseService = expenseService;
        this.blobStorageService = blobStorageService;
        this.receiptExtractionService = receiptExtractionService;
        this.receiptPostProcessingService = receiptPostProcessingService;
        this.fileHashService = fileHashService;
        this.receiptDuplicateService = receiptDuplicateService;
    }

    @PostMapping
    public Expense saveExpense(@RequestBody Expense expense, Authentication authentication) {
        String userEmail = authentication.getName();
        expense.setUserEmail(userEmail);
        return expenseService.saveExpense(expense);
    }

    @GetMapping
    public List<Expense> getMyExpenses(Authentication authentication) {
        String userEmail = authentication.getName();
        return expenseService.getExpensesByUser(userEmail);
    }
    @PostMapping("/upload")
    public Expense uploadReceipt(@RequestParam("file") MultipartFile file, Authentication authentication) throws Exception {
        String userEmail = authentication.getName();
        String fileHash = fileHashService.generateSHA256(file);

//        if (receiptDuplicateService.isExactDuplicate(userEmail, fileHash)) {
//            throw new DuplicateReceiptException("Duplicate receipt detected: same file already uploaded.");
//        }

        BlobStorageService.BlobUploadResult uploadResult = blobStorageService.uploadFileAndGenerateSas(file);

        Map<String, Object> extracted = receiptExtractionService.extractReceiptDataFromUrl(uploadResult.sasUrl());

        Expense expense = new Expense();
        expense.setUserEmail(userEmail);
        expense.setMerchantName(getStringValue(extracted, "merchantName", file.getOriginalFilename()));
        expense.setDate(getStringValue(extracted, "date", ""));
        expense.setTax(getStringValue(extracted, "tax", "0"));
        expense.setBlobUrl(uploadResult.blobUrl());
        expense.setStatus("PROCESSED");

        expense.setReceiptType(getStringValue(extracted, "receiptType", ""));
        expense.setCountryRegion(getStringValue(extracted, "countryRegion", ""));
        expense.setTaxRate(getStringValue(extracted, "taxRate", ""));
        expense.setNetAmount(getStringValue(extracted, "netAmount", ""));

        String amountStr = getStringValue(extracted, "amount", "0").replaceAll("[^0-9.]", "");
        expense.setAmount(amountStr.isBlank() ? 0.0 : Double.parseDouble(amountStr));

        @SuppressWarnings("unchecked")
        List<ExpenseItem> rawItems = (List<ExpenseItem>) extracted.get("items");

        List<ExpenseItem> cleanedItems = receiptPostProcessingService.cleanAndNormalizeItems(rawItems);

        expense.setItems(cleanedItems);

        expense.setCategory(getStringValue(extracted, "category", "General"));
        expense.setFileHash(fileHash);

//        if (receiptDuplicateService.isContentDuplicate(
//                userEmail,
//                expense.getMerchantName(),
//                expense.getDate(),
//                expense.getAmount()
//        )) {
//            throw new DuplicateReceiptException("Duplicate receipt detected: same merchant, date, and amount already exist.");
//        }

        return expenseService.saveExpense(expense);
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private String categorizeExpense(String merchantName, List<ExpenseItem> items) {
        if (merchantName != null) {
            String merchant = merchantName.toLowerCase();
            if (merchant.contains("mart") || merchant.contains("supermarket") || merchant.contains("family")) {
                return "Groceries";
            }
            if (merchant.contains("pharmacy") || merchant.contains("apollo")) {
                return "Medical";
            }
            if (merchant.contains("restaurant") || merchant.contains("cafe") || merchant.contains("hotel")) {
                return "Food";
            }
        }

        if (items != null) {
            for (ExpenseItem item : items) {
                String name = item.getName() == null ? "" : item.getName().toLowerCase();
                if (name.contains("soap") || name.contains("toothpaste") || name.contains("razor")) {
                    return "Household";
                }
            }
        }

        return "General";
    }

    @DeleteMapping("/{id}")
    public void deleteExpense(@PathVariable String id, Authentication authentication) {
        String userEmail = authentication.getName();
        expenseService.deleteExpense(id, userEmail);
    }
}