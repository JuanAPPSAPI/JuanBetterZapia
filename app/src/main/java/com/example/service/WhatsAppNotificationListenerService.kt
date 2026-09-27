package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.JuanBetterZapiaApp
import com.example.data.local.DiagnosticLog
import com.example.data.local.ProcessedEvent
import com.example.parser.ParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Servicio del sistema Android para escuchar notificaciones de WhatsApp.
 * Se ejecuta bajo demanda del sistema operativo sin necesidad de un foreground service constante.
 */
class WhatsAppNotificationListenerService : NotificationListenerService() {

    companion object {
        const val TAG = "JBZ_NotificationListener"

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastDetectedPackage = MutableStateFlow<String?>(null)
        val lastDetectedPackage: StateFlow<String?> = _lastDetectedPackage.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        Log.i(TAG, "NotificationListenerService CONECTADO por el sistema Android.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
        Log.w(TAG, "NotificationListenerService DESCONECTADO.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val app = applicationContext as? JuanBetterZapiaApp ?: JuanBetterZapiaApp.instance
        val parser = app.notificationParser
        val repo = app.repository
        val prefs = app.preferences

        val packageName = sbn.packageName ?: return

        // Filtrado rápido de paquete WhatsApp
        if (!parser.isWhatsAppPackage(packageName)) {
            return
        }

        _lastDetectedPackage.value = packageName

        // Si el agente está desactivado por el usuario, no procesar
        if (!prefs.isAgentActive) {
            Log.d(TAG, "Notificación recibida pero el agente está en pausa.")
            return
        }

        serviceScope.launch {
            try {
                // 1. Parsear y clasificar la notificación
                when (val result = parser.parse(sbn)) {
                    is ParseResult.Ignored -> {
                        Log.d(TAG, "Notificación WhatsApp descartada: ${result.reason}")
                    }
                    is ParseResult.Success -> {
                        val notificationData = result.notification

                        // Registrar diagnóstico seguro para visualización en pantalla de diagnóstico
                        repo.recordDiagnostic(
                            DiagnosticLog(
                                packageName = notificationData.packageName,
                                timestamp = notificationData.timestamp,
                                chatName = notificationData.chatName,
                                title = notificationData.rawTitle,
                                text = notificationData.messageText,
                                subText = notificationData.conversationTitle,
                                conversationTitle = notificationData.conversationTitle,
                                isGroupDetected = notificationData.isGroup,
                                groupDetectionReason = notificationData.groupDetectionReason,
                                rawExtrasJson = notificationData.rawExtrasSummary,
                                phoneResolved = notificationData.phone
                            )
                        )

                        // 2. Detección y filtrado de grupos
                        if (notificationData.isGroup) {
                            if (prefs.ignoreGroups) {
                                Log.i(
                                    TAG,
                                    "GRUPO IGNORADO COMPLETAMENTE: Chat='${notificationData.chatName}', Razón='${notificationData.groupDetectionReason}'"
                                )
                                // Guardar en historial como IGNORED_GROUP para registro y auditoría del usuario
                                repo.saveEvent(
                                    ProcessedEvent(
                                        eventId = notificationData.eventId,
                                        packageName = notificationData.packageName,
                                        chatName = notificationData.chatName,
                                        phone = null,
                                        messageText = "[Grupo Ignorado] ${notificationData.messageText.take(60)}...",
                                        isGroup = true,
                                        timestamp = notificationData.timestamp,
                                        createdAt = System.currentTimeMillis(),
                                        status = "IGNORED_GROUP",
                                        httpResponseCode = null,
                                        errorMessage = notificationData.groupDetectionReason,
                                        retryCount = 0
                                    )
                                )
                                return@launch
                            }
                        }

                        // 3. Deduplicación determinista
                        if (repo.isEventDuplicate(notificationData.eventId)) {
                            Log.d(TAG, "Notificación duplicada detectada (eventId: ${notificationData.eventId}). Omitiendo.")
                            return@launch
                        }

                        // 4. Conversación Individual confirmada -> Preparar y enviar al Webhook
                        Log.i(
                            TAG,
                            "MENSAJE INDIVIDUAL CONFIRMADO -> Enviar a Webhook: Chat='${notificationData.chatName}', Phone='${notificationData.phone ?: "null"}'"
                        )
                        val webhookResult = repo.sendNotificationToWebhook(notificationData)
                        if (webhookResult.success) {
                            Log.i(TAG, "Webhook entregado exitosamente para ${notificationData.chatName}")
                        } else {
                            Log.e(TAG, "Fallo al entregar webhook para ${notificationData.chatName}: ${webhookResult.errorMessage}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}", e)
            }
        }
    }
}
