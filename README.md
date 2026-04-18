# AI-Powered Expense & Receipt Manager

## 📌 Overview
This is a full-stack expense management system that allows users to upload receipts, automatically extract important details using AI, and manage expenses efficiently. The system integrates cloud services and AI to simplify expense tracking.

---

## 🚀 Features
- Upload receipt images and documents  
- Automatic data extraction (merchant, date, amount, tax, items)  
- Secure file storage using Azure Blob Storage  
- Generate SAS URLs for controlled access to uploaded files  
- Expense tracking and management  
- Delete expense with automatic cloud file removal  
- Handles duplicate detection and validation  
- Clean backend architecture with REST APIs  

---

## 🛠 Tech Stack
- **Backend:** Java, Spring Boot  
- **Database:** MongoDB  
- **Cloud:** Microsoft Azure Blob Storage  
- **AI Service:** Azure Document Intelligence (OCR + Data Extraction)  
- **Frontend:** React (Basic UI)  

---

## ☁️ Azure Integration
- Stored uploaded receipts in Azure Blob Storage  
- Generated secure SAS URLs for temporary access  
- Integrated Azure Document Intelligence to extract structured data from receipts  
- Ensured file consistency by deleting files from cloud when expense is removed  

---

## 🏗 Architecture
- Controller Layer – Handles API requests  
- Service Layer – Business logic  
- Repository Layer – Database operations  

---
