package com.example.dedup

import com.example.data.local.ProcessedEventDao

/**
 * Gestor de deduplicación de eventos para evitar que una misma notificación
 * desencadene múltiples peticiones al webhook.
 */
class EventDeduplicator(private val processedEventDao: ProcessedEventDao) {

    /**
     * Comprueba si el evento ya fue procesado previamente.
     */
    suspend fun isDuplicate(eventId: String): Boolean {
        return processedEventDao.exists(eventId)
    }
}
