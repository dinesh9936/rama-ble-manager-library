package com.rama.blecore.error


class BleException(
    val error: BleError
) : Exception(
    error.message,
    error.cause
)