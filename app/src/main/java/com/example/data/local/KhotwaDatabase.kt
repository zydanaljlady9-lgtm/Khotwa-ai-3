package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface KhotwaDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, primaryIntent = :intent, messageCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationMeta(
        id: Long,
        title: String,
        intent: String,
        count: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET stepsJson = :stepsJson WHERE id = :messageId")
    suspend fun updateMessageSteps(messageId: Long, stepsJson: String)

    @Query("SELECT * FROM search_cache WHERE queryKey = :queryKey LIMIT 1")
    suspend fun getCachedSearch(queryKey: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCachedSearch(entry: SearchCacheEntity)

    @Query("DELETE FROM search_cache")
    suspend fun clearSearchCache()
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        SearchCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KhotwaDatabase : RoomDatabase() {
    abstract fun khotwaDao(): KhotwaDao

    companion object {
        @Volatile
        private var INSTANCE: KhotwaDatabase? = null

        fun getInstance(context: Context): KhotwaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhotwaDatabase::class.java,
                    "khotwa_ai.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
