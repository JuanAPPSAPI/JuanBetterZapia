package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.WebhookPayload
import com.example.webhook.WebhookClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches JuanBetterZapia`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JuanBetterZapia", appName)
    }

    @Test
    fun `webhook client formats json payload properly with null phone`() {
        val client = WebhookClient()
        val payload = WebhookPayload(
            source = "JuanBetterZapia",
            package_name = "com.whatsapp",
            chat_name = "Carlos Gomez",
            phone = null,
            message = "Hola Juan",
            is_group = false,
            timestamp = 1700000000000L,
            event_id = "test_event_id_123"
        )

        val json = client.createJsonBody(payload)
        val obj = JSONObject(json)

        assertEquals("JuanBetterZapia", obj.getString("source"))
        assertEquals("com.whatsapp", obj.getString("package"))
        assertEquals("Carlos Gomez", obj.getString("chat_name"))
        assertTrue(obj.isNull("phone"))
        assertEquals("Hola Juan", obj.getString("message"))
        assertFalse(obj.getBoolean("is_group"))
        assertEquals(1700000000000L, obj.getLong("timestamp"))
        assertEquals("test_event_id_123", obj.getString("event_id"))
    }

    @Test
    fun `webhook client formats json payload properly with non-null phone`() {
        val client = WebhookClient()
        val payload = WebhookPayload(
            source = "JuanBetterZapia",
            package_name = "com.whatsapp",
            chat_name = "+34612345678",
            phone = "+34612345678",
            message = "¿Qué tal?",
            is_group = false,
            timestamp = 1700000000000L,
            event_id = "test_event_id_456"
        )

        val json = client.createJsonBody(payload)
        val obj = JSONObject(json)

        assertEquals("+34612345678", obj.getString("phone"))
        assertEquals("com.whatsapp", obj.getString("package"))
    }
}
