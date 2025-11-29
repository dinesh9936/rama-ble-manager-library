# BLE Helper Library (Android)
A simple and beginner-friendly Bluetooth Low Energy (BLE) library that makes BLE-related tasks easier for Android native developers.

## About

This library is built to simplify **Bluetooth Low Energy (BLE)** development on Android.  
Android’s BLE APIs are known to be complex and require a lot of boilerplate code.  
This library abstracts the difficult parts and provides **clean, high-level APIs** for common BLE operations.

---

## Scan Features

This library allows you to scan for BLE devices with multiple customizable options:

### Scan with the following parameters:

1. **Scan with timeout**  
   Start scanning and automatically stop after a specified duration and default time out is 10 seconds.
   ```kotlin
       CoroutineScope(Dispatchers.Main).launch {
                bleClient.scanDevices(
                    scanTimeout = 2_000L // You can pass this time in milliseconds or default is 10_000 miliseconds. 
                ).catch { e ->
                    if (e is BleScanError) {
                        Log.e(TAG, "BleTestScreen: ${e.message} and ${e.errorCode}") // Here you will get errorMessage and errorCode before start scan.
                    }else{
                        Log.e(TAG, "BleTestScreen: ${e.message}")  // Here you can get unHandled error
                    }
                }.collect { device ->
                    Log.d(TAG, "BleTestScreen: ${device.name}") // Here you can get device one by one during scan.
                }
            }


2. **Scan for devices with a specific service UUID**  
   Helps you discover only the BLE devices that offer the service you need.
    ```kotlin
        CoroutineScope(Dispatchers.Main).launch {
                bleClient.scanDevices(
                    scanServiceUUID = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  // Here you can pass you device service UUID to scan only your device.
                ).catch { e ->
                    if (e is BleScanError) {
                        Log.e(TAG, "BleTestScreen: ${e.message} and ${e.errorCode}")
                    }else{
                        Log.e(TAG, "BleTestScreen: ${e.message}")
                    }
                }.collect { device ->
                    Log.d(TAG, "BleTestScreen: ${device.name}")
                }
            }

3. **Scan for devices with names starting with a specific prefix**  
   Useful when targeting devices with naming patterns (e.g., *"BLE_"*, *"MyDevice"*).

4. **Check Bluetooth of device is enabled or not before scan.**
   We will check Bluetooth enabled or not before start scan and return error message and error code.

5. **Check Scan permission allowed or not before scan.**
   We will check Scan permission allowed or not before start scan and return error message and error code.

