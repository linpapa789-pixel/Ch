package com.example.data

enum class LogType {
    INFO,
    OCR,
    SUCCESS,
    WARNING,
    ERROR
}

data class LogEntry(
    val message: String,
    val type: LogType,
    val timestamp: Long = System.currentTimeMillis()
)

data class ServerResponseLog(
    val timestamp: Long = System.currentTimeMillis(),
    val workerId: Int,
    val token: String,
    val status: TokenStatus,
    val httpCode: Int,
    val rawResponse: String
)
