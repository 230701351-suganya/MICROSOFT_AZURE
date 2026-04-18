//package com.example.expensemanager.service;
//
//import com.example.expensemanager.model.ExpenseItem;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//public class ReceiptPostProcessingService {
//
//    public List<ExpenseItem> cleanAndNormalizeItems(List<ExpenseItem> rawItems) {
//        List<ExpenseItem> cleanedItems = cleanExtractedItems(rawItems);
//        normalizeItemTotals(cleanedItems);
//        removeEmptyItems(cleanedItems);
//        return cleanedItems;
//    }
//
//    private List<ExpenseItem> cleanExtractedItems(List<ExpenseItem> rawItems) {
//        List<ExpenseItem> cleaned = new ArrayList<>();
//
//        if (rawItems == null) {
//            return cleaned;
//        }
//
//        for (ExpenseItem current : rawItems) {
//            String name = current.getName() != null ? current.getName().trim() : "";
//            double price = current.getPrice() != null ? current.getPrice() : 0.0;
//            double quantity = current.getQuantity() != null ? current.getQuantity() : 0.0;
//            double totalPrice = current.getTotalPrice() != null ? current.getTotalPrice() : 0.0;
//
//            boolean hasOnlyName = !name.isBlank() && price == 0.0 && quantity == 0.0 && totalPrice == 0.0;
//
//            boolean isWeakFragment =
//                    name.equalsIgnoreCase("pack") ||
//                            name.equalsIgnoreCase("500g") ||
//                            name.equalsIgnoreCase("500 g") ||
//                            name.equalsIgnoreCase("2.5kg") ||
//                            name.equalsIgnoreCase("2.5 kg") ||
//                            name.equalsIgnoreCase("1kg") ||
//                            name.equalsIgnoreCase("1 kg") ||
//                            name.toLowerCase().matches(".*\\b(kg|g|gm|ml|l|ltr|pcs|pack)\\b.*") ||
//                            name.length() <= 10;
//
//            if (hasOnlyName && !cleaned.isEmpty()) {
//                ExpenseItem previous = cleaned.get(cleaned.size() - 1);
//                previous.setName((previous.getName() + " " + name).trim());
//                continue;
//            }
//
//            if (isWeakFragment && !cleaned.isEmpty() && (price == 0.0 || totalPrice == 0.0)) {
//                ExpenseItem previous = cleaned.get(cleaned.size() - 1);
//                previous.setName((previous.getName() + " " + name).trim());
//
//                if ((previous.getPrice() == null || previous.getPrice() == 0.0) && price > 0.0) {
//                    previous.setPrice(price);
//                }
//                if ((previous.getQuantity() == null || previous.getQuantity() == 0.0) && quantity > 0.0) {
//                    previous.setQuantity(quantity);
//                }
//                if ((previous.getTotalPrice() == null || previous.getTotalPrice() == 0.0) && totalPrice > 0.0) {
//                    previous.setTotalPrice(totalPrice);
//                }
//                continue;
//            }
//
//            cleaned.add(current);
//        }
//
//        return cleaned;
//    }
//
//    private void normalizeItemTotals(List<ExpenseItem> items) {
//        if (items == null) {
//            return;
//        }
//
//        for (ExpenseItem item : items) {
//            double price = item.getPrice() != null ? item.getPrice() : 0.0;
//            double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
//            double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;
//
//            if (total == 0.0 && price > 0.0 && quantity > 0.0) {
//                item.setTotalPrice(price * quantity);
//            }
//
//            if (quantity == 1.0 && price > 0.0 && total > 0.0 && Math.abs(price - total) > 0.01) {
//                item.setTotalPrice(price);
//            }
//        }
//    }
//
//    private void removeEmptyItems(List<ExpenseItem> items) {
//        items.removeIf(item -> {
//            String name = item.getName() != null ? item.getName().trim() : "";
//            double price = item.getPrice() != null ? item.getPrice() : 0.0;
//            double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
//            double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;
//
//            return name.isBlank() && price == 0.0 && quantity == 0.0 && total == 0.0;
//        });
//    }
//}
package com.example.expensemanager.service;

import com.example.expensemanager.model.ExpenseItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReceiptPostProcessingService {

    public List<ExpenseItem> cleanAndNormalizeItems(List<ExpenseItem> rawItems) {
        List<ExpenseItem> cleanedItems = cleanExtractedItems(rawItems);
        normalizeItemTotals(cleanedItems);
        removeEmptyItems(cleanedItems);
        return cleanedItems;
    }

    private List<ExpenseItem> cleanExtractedItems(List<ExpenseItem> rawItems) {
        List<ExpenseItem> cleaned = new ArrayList<>();

        if (rawItems == null) {
            return cleaned;
        }

        for (ExpenseItem current : rawItems) {
            if (current == null) {
                continue;
            }

            String name = current.getName() != null ? current.getName().trim() : "";
            double price = current.getPrice() != null ? current.getPrice() : 0.0;
            double quantity = current.getQuantity() != null ? current.getQuantity() : 0.0;
            double totalPrice = current.getTotalPrice() != null ? current.getTotalPrice() : 0.0;

            boolean hasOnlyName = !name.isBlank() && price == 0.0 && quantity == 0.0 && totalPrice == 0.0;

            String lowerName = name.toLowerCase(Locale.ROOT);

            boolean isWeakFragment =
                    lowerName.equals("pack") ||
                            lowerName.equals("500g") ||
                            lowerName.equals("500 g") ||
                            lowerName.equals("2.5kg") ||
                            lowerName.equals("2.5 kg") ||
                            lowerName.equals("1kg") ||
                            lowerName.equals("1 kg") ||
                            lowerName.matches(".*\\b(kg|g|gm|ml|l|ltr|pcs|pack)\\b.*") ||
                            name.length() <= 10;

            if (hasOnlyName && !cleaned.isEmpty()) {
                ExpenseItem previous = cleaned.get(cleaned.size() - 1);
                String previousName = previous.getName() != null ? previous.getName() : "";
                previous.setName((previousName + " " + name).trim());
                continue;
            }

            if (isWeakFragment && !cleaned.isEmpty() && (price == 0.0 || totalPrice == 0.0)) {
                ExpenseItem previous = cleaned.get(cleaned.size() - 1);
                String previousName = previous.getName() != null ? previous.getName() : "";
                previous.setName((previousName + " " + name).trim());

                if ((previous.getPrice() == null || previous.getPrice() == 0.0) && price > 0.0) {
                    previous.setPrice(price);
                }
                if ((previous.getQuantity() == null || previous.getQuantity() == 0.0) && quantity > 0.0) {
                    previous.setQuantity(quantity);
                }
                if ((previous.getTotalPrice() == null || previous.getTotalPrice() == 0.0) && totalPrice > 0.0) {
                    previous.setTotalPrice(totalPrice);
                }
                continue;
            }

            cleaned.add(current);
        }

        return cleaned;
    }

    private void normalizeItemTotals(List<ExpenseItem> items) {
        if (items == null) {
            return;
        }

        for (ExpenseItem item : items) {
            if (item == null) {
                continue;
            }

            double price = item.getPrice() != null ? item.getPrice() : 0.0;
            double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
            double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

            if (total == 0.0 && price > 0.0 && quantity > 0.0) {
                item.setTotalPrice(price * quantity);
            }

            if (quantity == 1.0 && price > 0.0 && total > 0.0 && Math.abs(price - total) > 0.01) {
                item.setTotalPrice(price);
            }
        }
    }

    private void removeEmptyItems(List<ExpenseItem> items) {
        if (items == null) {
            return;
        }

        items.removeIf(item -> {
            if (item == null) {
                return true;
            }

            String name = item.getName() != null ? item.getName().trim() : "";
            double price = item.getPrice() != null ? item.getPrice() : 0.0;
            double quantity = item.getQuantity() != null ? item.getQuantity() : 0.0;
            double total = item.getTotalPrice() != null ? item.getTotalPrice() : 0.0;

            return name.isBlank() && price == 0.0 && quantity == 0.0 && total == 0.0;
        });
    }
}