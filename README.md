# RamaBleManager

A reusable Android Bluetooth Low Energy (BLE) library designed to keep BLE code clean, testable, and independent from your UI.

`RamaBleManager` provides a simple API for:

- Checking BLE support and Bluetooth state
- Checking scan requirements separately from connection requirements
- Detecting missing runtime permissions
- Checking location-service requirements on older Android versions
- Scanning for nearby BLE devices
- Filtering scan results
- Stopping an active scan
- Connecting to a BLE device
- Configuring scan, connection, and timeout behavior
- Keeping Android-specific BLE implementation details inside the library

The library intentionally **does not request permissions or show Android UI**.  
It tells your app what is required, and your app decides when and how to ask the user.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Manifest Permissions](#manifest-permissions)
- [Create a BLE Client](#create-a-ble-client)
- [BLE Environment Checks](#ble-environment-checks)
- [Scan Permission Check](#scan-permission-check)
- [Request Missing Scan Permissions](#request-missing-scan-permissions)
- [Start Scanning](#start-scanning)
- [Filter Scan Results](#filter-scan-results)
- [Stop Scanning](#stop-scanning)
- [Connection Permission Check](#connection-permission-check)
- [Connect to a Device](#connect-to-a-device)
- [Recommended App Flow](#recommended-app-flow)
- [Environment State](#environment-state)
- [Android Permission Behavior](#android-permission-behavior)
- [Custom Configuration](#custom-configuration)
- [Error Handling](#error-handling)
- [Architecture](#architecture)
- [Example Activity](#example-activity)
- [Contributing](#contributing)

---

## Requirements

Current project baseline:

- Android minSdk: **24**
- Kotlin
- Kotlin Coroutines / Flow
- Android Bluetooth Low Energy support

Your application is responsible for requesting runtime permissions.

---

## Installation

### JitPack

Add JitPack to your repositories.

For newer Gradle projects, add it to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
        }
    }
}
```

Then add RamaBleManager to your app module:

```kotlin
dependencies {
    implementation("com.github.dinesh9936:RamaBleManager:1.0.0")
}
```

Replace `1.0.0` with the version/tag you want to use.

### Local module

If you are developing the library and application in the same Gradle project:

```kotlin
dependencies {
    implementation(project(":blecore"))
}
```

---

## Manifest Permissions

Add the BLE permissions to your application's `AndroidManifest.xml`.

```xml
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="false" />

    <!-- Android 11 and below -->
    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />

    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />

    <!-- Android 12+ -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="31" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_CONNECT" />

</manifest>
```

> RamaBleManager checks which permissions are required for the current Android version.  
> It does not request those permissions automatically.

---

## Create a BLE Client

Create the main library entry point with `BleClientFactory`.

```kotlin
val bleClient =
    BleClientFactory.create(
        context = applicationContext
    )
```

Keep the `BleClient` instance for as long as you need BLE operations.

For example:

```kotlin
class MainActivity : ComponentActivity() {

    private val bleClient by lazy {
        BleClientFactory.create(
            context = applicationContext
        )
    }
}
```

The public `BleClient` API provides:

```kotlin
interface BleClient {

    val environment: BleEnvironmentChecker

    fun scan(
        config: BleScanConfig = BleScanConfig()
    ): Flow<BleScanResult>

    suspend fun connect(
        device: BleDevice,
        config: BleConnectionConfig = BleConnectionConfig()
    ): BleConnection

    suspend fun stopScan()
}
```

---

# BLE Environment Checks

Before performing a BLE operation, check the requirements for that specific operation.

RamaBleManager intentionally separates:

```kotlin
bleClient.environment.checkScan()
```

from:

```kotlin
bleClient.environment.checkConnect()
```

This is important because scanning and connecting do not always require the same permissions.

---

## Scan Permission Check

Before scanning:

```kotlin
val environment =
    bleClient.environment.checkScan()

if (!environment.bleSupported) {
    // Device does not support BLE
    return
}

if (!environment.bluetoothEnabled) {
    // Bluetooth is disabled
    return
}

if (environment.missingPermissions.isNotEmpty()) {
    // Ask the user for these permissions
    return
}

if (
    environment.locationServiceRequired &&
    !environment.locationServiceEnabled
) {
    // Ask the user to enable Location Services
    return
}

if (environment.isReady) {
    // Safe to start scanning
}
```

You can also simply check:

```kotlin
if (bleClient.environment.checkScan().isReady) {
    // Start scanning
}
```

Use the individual fields when you want to show specific UI to the user.

---

## Request Missing Scan Permissions

RamaBleManager returns the exact permissions that are currently missing.

Your application can request those permissions using Android's Activity Result API.

```kotlin
private val scanPermissionLauncher =
    registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        val allGranted =
            result.values.all { it }

        if (allGranted) {
            startBleScan()
        }
    }
```

Then:

```kotlin
private fun checkScanPermission() {

    val environment =
        bleClient.environment.checkScan()

    if (environment.missingPermissions.isNotEmpty()) {

        scanPermissionLauncher.launch(
            environment
                .missingPermissions
                .toTypedArray()
        )

        return
    }

    startBleScan()
}
```

This keeps Android permission UI outside the library.

---

# Start Scanning

Scanning returns a Kotlin `Flow<BleScanResult>`.

```kotlin
lifecycleScope.launch {

    bleClient
        .scan()
        .catch { error ->
            Log.e(
                "BLE",
                "Scan failed",
                error
            )
        }
        .collect { result ->

            Log.d(
                "BLE",
                "Name: ${result.device.name}"
            )

            Log.d(
                "BLE",
                "Address: ${result.device.address}"
            )
        }
}
```

Always check the scan environment before scanning.

```kotlin
val state =
    bleClient.environment.checkScan()

if (state.isReady) {
    startBleScan()
}
```

---

## Filter Scan Results

Use `BleScanConfig` and `BleScanFilter`.

Example: scan only for devices advertising the Nordic UART service.

```kotlin
val scanConfig =
    BleScanConfig(
        allowDuplicates = false,
        filters = listOf(
            BleScanFilter(
                serviceUuid =
                    UUID.fromString(
                        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
                    )
            )
        )
    )
```

Start scanning:

```kotlin
lifecycleScope.launch {

    bleClient
        .scan(
            config = scanConfig
        )
        .collect { result ->

            val device =
                result.device

            Log.d(
                "BLE",
                "Found ${device.name} - ${device.address}"
            )
        }
}
```

The library should remain hardware-agnostic.

Applications decide:

- Device name filters
- Service UUID filters
- Which discovered device should be selected
- Which protocol the selected device uses

---

# Stop Scanning

Stop the current scan when you no longer need it.

```kotlin
lifecycleScope.launch {
    bleClient.stopScan()
}
```

Examples of when you may want to stop scanning:

- The target device was found
- The user leaves the screen
- The user taps Stop
- You are about to connect to a selected device
- Your application-specific scan period has finished

---

# Connection Permission Check

Connecting has a separate environment check.

```kotlin
val environment =
    bleClient.environment.checkConnect()
```

Then:

```kotlin
if (!environment.bleSupported) {
    return
}

if (!environment.bluetoothEnabled) {
    return
}

if (environment.missingPermissions.isNotEmpty()) {
    // Request only the missing connection permissions
    return
}

if (environment.isReady) {
    // Connect
}
```

On Android 12+, this normally checks `BLUETOOTH_CONNECT`.

It does not require the scan permission merely because you want to connect.

---

## Request Missing Connection Permissions

Create a separate launcher if you want scan and connection permission flows to stay independent.

```kotlin
private val connectPermissionLauncher =
    registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        val allGranted =
            result.values.all { it }

        if (allGranted) {
            // Continue your pending connection
        }
    }
```

Request only what RamaBleManager reports:

```kotlin
val environment =
    bleClient.environment.checkConnect()

if (environment.missingPermissions.isNotEmpty()) {

    connectPermissionLauncher.launch(
        environment
            .missingPermissions
            .toTypedArray()
    )
}
```

---

# Connect to a Device

A scan result contains a `BleDevice`.

For example:

```kotlin
val device: BleDevice =
    scanResult.device
```

Before connecting:

```kotlin
val environment =
    bleClient.environment.checkConnect()

if (!environment.isReady) {
    return
}
```

Then connect from a coroutine:

```kotlin
lifecycleScope.launch {

    try {

        val connection =
            bleClient.connect(
                device = device
            )

        Log.d(
            "BLE",
            "Connected to ${device.address}"
        )

        // Keep the returned BleConnection while
        // interacting with this device.

    } catch (error: Throwable) {

        Log.e(
            "BLE",
            "Connection failed",
            error
        )
    }
}
```

You can also provide a custom `BleConnectionConfig`:

```kotlin
val connection =
    bleClient.connect(
        device = device,
        config = BleConnectionConfig()
    )
```

The returned `BleConnection` represents the active BLE session.

Use the operations exposed by `BleConnection` in the version of RamaBleManager you are using for GATT communication such as service discovery, read/write, notifications, MTU/RSSI, and disconnect/close behavior.

---

# Recommended App Flow

A recommended BLE flow is:

```text
User taps Scan
      |
      v
checkScan()
      |
      +-- BLE unsupported ----------> Show unsupported message
      |
      +-- Bluetooth disabled -------> Ask user to enable Bluetooth
      |
      +-- Permission missing -------> Request scan permission
      |
      +-- Location disabled --------> Ask user to enable location
      |                               (when required)
      |
      v
scan()
      |
      v
Receive BleScanResult
      |
      v
User selects device
      |
      v
checkConnect()
      |
      +-- Connect permission missing -> Request permission
      |
      v
connect(device)
      |
      v
BleConnection
      |
      v
GATT operations
      |
      v
Disconnect / close
```

A key design principle is:

> **Check only what the next BLE operation needs.**

Do not require every BLE permission when the user only wants to scan or only wants to connect.

---

# Environment State

`BleEnvironmentState` gives your app detailed information about the current BLE environment.

```kotlin
data class BleEnvironmentState(
    val bleSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val requiredPermissions: List<String>,
    val missingPermissions: List<String>,
    val locationServiceRequired: Boolean,
    val locationServiceEnabled: Boolean
)
```

It also provides:

```kotlin
environment.isReady
```

`isReady` becomes `true` when:

- BLE is supported
- Bluetooth is enabled
- No required permissions are missing
- Location service is enabled when the operation requires it

You can also inspect:

```kotlin
environment.requirements
```

to see unmet BLE requirements.

Examples include:

- BLE not supported
- Bluetooth disabled
- Missing permissions
- Location service disabled

---

# Android Permission Behavior

RamaBleManager automatically determines permission requirements according to the Android version.

## Scanning

### Android 12+

Scan permission:

```text
android.permission.BLUETOOTH_SCAN
```

### Android 11 and below

Scan permission:

```text
android.permission.ACCESS_FINE_LOCATION
```

Location Services may also need to be enabled.

---

## Connecting

### Android 12+

Connection permission:

```text
android.permission.BLUETOOTH_CONNECT
```

### Android 11 and below

There is no equivalent BLE runtime connect permission.

This is why RamaBleManager provides separate APIs:

```kotlin
checkScan()
```

and:

```kotlin
checkConnect()
```

---

# Custom Configuration

You can provide a `BleClientConfig` when creating the client.

```kotlin
val bleClient =
    BleClientFactory.create(
        context = applicationContext,
        config = BleClientConfig()
    )
```

The factory also supports custom permission and environment policies.

```kotlin
val bleClient =
    BleClientFactory.create(
        context = applicationContext,
        config = BleClientConfig(),
        permissionProvider = myPermissionProvider,
        environmentPolicy = myEnvironmentPolicy
    )
```

This is useful when an application needs behavior different from the default Android policy.

The default implementation uses:

- `AndroidBlePermissionProvider`
- `AndroidBlePermissionChecker`
- `AndroidBleEnvironmentPolicy`
- `AndroidBleEnvironmentChecker`
- `AndroidBleScanner`
- `AndroidBleConnector`

Android-specific implementation classes remain internal to the library.

---

# Error Handling

BLE operations can fail for many reasons:

- Bluetooth was turned off
- Permission was revoked
- Scan failed
- Device moved out of range
- Connection timed out
- Peripheral rejected the connection
- GATT operation failed
- Device disconnected unexpectedly

Handle failures at the application boundary.

For scanning:

```kotlin
bleClient
    .scan(config)
    .catch { error ->
        Log.e(
            "BLE",
            "Scan failed",
            error
        )
    }
    .collect { result ->
        // Handle result
    }
```

For connection:

```kotlin
try {

    val connection =
        bleClient.connect(device)

} catch (error: Throwable) {

    Log.e(
        "BLE",
        "Connection failed",
        error
    )
}
```

Avoid swallowing BLE exceptions silently. Log enough information to diagnose problems, but do not log sensitive application data unnecessarily.

---

# Architecture

RamaBleManager follows a layered design.

```text
Your Android App
        |
        v
     BleClient
        |
        +----------------------+
        |                      |
        v                      v
BleEnvironmentChecker      BleScanner
        |
        v
 Permission +
 Environment
        |
        +----------------------+
                               |
                               v
                         BleConnector
                               |
                               v
                         BleConnection
                               |
                               v
                         Android BLE/GATT
```

The public application code depends on abstractions such as:

```text
BleClient
BleEnvironmentChecker
BleEnvironmentState
BlePermissionProvider
BleScanConfig
BleScanFilter
BleScanResult
BleDevice
BleConnectionConfig
BleConnection
```

Android implementation details stay under internal packages.

---

# Example Activity

The following example checks scan requirements, requests missing permissions, and starts scanning.

```kotlin
class MainActivity : ComponentActivity() {

    private val bleClient by lazy {
        BleClientFactory.create(
            context = applicationContext
        )
    }

    private val scanPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                permissions.values.all { it }

            if (allGranted) {
                startBleScan()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        checkAndStartScan()
    }

    private fun checkAndStartScan() {

        val environment =
            bleClient.environment.checkScan()

        if (!environment.bleSupported) {
            Log.d(TAG, "BLE is not supported")
            return
        }

        if (!environment.bluetoothEnabled) {
            Log.d(TAG, "Bluetooth is disabled")
            return
        }

        if (environment.missingPermissions.isNotEmpty()) {

            scanPermissionLauncher.launch(
                environment
                    .missingPermissions
                    .toTypedArray()
            )

            return
        }

        if (
            environment.locationServiceRequired &&
            !environment.locationServiceEnabled
        ) {
            Log.d(TAG, "Location service is disabled")
            return
        }

        startBleScan()
    }

    private fun startBleScan() {

        val config =
            BleScanConfig(
                allowDuplicates = false,
                filters = listOf(
                    BleScanFilter(
                        serviceUuid =
                            UUID.fromString(
                                "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
                            )
                    )
                )
            )

        lifecycleScope.launch {

            bleClient
                .scan(config)
                .catch { error ->

                    Log.e(
                        TAG,
                        "Scan failed",
                        error
                    )
                }
                .collect { result ->

                    Log.d(
                        TAG,
                        "Device: ${result.device.name}"
                    )

                    Log.d(
                        TAG,
                        "Address: ${result.device.address}"
                    )
                }
        }
    }

    companion object {
        private const val TAG =
            "RamaBleSample"
    }
}
```

---

# Design Philosophy

RamaBleManager follows a few important principles.

### The library should not control application UI

The library does not:

- Open permission dialogs
- Open Bluetooth settings
- Open location settings
- Show Toasts
- Show dialogs
- Decide what the user should click

Instead, it exposes state and lets the consuming application decide what UI to show.

### Scan and connection requirements are independent

Use:

```kotlin
bleClient.environment.checkScan()
```

before scan operations.

Use:

```kotlin
bleClient.environment.checkConnect()
```

before connection operations.

### Keep device-specific protocol logic outside the generic BLE layer

RamaBleManager should transport BLE data.

Your application or a higher-level device/protocol module should decide:

- Service UUIDs
- Characteristic UUIDs
- Command format
- Packet parsing
- Response parsing
- Device-specific retry behavior
- Business rules

This keeps the library reusable across different BLE hardware.

---

# Contributing

Contributions are welcome.

If you find a bug, want to improve the API, add tests, improve documentation, or add a general-purpose BLE capability, feel free to contribute.

## 1. Fork the repository

Fork:

```text
https://github.com/dinesh9936/RamaBleManager
```

Then clone your fork:

```bash
git clone https://github.com/<your-username>/RamaBleManager.git
cd RamaBleManager
```

## 2. Create a branch

Use a descriptive branch name.

For a feature:

```bash
git checkout -b feature/add-new-feature
```

For a bug fix:

```bash
git checkout -b fix/scan-timeout
```

For documentation:

```bash
git checkout -b docs/update-readme
```

## 3. Make your changes

Please try to keep changes:

- Focused
- Backward-compatible when possible
- Hardware-agnostic
- Covered by tests where practical
- Consistent with the existing public/internal package boundaries

Do not expose Android implementation details unnecessarily through the public API.

## 4. Build and test

Before submitting a pull request, run:

```bash
./gradlew :blecore:assembleDebug
```

Run unit tests:

```bash
./gradlew :blecore:testDebugUnitTest
```

You can also build the sample application:

```bash
./gradlew :app:assembleDebug
```

## 5. Commit your changes

Example:

```bash
git add .
git commit -m "Add separate BLE connection permission handling"
```

Use clear commit messages that explain what changed.

## 6. Push your branch

```bash
git push origin feature/add-new-feature
```

## 7. Open a Pull Request

Open a pull request against the main RamaBleManager repository.

In the pull request, please explain:

- What problem you are solving
- What changed
- Why the change is useful
- How it was tested
- Whether the public API changed

For BLE behavior changes, include Android version/device information when it is relevant.

---

## Contribution Guidelines

Please keep the core library generic.

Good contributions include:

- BLE scanning improvements
- Connection reliability improvements
- GATT operation improvements
- Better timeout handling
- Better cancellation handling
- Better error models
- Permission/environment handling
- Tests
- Documentation
- Sample application improvements

Device-specific business protocols generally belong outside the reusable core library.

For example, logic specifically for one meter, sensor, medical device, lock, or proprietary command protocol should normally live in a separate module built on top of RamaBleManager.

---

## Issues and Feature Requests

When reporting an issue, include as much of the following as possible:

- Android version
- Device manufacturer/model
- RamaBleManager version
- What operation failed
- Expected behavior
- Actual behavior
- Relevant logs
- Reproduction steps

Never include private keys, credentials, personal data, or other sensitive information in an issue.

---

## Project

Repository:

```text
https://github.com/dinesh9936/RamaBleManager
```

Built for reusable Android BLE development.
