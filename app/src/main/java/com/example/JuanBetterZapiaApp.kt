package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import com.example.data.security.SecurePreferences
import com.example.dedup.EventDeduplicator
import com.example.parser.PhoneResolver
import com.example.parser.WhatsAppNotificationParser
import com.example.webhook.WebhookClient

class JuanBetterZapiaApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferences: SecurePreferences
        private set
    lateinit var webhookClient: WebhookClient
        private set
    lateinit var repository: AppRepository
        private set
    lateinit var phoneResolver: PhoneResolver
        private set
    lateinit var notificationParser: WhatsAppNotificationParser
        private set
    lateinit var deduplicator: EventDeduplicator
        private set

    companion object {
        lateinit var instance: JuanBetterZapiaApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferences = SecurePreferences(this)
        webhookClient = WebhookClient()
        repository = AppRepository(database, preferences, webhookClient)
        phoneResolver = PhoneResolver(this)
        notificationParser = WhatsAppNotificationParser(preferences, phoneResolver)
        deduplicator = EventDeduplicator(database.processedEventDao())
    }
}
