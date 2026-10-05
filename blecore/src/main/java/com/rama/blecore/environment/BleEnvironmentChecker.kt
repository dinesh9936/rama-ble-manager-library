package com.rama.blecore.environment

interface BleEnvironmentChecker {

    fun checkScan(): BleEnvironmentState

    fun checkConnect(): BleEnvironmentState
}