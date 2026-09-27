package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedEventDao {

    @Query("SELECT * FROM processed_events ORDER BY createdAt DESC")
    fun getAllEvents(): Flow<List<ProcessedEvent>>

    @Query("SELECT * FROM processed_events ORDER BY createdAt DESC LIMIT 1")
    fun getLatestEvent(): Flow<ProcessedEvent?>

    @Query("SELECT COUNT(*) FROM processed_events WHERE status = 'SENT'")
    fun getSentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM processed_events WHERE status = 'IGNORED_GROUP'")
    fun getIgnoredGroupCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM processed_events WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT * FROM processed_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventById(eventId: String): ProcessedEvent?

    @Query("SELECT EXISTS(SELECT 1 FROM processed_events WHERE eventId = :eventId)")
    suspend fun exists(eventId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ProcessedEvent)

    @Update
    suspend fun updateEvent(event: ProcessedEvent)

    @Query("DELETE FROM processed_events")
    suspend fun clearAll()

    @Query("DELETE FROM processed_events WHERE createdAt < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)
}

@Dao
interface DiagnosticLogDao {

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<DiagnosticLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLog)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearLogs()
}
