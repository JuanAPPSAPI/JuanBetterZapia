package com.example.model

/**
 * Representa los datos extraídos de una notificación de WhatsApp.
 */
data class ExtractedWhatsAppNotification(
    val packageName: String,
    val chatName: String,
    val messageText: String,
    val phone: String? = null,
    val isGroup: Boolean = false,
    val groupDetectionReason: String = "Individual chat",
    val timestamp: Long = System.currentTimeMillis(),
    val eventId: String,
    val rawTitle: String? = null,
    val conversationTitle: String? = null,
    val isGroupConversationExtra: Boolean? = null,
    val rawExtrasSummary: String = ""
)

/**
 * Payload exacto requerido para el webhook.
 */
data class WebhookPayload(
    val source: String = "JuanBetterZapia",
    val package_name: String, // mapped to "package" in JSON
    val chat_name: String,
    val phone: String?,
    val message: String,
    val is_group: Boolean,
    val timestamp: Long,
    val event_id: String
)

/**
 * Resultado del intento de envío al webhook.
 */
data class WebhookResult(
    val success: Boolean,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val durationMs: Long = 0
)
