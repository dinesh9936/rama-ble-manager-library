# BLE Helper Library (Android)

A simple and beginner-friendly Bluetooth Low Energy (BLE) library that makes BLE-related tasks
easier for Android native developers.

## About

This library is built to simplify **Bluetooth Low Energy (BLE)** development on Android.  
Android’s BLE APIs are known to be complex and require a lot of boilerplate code.  
This library abstracts the difficult parts and provides **clean, high-level APIs** for common BLE
operations.

---

## Scan Features

This library allows you to scan for BLE devices with multiple customizable options:

### Scan with the following parameters:

1. **You have to create a bleClient of BleClient class**
   ````kotlin
   val bleClient = BleClientFactory.create(context, BleConfig()) 
   // Here context is you activity context and BleConfig is configuration class 
   // you can pass configuration through this class.
   // val bleConfig = BleConfig(
   //           val scanPeriodMillis: Long = 10_000L,
   //           val autoReconnect: Boolean = false,
   //           val enableLogging: Boolean = true,
   //           val retryCount: Int = 2,
   //           val retryDelayMillis: Long = 150L,
   //           val reconnectAttempts: Int = 3,
   //           val reconnectDelayMillis: Long = 1000L
   //          )

 2. **Scan with timeout**  
      Start scanning and automatically stop after a specified duration and default time out is 10
      seconds.
      ```kotlin
          // You can pass timeout in milliseconds
          // default timeout is 10_000L  
          CoroutineScope(Dispatchers.Main).launch {
                   bleClient.scanDevices(
                       scanTimeout = 2_000L 
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


3. **Scan for devices with a specific service UUID**  
   Helps you discover only the BLE devices that offer the service you need.
    ```kotlin
        // Here you can pass you device service UUID to scan only your device.
        // ByDefault is scan all devices
        CoroutineScope(Dispatchers.Main).launch {
                bleClient.scanDevices(
                    scanServiceUUID = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" 
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

4. **Scan for devices with names starting with a specific prefix or by passing full name of device
   **  
   Useful when targeting devices with naming patterns (e.g., *"BLE_"*, *"MyDevice"*).
     ````kotlin
         // If you want to scan all devices which has prefix then pass device name with prefix and pass isDeviceNamePrefix as true
         // If you want to scan only specific device then pass deviceName and isDeviceNamePrefix as false
          CoroutineScope(Dispatchers.Main).launch {
                  bleClient.scanDevices(
                    deviceName = "BLE_",  
                    isDeviceNamePrefix = true
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
5. **Check Bluetooth of device is enabled or not before scan.**
   We will check Bluetooth enabled or not before start scan and return error message and error code.
   ````kotlin
      // When Bluetooth of you device is disabled then it will return 
      // Error (message = Bluetooth is disabled , code = 1230)
      // Here you can show warning message to enable Bluetooth device.

6. **Check Scan permission allowed or not before scan.**
   We will check Scan permission allowed or not before start scan and return error message and error
   code.
   ````kotlin
       // Required permission before scan 
       // 1- NearByDevice for android version above 12 if not allowed ->
       //    Error(msg = Scan permissions (NearByDevice) is required for BLE scanning, code = 12320) 
       // 2- Location for android below 13
       //    Error(msg = Scan permissions (Location) is required for BLE scanning, code = 12321) 
       

