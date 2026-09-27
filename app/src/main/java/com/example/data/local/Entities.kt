package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de un evento de mensaje procesado para deduplicación e historial.
 */
@Entity(tableName = "processed_events")
data class ProcessedEvent(
    @PrimaryKey
    val eventId: String,
    val packageName: String,
    val chatName: String,
    val phone: String?,
    val messageText: String,
    val isGroup: Boolean,
    val timestamp: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String, // SENT, FAILED, IGNORED_GROUP, IGNORED_CONTACT, RETRYING, AGENT_DISABLED
    val httpResponseCode: Int? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

/**
 * Registro de diagnóstico para inspeccionar los campos reales de una notificación de WhatsApp.
 */
@Entity(tableName = "diagnostic_logs")
data class DiagnosticLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chatName: String?,
    val title: String?,
    val text: String?,
    val subText: String?,
    val conversationTitle: String?,
    val isGroupDetected: Boolean,
    val groupDetectionReason: String,
    val rawExtrasJson: String,
    val phoneResolved: String?
)
