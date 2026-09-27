package com.example.parser

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.security.SecurePreferences
import com.example.model.ExtractedWhatsAppNotification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Parser especializado y robusto para notificaciones de WhatsApp y WhatsApp Business.
 * Extrae limpiamente la información respetando las APIs oficiales de Android.
 */
class WhatsAppNotificationParser(
    private val securePreferences: SecurePreferences,
    private val phoneResolver: PhoneResolver
) {

    companion object {
        const val TAG = "WhatsAppParser"
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

        private val SYSTEM_SERVICE_TEXTS = setOf(
            "checking for new messages",
            "comprobando si hay mensajes nuevos",
            "whatsapp web",
            "whatsapp web activo",
            "copia de seguridad en curso",
            "backup in progress",
            "llamada entrante",
            "incoming call",
            "llamada perdida",
            "missed call"
        )
    }

    /**
     * Comprueba si el paquete de la notificación corresponde a WhatsApp según la configuración.
     */
    fun isWhatsAppPackage(packageName: String): Boolean {
        if (packageName == PKG_WHATSAPP && securePreferences.processWhatsApp) return true
        if (packageName == PKG_WHATSAPP_BUSINESS && securePreferences.processWhatsAppBusiness) return true
        return false
    }

    /**
     * Procesa la notificación y devuelve ExtractedWhatsAppNotification si es válida, o null si debe ignorarse.
     */
    fun parse(sbn: StatusBarNotification): ParseResult {
        val packageName = sbn.packageName
        if (!isWhatsAppPackage(packageName)) {
            return ParseResult.Ignored("No es un paquete de WhatsApp habilitado ($packageName)")
        }

        val notification = sbn.notification ?: return ParseResult.Ignored("Notificación vacía")

        // 1. Descartar notificaciones de resumen (group summary)
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return ParseResult.Ignored("Notificación de resumen general (FLAG_GROUP_SUMMARY)")
        }

        // Descartar notificaciones de llamada activa o en curso
        if (notification.category == Notification.CATEGORY_CALL) {
            return ParseResult.Ignored("Notificación de llamada de WhatsApp")
        }

        val extras = notification.extras ?: Bundle()

        // 2. Extraer Títulos y Conversación
        val title = extractTitle(extras)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        // 3. Extraer Mensaje
        val messageText = extractMessageText(extras)
        if (messageText.isBlank()) {
            return ParseResult.Ignored("Mensaje vacío o sin texto")
        }

        // Descartar mensajes del sistema como "Comprobando mensajes nuevos" o WhatsApp Web
        val lowerMessage = messageText.lowercase()
        val lowerTitle = title.lowercase()
        if (SYSTEM_SERVICE_TEXTS.any { lowerMessage.contains(it) || lowerTitle.contains(it) }) {
            return ParseResult.Ignored("Notificación de servicio del sistema WhatsApp")
        }

        // 4. Detección Robusta de Grupos
        val groupCheck = evaluateIsGroup(extras, title, conversationTitle, subText)
        val isGroup = groupCheck.isGroup

        // 5. Determinar Chat Name
        val chatName = determineChatName(title, conversationTitle, isGroup)
        if (chatName.isBlank() || chatName.equals("WhatsApp", ignoreCase = true)) {
            return ParseResult.Ignored("Nombre de chat inválido o genérico: $chatName")
        }

        // 6. Verificar si está en la lista de ignorados
        if (securePreferences.ignoredContacts.any { it.equals(chatName, ignoreCase = true) }) {
            return ParseResult.Ignored("Contacto ignorado por configuración ($chatName)")
        }

        // 7. Resolución legítima del número de teléfono
        val phoneResult = if (!isGroup) {
            phoneResolver.resolvePhone(
                chatName = chatName,
                extras = extras,
                allowContactsLookup = securePreferences.enableContactsPhoneLookup
            )
        } else {
            PhoneResolutionResult(null, "group_chat", "Conversación de grupo")
        }

        // 8. Timestamp y Event ID determinista
        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val eventId = generateEventId(packageName, chatName, messageText, postTime)

        // Resumen seguro de extras para diagnóstico
        val rawExtrasSummary = dumpExtrasSafely(extras)

        val extracted = ExtractedWhatsAppNotification(
            packageName = packageName,
            chatName = chatName,
            messageText = messageText,
            phone = phoneResult.phone,
            isGroup = isGroup,
            groupDetectionReason = groupCheck.reason,
            timestamp = postTime,
            eventId = eventId,
            rawTitle = title,
            conversationTitle = conversationTitle,
            isGroupConversationExtra = groupCheck.isGroupExtra,
            rawExtrasSummary = rawExtrasSummary
        )

        return ParseResult.Success(extracted)
    }

    /**
     * Detección multi-criterio de grupos utilizando las APIs oficiales de Android.
     */
    private fun evaluateIsGroup(
        extras: Bundle,
        title: String,
        conversationTitle: String?,
        subText: String?
    ): GroupEvaluationResult {
        // Criterio 1: Extra oficial de Android Notification.EXTRA_IS_GROUP_CONVERSATION
        var isGroupExtra: Boolean? = null
        if (extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
            val boolVal = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            isGroupExtra = boolVal
            if (boolVal) {
                return GroupEvaluationResult(
                    isGroup = true,
                    reason = "android.isGroupConversation = true (API oficial MessagingStyle)",
                    isGroupExtra = true
                )
            }
        }

        // Criterio 2: EXTRA_CONVERSATION_TITLE no nulo y distinto del título individual
        if (!conversationTitle.isNullOrBlank()) {
            if (!conversationTitle.equals(title, ignoreCase = true)) {
                return GroupEvaluationResult(
                    isGroup = true,
                    reason = "EXTRA_CONVERSATION_TITLE presente ('$conversationTitle') diferente de remitente ('$title')",
                    isGroupExtra = isGroupExtra
                )
            }
            // Si conversationTitle está presente pero es idéntico a title
            if (securePreferences.strictGroupCheck) {
                return GroupEvaluationResult(
                    isGroup = true,
                    reason = "EXTRA_CONVERSATION_TITLE presente en modo estricto",
                    isGroupExtra = isGroupExtra
                )
            }
        }

        // Criterio 3: Verificación de subTexto típico de grupo
        if (!subText.isNullOrBlank()) {
            if (subText.contains(":") || (!title.isBlank() && !subText.equals(title, ignoreCase = true))) {
                // En ciertas versiones de WhatsApp subtext indica el remitente o el grupo
                if (securePreferences.strictGroupCheck) {
                    return GroupEvaluationResult(
                        isGroup = true,
                        reason = "SubTexto sospechoso de grupo ('$subText')",
                        isGroupExtra = isGroupExtra
                    )
                }
            }
        }

        // Si el booleano explícito fue false, es un chat 1 a 1 confirmado
        if (isGroupExtra == false) {
            return GroupEvaluationResult(
                isGroup = false,
                reason = "android.isGroupConversation = false confirmado",
                isGroupExtra = false
            )
        }

        // Por defecto, chat individual si no hay indicadores de grupo
        return GroupEvaluationResult(
            isGroup = false,
            reason = "Sin indicadores de grupo detectados",
            isGroupExtra = isGroupExtra
        )
    }

    private fun extractTitle(extras: Bundle): String {
        val titleSeq = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        return titleSeq?.toString()?.trim() ?: ""
    }

    private fun extractMessageText(extras: Bundle): String {
        // En MessagingStyle, revisar si hay array de mensajes
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val lastMessage = messages.lastOrNull()
            if (lastMessage is Bundle) {
                val text = lastMessage.getCharSequence("text")?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    return text
                }
            }
        }

        // Si no está en mensajes, revisar BIG_TEXT o TEXT estándar
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!bigText.isNullOrEmpty()) return bigText

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text

        return ""
    }

    private fun determineChatName(title: String, conversationTitle: String?, isGroup: Boolean): String {
        return if (isGroup && !conversationTitle.isNullOrBlank()) {
            conversationTitle
        } else if (title.isNotBlank()) {
            title
        } else {
            conversationTitle ?: ""
        }
    }

    /**
     * Genera un event_id determinista usando SHA-256.
     * Ventana de tiempo aproximada a 5 segundos para agrupar ráfagas repetidas de la misma notificación.
     */
    fun generateEventId(
        packageName: String,
        chatName: String,
        message: String,
        timestamp: Long
    ): String {
        // Redondeo de timestamp a ventana de 5 segundos para evitar duplicados si la misma notificación se actualiza
        val timeWindow = timestamp / 5000L
        val raw = "$packageName|$chatName|$message|$timeWindow"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Volcado seguro de extras para diagnóstico en UI y Logcat sin exponer secretos ni datos privados.
     */
    fun dumpExtrasSafely(extras: Bundle): String {
        val json = JSONObject()
        for (key in extras.keySet()) {
            val value = extras.get(key)
            when (value) {
                null -> json.put(key, "null")
                is String, is CharSequence, is Number, is Boolean -> json.put(key, value.toString())
                is ArrayList<*> -> json.put(key, JSONArray(value))
                is Array<*> -> json.put(key, "[Array length=${value.size}]")
                is Bundle -> json.put(key, "[Bundle with ${value.size()} keys]")
                else -> json.put(key, "[${value.javaClass.simpleName}]")
            }
        }
        return json.toString(2)
    }
}

sealed class ParseResult {
    data class Success(val notification: ExtractedWhatsAppNotification) : ParseResult()
    data class Ignored(val reason: String) : ParseResult()
}

private data class GroupEvaluationResult(
    val isGroup: Boolean,
    val reason: String,
    val isGroupExtra: Boolean?
)
