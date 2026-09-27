package com.example.webhook

import android.util.Log
import com.example.model.WebhookPayload
import com.example.model.WebhookResult
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para el envío de eventos al webhook con soporte de reintentos
 * y retroceso exponencial (exponential backoff).
 */
class WebhookClient {

    companion object {
        private const val TAG = "JBZ_WebhookClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // Controlamos los reintentos manualmente con backoff
        .build()

    /**
     * Construye el cuerpo JSON del webhook cumpliendo estrictamente la especificación requerida.
     */
    fun createJsonBody(payload: WebhookPayload): String {
        val json = JSONObject()
        json.put("source", "JuanBetterZapia")
        json.put("package", payload.package_name)
        json.put("chat_name", payload.chat_name)
        if (payload.phone != null) {
            json.put("phone", payload.phone)
        } else {
            json.put("phone", JSONObject.NULL)
        }
        json.put("message", payload.message)
        json.put("is_group", payload.is_group)
        json.put("timestamp", payload.timestamp)
        json.put("event_id", payload.event_id)
        return json.toString()
    }

    /**
     * Envía un intento simple de webhook.
     */
    suspend fun sendSingleAttempt(
        url: String,
        secret: String,
        payload: WebhookPayload
    ): WebhookResult {
        if (url.isBlank()) {
            return WebhookResult(
                success = false,
                errorMessage = "URL de webhook no configurada"
            )
        }

        val jsonString = createJsonBody(payload)
        val startTime = System.currentTimeMillis()

        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")

            if (secret.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $secret")
            }

            requestBuilder.post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
            val request = requestBuilder.build()

            // NOTA DE SEGURIDAD: Nunca registramos el secret ni los headers sensibles en Logcat
            Log.d(TAG, "Enviando POST a webhook [URL=${maskUrl(url)}, EventID=${payload.event_id}]")

            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val code = response.code
                val body = response.body?.string()?.take(500)

                if (response.isSuccessful) {
                    Log.i(TAG, "Webhook entregado exitosamente. HTTP $code en ${duration}ms")
                    WebhookResult(
                        success = true,
                        statusCode = code,
                        responseBody = body,
                        durationMs = duration
                    )
                } else {
                    Log.w(TAG, "Webhook respondió con error HTTP $code")
                    WebhookResult(
                        success = false,
                        statusCode = code,
                        responseBody = body,
                        errorMessage = "HTTP $code: ${response.message}",
                        durationMs = duration
                    )
                }
            }
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Fallo de conexión al enviar webhook: ${e.message}")
            WebhookResult(
                success = false,
                errorMessage = "Error de red: ${e.localizedMessage ?: e.javaClass.simpleName}",
                durationMs = duration
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Error inesperado al enviar webhook: ${e.message}")
            WebhookResult(
                success = false,
                errorMessage = "Excepción: ${e.localizedMessage ?: e.javaClass.simpleName}",
                durationMs = duration
            )
        }
    }

    /**
     * Envía con reintentos controlados y backoff exponencial (1s, 2s, 4s, ...).
     */
    suspend fun sendWithExponentialBackoff(
        url: String,
        secret: String,
        payload: WebhookPayload,
        maxRetries: Int = 3,
        onAttemptFailed: (attempt: Int, error: String) -> Unit = { _, _ -> }
    ): WebhookResult {
        var lastResult = WebhookResult(success = false, errorMessage = "Sin intentos")
        val effectiveRetries = maxRetries.coerceIn(1, 5)

        for (attempt in 1..effectiveRetries) {
            lastResult = sendSingleAttempt(url, secret, payload)
            if (lastResult.success) {
                return lastResult
            }

            onAttemptFailed(attempt, lastResult.errorMessage ?: "Fallo en intento $attempt")

            if (attempt < effectiveRetries) {
                // Backoff exponencial: 1000ms * 2^(attempt - 1) => 1s, 2s, 4s...
                val backoffDelay = 1000L * (1L shl (attempt - 1))
                Log.d(TAG, "Reintentando webhook en ${backoffDelay}ms (Intento $attempt de $effectiveRetries)")
                delay(backoffDelay)
            }
        }

        return lastResult
    }

    /**
     * Prueba de conectividad con el webhook enviando un evento de verificación.
     */
    suspend fun testConnection(url: String, secret: String): WebhookResult {
        val testPayload = WebhookPayload(
            source = "JuanBetterZapia",
            package_name = "com.whatsapp",
            chat_name = "Prueba de Diagnóstico",
            phone = null,
            message = "Mensaje de prueba de conexión desde JuanBetterZapia",
            is_group = false,
            timestamp = System.currentTimeMillis(),
            event_id = "test_ping_${System.currentTimeMillis()}"
        )
        return sendSingleAttempt(url, secret, testPayload)
    }

    private fun maskUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${uri.path}"
        } catch (e: Exception) {
            "url_configurada"
        }
    }
}
