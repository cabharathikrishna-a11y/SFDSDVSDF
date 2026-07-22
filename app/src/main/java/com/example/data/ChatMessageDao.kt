package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllCachedMessagesFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun getAllCachedMessages(): List<ChatMessage>

    @Query("SELECT * FROM (SELECT * FROM chat_messages ORDER BY id DESC LIMIT :limit) ORDER BY id ASC")
    suspend fun getRecentCachedMessages(limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE created_at >= :sinceIsoDate ORDER BY id ASC")
    suspend fun getMessagesSince(sinceIsoDate: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("UPDATE chat_messages SET status = 'READ' WHERE sender_id != :currentUserId AND status != 'READ'")
    suspend fun markIncomingMessagesAsRead(currentUserId: String)

    @Query("UPDATE chat_messages SET status = 'READ' WHERE id IN (:ids)")
    suspend fun markMessagesAsRead(ids: List<Long>)

    @Query("UPDATE chat_messages SET reactions = :reactions WHERE id = :id")
    suspend fun updateMessageReactions(id: Long, reactions: String)

    @Query("UPDATE chat_messages SET is_pinned = :isPinned WHERE id = :id")
    suspend fun updateMessagePinned(id: Long, isPinned: Boolean)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_messages WHERE created_at < :beforeIsoDate")
    suspend fun deleteMessagesOlderThan(beforeIsoDate: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
