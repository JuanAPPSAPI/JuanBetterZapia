package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.JuanBetterZapiaApp
import com.example.data.local.DiagnosticLog
import com.example.data.local.ProcessedEvent
import com.example.model.ExtractedWhatsAppNotification
import com.example.model.WebhookResult
import com.example.service.WhatsAppNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JuanBetterZapiaApp
    private val repo = app.repository
    private val prefs = app.preferences

    // Estado del agente y configuración
    private val _isAgentActive = MutableStateFlow(prefs.isAgentActive)
    val isAgentActive: StateFlow<Boolean> = _isAgentActive.asStateFlow()

    private val _webhookUrl = MutableStateFlow(prefs.webhookUrl)
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private val _webhookSecret = MutableStateFlow(prefs.webhookSecret)
    val webhookSecret: StateFlow<String> = _webhookSecret.asStateFlow()

    private val _processWhatsApp = MutableStateFlow(prefs.processWhatsApp)
    val processWhatsApp: StateFlow<Boolean> = _processWhatsApp.asStateFlow()

    private val _processWhatsAppBusiness = MutableStateFlow(prefs.processWhatsAppBusiness)
    val processWhatsAppBusiness: StateFlow<Boolean> = _processWhatsAppBusiness.asStateFlow()

    private val _ignoreGroups = MutableStateFlow(prefs.ignoreGroups)
    val ignoreGroups: StateFlow<Boolean> = _ignoreGroups.asStateFlow()

    private val _strictGroupCheck = MutableStateFlow(prefs.strictGroupCheck)
    val strictGroupCheck: StateFlow<Boolean> = _strictGroupCheck.asStateFlow()

    private val _ignoredContacts = MutableStateFlow(prefs.ignoredContacts)
    val ignoredContacts: StateFlow<Set<String>> = _ignoredContacts.asStateFlow()

    private val _maxRetries = MutableStateFlow(prefs.maxRetries)
    val maxRetries: StateFlow<Int> = _maxRetries.asStateFlow()

    private val _enableContactsPhoneLookup = MutableStateFlow(prefs.enableContactsPhoneLookup)
    val enableContactsPhoneLookup: StateFlow<Boolean> = _enableContactsPhoneLookup.asStateFlow()

    // Estado del sistema y permisos
    private val _isNotificationListenerGranted = MutableStateFlow(false)
    val isNotificationListenerGranted: StateFlow<Boolean> = _isNotificationListenerGranted.asStateFlow()

    val isListenerConnected: StateFlow<Boolean> = WhatsAppNotificationListenerService.isServiceConnected
    val lastDetectedPackage: StateFlow<String?> = WhatsAppNotificationListenerService.lastDetectedPackage

    // Métricas y datos de la base de datos
    val sentCount: StateFlow<Int> = repo.sentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val ignoredGroupCount: StateFlow<Int> = repo.ignoredGroupCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = repo.failedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val latestEvent: StateFlow<ProcessedEvent?> = repo.latestEvent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allEvents: StateFlow<List<ProcessedEvent>> = repo.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDiagnostics: StateFlow<List<DiagnosticLog>> = repo.recentDiagnostics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pruebas y feedback
    private val _isTestingWebhook = MutableStateFlow(false)
    val isTestingWebhook: StateFlow<Boolean> = _isTestingWebhook.asStateFlow()

    private val _testWebhookResult = MutableStateFlow<WebhookResult?>(null)
    val testWebhookResult: StateFlow<WebhookResult?> = _testWebhookResult.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    init {
        checkNotificationListenerPermission()
    }

    fun checkNotificationListenerPermission() {
        val context = getApplication<Application>()
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isGranted = flat?.contains(packageName) == true ||
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        _isNotificationListenerGranted.value = isGranted
    }

    fun setAgentActive(active: Boolean) {
        prefs.isAgentActive = active
        _isAgentActive.value = active
    }

    fun setWebhookUrl(url: String) {
        prefs.webhookUrl = url
        _webhookUrl.value = url
    }

    fun setWebhookSecret(secret: String) {
        prefs.webhookSecret = secret
        _webhookSecret.value = secret
    }

    fun setProcessWhatsApp(enabled: Boolean) {
        prefs.processWhatsApp = enabled
        _processWhatsApp.value = enabled
    }

    fun setProcessWhatsAppBusiness(enabled: Boolean) {
        prefs.processWhatsAppBusiness = enabled
        _processWhatsAppBusiness.value = enabled
    }

    fun setIgnoreGroups(ignore: Boolean) {
        prefs.ignoreGroups = ignore
        _ignoreGroups.value = ignore
    }

    fun setStrictGroupCheck(strict: Boolean) {
        prefs.strictGroupCheck = strict
        _strictGroupCheck.value = strict
    }

    fun setMaxRetries(retries: Int) {
        prefs.maxRetries = retries
        _maxRetries.value = retries
    }

    fun setEnableContactsPhoneLookup(enabled: Boolean) {
        prefs.enableContactsPhoneLookup = enabled
        _enableContactsPhoneLookup.value = enabled
    }

    fun addIgnoredContact(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            val updated = _ignoredContacts.value + trimmed
            prefs.ignoredContacts = updated
            _ignoredContacts.value = updated
        }
    }

    fun removeIgnoredContact(name: String) {
        val updated = _ignoredContacts.value - name
        prefs.ignoredContacts = updated
        _ignoredContacts.value = updated
    }

    fun clearFeedback() {
        _actionFeedback.value = null
        _testWebhookResult.value = null
    }

    fun testWebhookConnection() {
        viewModelScope.launch {
            _isTestingWebhook.value = true
            _testWebhookResult.value = null
            try {
                val result = repo.testWebhookConnection()
                _testWebhookResult.value = result
            } catch (e: Exception) {
                _testWebhookResult.value = WebhookResult(
                    success = false,
                    errorMessage = e.localizedMessage ?: "Error desconocido"
                )
            } finally {
                _isTestingWebhook.value = false
            }
        }
    }

    fun retryEvent(event: ProcessedEvent) {
        viewModelScope.launch {
            _actionFeedback.value = "Reintentando envío para '${event.chatName}'..."
            val result = repo.retryEvent(event)
            _actionFeedback.value = if (result.success) {
                "¡Reenvío exitoso para '${event.chatName}'!"
            } else {
                "Fallo al reintentar: ${result.errorMessage}"
            }
        }
    }

    fun sendManualTestEvent() {
        viewModelScope.launch {
            _actionFeedback.value = "Enviando evento de prueba simulado..."
            val mockNotification = ExtractedWhatsAppNotification(
                packageName = "com.whatsapp",
                chatName = "Juan Pérez",
                messageText = "Hola, este es un mensaje de prueba desde JuanBetterZapia.",
                phone = "+5491123456789",
                isGroup = false,
                groupDetectionReason = "Chat individual de prueba",
                timestamp = System.currentTimeMillis(),
                eventId = "test_event_${System.currentTimeMillis()}"
            )
            val result = repo.sendNotificationToWebhook(mockNotification)
            _actionFeedback.value = if (result.success) {
                "¡Evento de prueba enviado y recibido por el Webhook (HTTP ${result.statusCode})!"
            } else {
                "Fallo al enviar al Webhook: ${result.errorMessage}"
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearHistory()
            _actionFeedback.value = "Historial de eventos vaciado."
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch {
            repo.clearDiagnostics()
            _actionFeedback.value = "Registros de diagnóstico eliminados."
        }
    }
}
