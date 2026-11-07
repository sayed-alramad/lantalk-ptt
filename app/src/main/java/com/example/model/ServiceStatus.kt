package com.example.model

/**
 * Represents the real-time operational status of the Push-to-Talk engine.
 */
enum class ServiceStatus(val label: String) {
    IDLE("Idle"),
    LISTENING("Listening"),
    TRANSMITTING("Transmitting"),
    RECEIVING("Receiving");

    val isTransmitting: Boolean
        get() = this == TRANSMITTING

    val isReceiving: Boolean
        get() = this == RECEIVING

    val isActive: Boolean
        get() = this != IDLE
}
