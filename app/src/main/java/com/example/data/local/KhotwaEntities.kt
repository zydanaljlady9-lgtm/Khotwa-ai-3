package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val primaryIntent: String,
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val isUser: Boolean,
    val content: String,
    val intentName: String,
    val factsJson: String = "[]",
    val inferencesJson: String = "[]",
    val uncertaintiesJson: String = "[]",
    val stepsJson: String = "[]",
    val citationsJson: String = "[]",
    val conflictSummary: String? = null,
    val modelUsed: String = "",
    val ttftMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val searchDurationMs: Long = 0L,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val queryKey: String,
    val queryText: String,
    val citationsJson: String,
    val conflictSummary: String?,
    val createdAt: Long = System.currentTimeMillis()
)
