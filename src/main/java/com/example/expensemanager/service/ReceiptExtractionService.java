package com.example.expensemanager.service;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentFieldType;
import com.azure.core.credential.AzureKeyCredential;
import com.example.expensemanager.model.ExpenseItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReceiptExtractionService {

    @Value("${azure.document.endpoint}")
    private String endpoint;

    @Value("${azure.document.key}")
    private String key;

    private static final boolean DEBUG = true;

    public Map<String, Object> extractReceiptDataFromUrl(String sasUrl) {
        DocumentAnalysisClient client = new DocumentAnalysisClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();

        AnalyzeResult result = client
                .beginAnalyzeDocumentFromUrl("prebuilt-receipt", sasUrl)
                .getFinalResult();

        String fullText = result.getContent() != null ? result.getContent() : "";

        Map<String, Object> extracted = new HashMap<>();

        if (DEBUG) {
            System.out.println("\n================ AZURE RECEIPT DEBUG START ================\n");
            System.out.println("FULL OCR TEXT:\n" + fullText);
            System.out.println("\n===========================================================\n");
        }

        for (AnalyzedDocument document : result.getDocuments()) {
            Map<String, DocumentField> fields = document.getFields();

            if (DEBUG) {
                logExtractedFields(fields);
            }

            String merchantName = firstNonBlank(
                    getFieldContent(fields, "MerchantName"),
                    extractMerchantNameFromText(fullText)
            );

            String date = getFieldContent(fields, "TransactionDate");

            List<ExpenseItem> items = extractItems(fields.get("Items"));

            double subtotal = extractSubtotal(fields, items, fullText);
            double total = extractTotal(fields, fullText);
            double taxFromFields = extractTax(fields);
            double roundOff = extractRoundOff(fullText);
            double serviceCharge = extractServiceCharge(fullText);
            double discount = extractDiscount(fullText);

            // If total is still missing, derive from subtotal + known extras
            if (total == 0.0 && subtotal > 0.0) {
                double derived = subtotal + taxFromFields + serviceCharge + roundOff - discount;
                if (derived > 0.0) {
                    total = roundToTwoDecimals(derived);
                }
            }

            // Final reconciliation:
            // Since your DB has only one "tax" field, store all extra charges between subtotal and total
            double finalCharges = 0.0;

            if (total > 0.0 && subtotal > 0.0) {
                finalCharges = roundToTwoDecimals(total - subtotal);
            } else if (taxFromFields > 0.0 || serviceCharge != 0.0 || roundOff != 0.0 || discount != 0.0) {
                finalCharges = roundToTwoDecimals(taxFromFields + serviceCharge + roundOff - discount);
            }

            // If subtotal missing but items are present, derive subtotal from item totals
            if (subtotal == 0.0 && !items.isEmpty()) {
                subtotal = roundToTwoDecimals(sumItemTotals(items));
            }

            // If total still missing after everything, use subtotal + finalCharges
            if (total == 0.0 && subtotal > 0.0) {
                total = roundToTwoDecimals(subtotal + finalCharges);
            }

            // If subtotal missing but total and finalCharges exist
            if (subtotal == 0.0 && total > 0.0 && finalCharges != 0.0) {
                subtotal = roundToTwoDecimals(total - finalCharges);
            }

            double taxRate = 0.0;
            if (subtotal > 0.0 && finalCharges > 0.0) {
                taxRate = roundToTwoDecimals((finalCharges / subtotal) * 100.0);
            }
            String category = detectCategory(merchantName, items);
            extracted.put("merchantName", merchantName);
            extracted.put("date", date);
            extracted.put("amount", total);
            extracted.put("tax", finalCharges);
            extracted.put("receiptType", getFieldContent(fields, "ReceiptType"));
            extracted.put("countryRegion", getFieldContent(fields, "CountryRegion"));
            extracted.put("taxRate", taxRate > 0.0 ? taxRate : "");
            extracted.put("netAmount", subtotal);
            extracted.put("items", items);
            extracted.put("category", category);
            // Optional debug breakdown
            extracted.put("debugTaxFromFields", taxFromFields);
            extracted.put("debugRoundOff", roundOff);
            extracted.put("debugServiceCharge", serviceCharge);
            extracted.put("debugDiscount", discount);

            if (DEBUG) {
                System.out.println("----- FINAL MAPPED VALUES -----");
                System.out.println("merchantName     = " + merchantName);
                System.out.println("date             = " + date);
                System.out.println("subtotal         = " + subtotal);
                System.out.println("taxFromFields    = " + taxFromFields);
                System.out.println("serviceCharge    = " + serviceCharge);
                System.out.println("roundOff         = " + roundOff);
                System.out.println("discount         = " + discount);
                System.out.println("finalCharges     = " + finalCharges);
                System.out.println("amount           = " + total);
                System.out.println("taxRate          = " + taxRate);
                System.out.println("items count      = " + items.size());
                System.out.println("--------------------------------");
            }
        }

        if (DEBUG) {
            System.out.println("\n================ AZURE RECEIPT DEBUG END ==================\n");
        }

        return extracted;
    }

    private double extractSubtotal(Map<String, DocumentField> fields, List<ExpenseItem> items, String fullText) {
        double subtotal = parseDouble(getFieldContent(fields, "Subtotal"));

        if (subtotal == 0.0 && !items.isEmpty()) {
            subtotal = roundToTwoDecimals(sumItemTotals(items));
        }

        if (subtotal == 0.0) {
            subtotal = findAmountNearLabel(fullText,
                    "sub total",
                    "subtotal",
                    "sub-total");
        }

        return roundToTwoDecimals(subtotal);
    }

    private double extractTotal(Map<String, DocumentField> fields, String fullText) {
        double total = parseDouble(getFieldContent(fields, "Total"));

        if (total == 0.0) {
            total = findAmountNearLabel(fullText,
                    "grand total",
                    "total amount",
                    "gross amount",
                    "amount due",
                    "grandtotal");
        }

        if (total == 0.0) {
            total = findStandaloneLastBigAmount(fullText);
        }

        return roundToTwoDecimals(total);
    }

    private double extractTax(Map<String, DocumentField> fields) {
        double totalTax = parseDouble(getFieldContent(fields, "TotalTax"));

        if (totalTax == 0.0) {
            totalTax = extractTaxFromTaxDetails(fields.get("TaxDetails"));
        }

        return roundToTwoDecimals(totalTax);
    }

    private double extractTaxFromTaxDetails(DocumentField taxDetailsField) {
        double totalTax = 0.0;

        if (taxDetailsField == null || taxDetailsField.getType() != DocumentFieldType.LIST) {
            return 0.0;
        }

        for (DocumentField taxItem : taxDetailsField.getValueAsList()) {
            if (taxItem == null || taxItem.getType() != DocumentFieldType.MAP) {
                continue;
            }

            Map<String, DocumentField> taxMap = taxItem.getValueAsMap();
            totalTax += parseDouble(getMapFieldContent(taxMap, "Amount"));
        }

        return roundToTwoDecimals(totalTax);
    }

    private double extractRoundOff(String text) {
        return findSignedAmountNearLabel(text,
                "round off",
                "roundoff",
                "rounding");
    }

    private double extractServiceCharge(String text) {
        return findAmountNearLabel(text,
                "service charge",
                "servicecharge",
                "packing charge",
                "delivery charge",
                "convenience fee",
                "cover charge");
    }

    private double extractDiscount(String text) {
        return findAmountNearLabel(text,
                "discount",
                "disc",
                "offer",
                "coupon");
    }

    private String extractMerchantNameFromText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] lines = text.split("\\R");
        for (String line : lines) {
            String cleaned = line == null ? "" : line.trim();
            if (!cleaned.isBlank() && cleaned.length() > 2 && !containsMostlyDigits(cleaned)) {
                return cleaned;
            }
        }
        return "";
    }

    private boolean containsMostlyDigits(String s) {
        int digits = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digits++;
        }
        return digits > s.length() / 2;
    }

    private double sumItemTotals(List<ExpenseItem> items) {
        double sum = 0.0;
        for (ExpenseItem item : items) {
            if (item != null && item.getTotalPrice() != null) {
                sum += item.getTotalPrice();
            }
        }
        return sum;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private double findAmountNearLabel(String text, String... labels) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        String normalizedText = text.replace("₹", " ").replace(",", "");

        for (String label : labels) {
            String regex = "(?i)" + Pattern.quote(label) + "[^\\d\\-]{0,30}(-?\\d+(?:\\.\\d{1,2})?)";
            Matcher matcher = Pattern.compile(regex).matcher(normalizedText);
            if (matcher.find()) {
                return parseDouble(matcher.group(1));
            }
        }

        return 0.0;
    }

    private double findSignedAmountNearLabel(String text, String... labels) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        String normalizedText = text.replace("₹", " ").replace(",", "");

        for (String label : labels) {
            String regex = "(?i)" + Pattern.quote(label) + "[^\\d\\-]{0,30}(-?\\d+(?:\\.\\d{1,2})?)";
            Matcher matcher = Pattern.compile(regex).matcher(normalizedText);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (Exception e) {
                    return 0.0;
                }
            }
        }

        return 0.0;
    }

    private double findStandaloneLastBigAmount(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        String normalized = text.replace(",", "").replace("₹", " ");
        Matcher matcher = Pattern.compile("(\\d{2,}(?:\\.\\d{1,2})?)").matcher(normalized);

        double last = 0.0;
        while (matcher.find()) {
            double value = parseDouble(matcher.group(1));
            if (value > last) {
                last = value;
            }
        }

        return last;
    }

    private void logExtractedFields(Map<String, DocumentField> fields) {
        if (fields == null || fields.isEmpty()) {
            System.out.println("No fields returned by Azure.");
            return;
        }

        System.out.println("========== AZURE EXTRACTED FIELDS ==========");

        for (Map.Entry<String, DocumentField> entry : fields.entrySet()) {
            String key = entry.getKey();
            DocumentField field = entry.getValue();

            System.out.println("Field Name   : " + key);
            System.out.println("Field Type   : " + (field != null ? field.getType() : "null"));
            System.out.println("Field Content: " + (field != null ? field.getContent() : "null"));

            if (field != null) {
                try {
                    DocumentFieldType type = field.getType();

                    if (type == DocumentFieldType.STRING) {
                        System.out.println("Typed Value  : " + field.getValueAsString());
                    } else if (type == DocumentFieldType.DOUBLE) {
                        System.out.println("Typed Value  : " + field.getValueAsDouble());
                    } else if (type == DocumentFieldType.LONG) {
                        System.out.println("Typed Value  : " + field.getValueAsLong());
                    } else if (type == DocumentFieldType.DATE) {
                        System.out.println("Typed Value  : " + field.getValueAsDate());
                    } else {
                        System.out.println("Typed Value  : [not printed for type " + type + "]");
                    }
                } catch (Exception e) {
                    System.out.println("Typed Value  : Error reading typed value - " + e.getMessage());
                }
            }

            System.out.println("--------------------------------------------");

            if (field != null && field.getType() == DocumentFieldType.LIST) {
                logListField(field);
            }
        }

        System.out.println("============================================");
    }

    private void logListField(DocumentField listField) {
        List<DocumentField> list = listField.getValueAsList();

        if (list == null || list.isEmpty()) {
            System.out.println("  List field is empty.");
            return;
        }

        int index = 1;
        for (DocumentField item : list) {
            System.out.println("  Item #" + index);

            if (item != null && item.getType() == DocumentFieldType.MAP) {
                Map<String, DocumentField> itemMap = item.getValueAsMap();

                for (Map.Entry<String, DocumentField> itemEntry : itemMap.entrySet()) {
                    String itemKey = itemEntry.getKey();
                    DocumentField itemValue = itemEntry.getValue();

                    System.out.println("    " + itemKey + " -> " +
                            (itemValue != null ? itemValue.getContent() : "null"));
                }
            } else {
                System.out.println("    Item is not a MAP");
            }

            System.out.println();
            index++;
        }
    }

    private String getFieldContent(Map<String, DocumentField> fields, String fieldName) {
        if (fields == null || fieldName == null) {
            return "";
        }

        DocumentField field = fields.get(fieldName);
        return field != null && field.getContent() != null ? field.getContent().trim() : "";
    }

    private List<ExpenseItem> extractItems(DocumentField itemsField) {
        List<ExpenseItem> items = new ArrayList<>();

        if (itemsField == null || itemsField.getType() != DocumentFieldType.LIST) {
            return items;
        }

        for (DocumentField itemField : itemsField.getValueAsList()) {
            if (itemField == null || itemField.getType() != DocumentFieldType.MAP) {
                continue;
            }

            Map<String, DocumentField> itemMap = itemField.getValueAsMap();

            String name = getMapFieldContent(itemMap, "Description");
            Double price = parseDouble(getMapFieldContent(itemMap, "Price"));
            Double quantity = parseDouble(getMapFieldContent(itemMap, "Quantity"));
            Double totalPrice = parseDouble(getMapFieldContent(itemMap, "TotalPrice"));

            items.add(new ExpenseItem(name, price, quantity, totalPrice));
        }

        return items;
    }

    private String detectCategory(String merchantName, List<ExpenseItem> items) {

        String text = ((merchantName != null ? merchantName : "") + " " + getItemsText(items)).toLowerCase();

        // FOOD / RESTAURANT
        if (contains(text,
                "restaurant", "cafe", "hotel", "pizza", "burger", "food", "bar",
                "buffet", "dining", "grill", "coffee", "chai", "tea")) {
            return "Food";
        }

        // GROCERIES
        if (contains(text,
                "mart", "supermarket", "store", "grocery", "provision", "hypermarket")) {
            return "Groceries";
        }

        // TRAVEL
        if (contains(text,
                "uber", "ola", "taxi", "bus", "rail", "flight", "metro")) {
            return "Travel";
        }

        // SHOPPING
        if (contains(text,
                "mall", "fashion", "clothing", "electronics", "amazon", "flipkart")) {
            return "Shopping";
        }

        // HEALTH
        if (contains(text,
                "pharmacy", "hospital", "clinic", "medical", "medicines")) {
            return "Health";
        }

        // default
        return "General";
    }

    private String getMapFieldContent(Map<String, DocumentField> fields, String key) {
        if (fields == null || key == null) {
            return "";
        }

        DocumentField field = fields.get(key);
        return field != null && field.getContent() != null ? field.getContent().trim() : "";
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        String cleaned = value.trim();

        boolean negative = cleaned.contains("-");

        cleaned = cleaned.replaceAll("[^0-9.]", "");

        if (cleaned.isBlank()) {
            return 0.0;
        }

        int firstDotIndex = cleaned.indexOf('.');
        if (firstDotIndex != -1) {
            String beforeDot = cleaned.substring(0, firstDotIndex + 1);
            String afterDot = cleaned.substring(firstDotIndex + 1).replace(".", "");
            cleaned = beforeDot + afterDot;
        }

        try {
            double parsed = Double.parseDouble(cleaned);
            return negative ? -parsed : parsed;
        } catch (NumberFormatException e) {
            if (DEBUG) {
                System.out.println("Invalid numeric value from Azure/OCR: " + value);
            }
            return 0.0;
        }
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    private boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getItemsText(List<ExpenseItem> items) {
        if (items == null) return "";

        StringBuilder sb = new StringBuilder();

        for (ExpenseItem item : items) {
            if (item != null && item.getName() != null) {
                sb.append(item.getName()).append(" ");
            }
        }

        return sb.toString();
    }
}