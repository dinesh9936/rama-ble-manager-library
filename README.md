# BLE Helper Library
A simple and beginner-friendly Bluetooth Low Energy (BLE) library that makes BLE-related tasks easier for Android native developers.

## About

This library is built to simplify **Bluetooth Low Energy (BLE)** development on Android.  
Android’s BLE APIs are known to be complex and require a lot of boilerplate code.  
This library abstracts the difficult parts and provides **clean, high-level APIs** for common BLE operations.

---

## 🔍 Scan Features

This library allows you to scan for BLE devices with multiple customizable options:

### ✔ Scan with the following parameters:

1. **Scan with timeout**  
   Start scanning and automatically stop after a specified duration.

2. **Scan for devices with a specific service UUID**  
   Helps you discover only the BLE devices that offer the service you need.

3. **Scan for devices with names starting with a specific prefix**  
   Useful when targeting devices with naming patterns (e.g., *"BLE_"*, *"MyDevice"*).

---
