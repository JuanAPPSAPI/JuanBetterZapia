package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticLog
import com.example.data.local.ProcessedEvent
import com.example.data.security.SecurePreferences
import com.example.model.ExtractedWhatsAppNotification
import com.example.model.WebhookPayload
import com.example.model.WebhookResult
import com.example.webhook.WebhookClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repositorio central que abstrae el acceso a Room, Preferencias Seguras y Webhook.
 */
class AppRepository(
    private val database: AppDatabase,
    val preferences: SecurePreferences,
    private val webhookClient: WebhookClient
) {
    private val eventDao = database.processedEventDao()
    private val diagnosticDao = database.diagnosticLogDao()

    val allEvents: Flow<List<ProcessedEvent>> = eventDao.getAllEvents()
    val latestEvent: Flow<ProcessedEvent?> = eventDao.getLatestEvent()
    val sentCount: Flow<Int> = eventDao.getSentCount()
    val ignoredGroupCount: Flow<Int> = eventDao.getIgnoredGroupCount()
    val failedCount: Flow<Int> = eventDao.getFailedCount()
    val recentDiagnostics: Flow<List<DiagnosticLog>> = diagnosticDao.getRecentLogs()

    suspend fun isEventDuplicate(eventId: String): Boolean = withContext(Dispatchers.IO) {
        eventDao.exists(eventId)
    }

    suspend fun saveEvent(event: ProcessedEvent) = withContext(Dispatchers.IO) {
        eventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: ProcessedEvent) = withContext(Dispatchers.IO) {
        eventDao.updateEvent(event)
    }

    suspend fun recordDiagnostic(log: DiagnosticLog) = withContext(Dispatchers.IO) {
        diagnosticDao.insertLog(log)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        eventDao.clearAll()
    }

    suspend fun clearDiagnostics() = withContext(Dispatchers.IO) {
        diagnosticDao.clearLogs()
    }

    suspend fun testWebhookConnection(): WebhookResult = withContext(Dispatchers.IO) {
        webhookClient.testConnection(
            url = preferences.webhookUrl,
            secret = preferences.webhookSecret
        )
    }

    suspend fun sendNotificationToWebhook(
        notification: ExtractedWhatsAppNotification
    ): WebhookResult = withContext(Dispatchers.IO) {
        val payload = WebhookPayload(
            source = "JuanBetterZapia",
            package_name = notification.packageName,
            chat_name = notification.chatName,
            phone = notification.phone,
            message = notification.messageText,
            is_group = notification.isGroup,
            timestamp = notification.timestamp,
            event_id = notification.eventId
        )

        val result = webhookClient.sendWithExponentialBackoff(
            url = preferences.webhookUrl,
            secret = preferences.webhookSecret,
            payload = payload,
            maxRetries = preferences.maxRetries,
            onAttemptFailed = { attempt, error ->
                // Actualizar estado en DB durante reintentos si es necesario
            }
        )

        // Registrar o actualizar estado en la base de datos
        val event = ProcessedEvent(
            eventId = notification.eventId,
            packageName = notification.packageName,
            chatName = notification.chatName,
            phone = notification.phone,
            messageText = notification.messageText,
            isGroup = notification.isGroup,
            timestamp = notification.timestamp,
            createdAt = System.currentTimeMillis(),
            status = if (result.success) "SENT" else "FAILED",
            httpResponseCode = result.statusCode,
            errorMessage = if (result.success) null else (result.errorMessage ?: result.responseBody),
            retryCount = preferences.maxRetries
        )
        eventDao.insertEvent(event)

        result
    }

    suspend fun retryEvent(event: ProcessedEvent): WebhookResult = withContext(Dispatchers.IO) {
        val payload = WebhookPayload(
            source = "JuanBetterZapia",
            package_name = event.packageName,
            chat_name = event.chatName,
            phone = event.phone,
            message = event.messageText,
            is_group = event.isGroup,
            timestamp = event.timestamp,
            event_id = event.eventId
        )

        val result = webhookClient.sendWithExponentialBackoff(
            url = preferences.webhookUrl,
            secret = preferences.webhookSecret,
            payload = payload,
            maxRetries = preferences.maxRetries
        )

        val updated = event.copy(
            status = if (result.success) "SENT" else "FAILED",
            httpResponseCode = result.statusCode,
            errorMessage = if (result.success) null else (result.errorMessage ?: result.responseBody),
            retryCount = event.retryCount + 1
        )
        eventDao.updateEvent(updated)
        result
    }
}
