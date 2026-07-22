package com.example.data

import com.example.model.ChatMessage
import com.example.model.UserPresence
import com.example.util.SupabaseManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class ChatRepository(
    private val chatMessageDao: ChatMessageDao? = try {
        AppDatabase.getInstance(com.example.MainApplication.instance).chatMessageDao()
    } catch (e: Exception) {
        null
    }
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCachedMessages(): List<ChatMessage> {
        return chatMessageDao?.getAllCachedMessages() ?: emptyList()
    }

    suspend fun getRecentCachedMessages(limit: Int): List<ChatMessage> {
        return chatMessageDao?.getRecentCachedMessages(limit) ?: emptyList()
    }

    suspend fun fetchAndCacheRecentMessages(limit: Int): List<ChatMessage> {
        return try {
            val remoteMessages = SupabaseManager.client.from("messages")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ChatMessage>()
                .reversed()

            if (remoteMessages.isNotEmpty()) {
                chatMessageDao?.insertMessages(remoteMessages)
            }
            val cached = chatMessageDao?.getRecentCachedMessages(limit)
            if (!cached.isNullOrEmpty()) cached else remoteMessages
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback query without limit/order filter if needed
            try {
                val fallbackRemote = SupabaseManager.client.from("messages")
                    .select {
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<ChatMessage>()
                if (fallbackRemote.isNotEmpty()) {
                    chatMessageDao?.insertMessages(fallbackRemote)
                }
                chatMessageDao?.getRecentCachedMessages(limit) ?: fallbackRemote
            } catch (e2: Exception) {
                chatMessageDao?.getRecentCachedMessages(limit) ?: emptyList()
            }
        }
    }

    suspend fun fetchAndCacheLast12MonthsMessages(): List<ChatMessage> {
        return fetchAndCacheRecentMessages(100)
    }

    suspend fun getInitialMessages(): List<ChatMessage> {
        return fetchAndCacheLast12MonthsMessages()
    }

    suspend fun sendMessage(
        text: String,
        senderId: String,
        replyToId: Long? = null,
        replyToText: String? = null,
        replyToSender: String? = null
    ): ChatMessage {
        val message = ChatMessage(
            senderId = senderId,
            text = text,
            status = "SENT",
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSender = replyToSender
        )
        val sent = try {
            SupabaseManager.client.from("messages")
                .insert(message) {
                    select()
                }
                .decodeSingle<ChatMessage>()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if Supabase schema is missing reply_to columns
            try {
                val fallbackMsg = ChatMessage(senderId = senderId, text = text, status = "SENT")
                SupabaseManager.client.from("messages")
                    .insert(fallbackMsg) { select() }
                    .decodeSingle<ChatMessage>()
                    .copy(replyToId = replyToId, replyToText = replyToText, replyToSender = replyToSender)
            } catch (e2: Exception) {
                message
            }
        }

        try {
            chatMessageDao?.insertMessage(sent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sent
    }

    suspend fun editMessage(messageId: Long, newText: String): ChatMessage? {
        return try {
            val updated = SupabaseManager.client.from("messages")
                .update({
                    set("text", newText)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                    select()
                }
                .decodeSingleOrNull<ChatMessage>()

            if (updated != null) {
                try {
                    chatMessageDao?.insertMessage(updated)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            updated
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateMessageReaction(messageId: Long, reactions: String) {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("reactions", reactions)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.updateMessageReactions(messageId, reactions)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.updateMessageReactions(messageId, reactions)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun pinMessage(messageId: Long, isPinned: Boolean) {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("is_pinned", isPinned)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.updateMessagePinned(messageId, isPinned)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.updateMessagePinned(messageId, isPinned)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun markIncomingMessagesAsRead(currentUserId: String) {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("status", "READ")
                }) {
                    filter {
                        neq("sender_id", currentUserId)
                        neq("status", "READ")
                    }
                }
            chatMessageDao?.markIncomingMessagesAsRead(currentUserId)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.markIncomingMessagesAsRead(currentUserId)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun markMessagesAsRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        try {
            messageIds.forEach { id ->
                SupabaseManager.client.from("messages")
                    .update({
                        set("status", "READ")
                    }) {
                        filter {
                            eq("id", id)
                        }
                    }
            }
            chatMessageDao?.markMessagesAsRead(messageIds)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.markMessagesAsRead(messageIds)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        try {
            SupabaseManager.client.from("messages")
                .delete {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.deleteMessageById(messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.deleteMessageById(messageId)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun subscribeToNewMessages(): Flow<ChatMessage> = callbackFlow {
        val channel = SupabaseManager.client.channel("public:messages")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val job = launch {
            try {
                channel.subscribe()
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            try {
                                val msg = json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                                chatMessageDao?.insertMessage(msg)
                                trySend(msg)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        is PostgresAction.Update -> {
                            try {
                                val msg = json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                                chatMessageDao?.insertMessage(msg)
                                trySend(msg)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        is PostgresAction.Delete -> {
                            try {
                                val id = action.oldRecord["id"]?.jsonPrimitive?.longOrNull
                                if (id != null) {
                                    chatMessageDao?.deleteMessageById(id)
                                    trySend(ChatMessage(id = id, senderId = "", text = "__DELETED__"))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        awaitClose {
            launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            job.cancel()
        }
    }

    private var presenceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun subscribeToPresence(currentUserId: String): Flow<Map<String, UserPresence>> = callbackFlow {
        val channel = SupabaseManager.client.channel("presence:study_group")
        presenceChannel = channel
        val presenceMap = mutableMapOf<String, UserPresence>()

        val job = launch {
            try {
                channel.subscribe()
                channel.track(
                    buildJsonObject {
                        put("userId", currentUserId)
                        put("isOnline", true)
                        put("isTyping", false)
                        put("updatedAt", System.currentTimeMillis())
                    }
                )

                channel.presenceDataFlow<JsonObject>().collect { presenceList ->
                    presenceMap.clear()
                    presenceList.forEach { jsonObject ->
                        try {
                            val uid = jsonObject["userId"]?.jsonPrimitive?.content ?: ""
                            val isOnline = jsonObject["isOnline"]?.jsonPrimitive?.booleanOrNull ?: true
                            val isTyping = jsonObject["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false
                            val updatedAt = jsonObject["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                            if (uid.isNotEmpty()) {
                                presenceMap[uid] = UserPresence(uid, isOnline, isTyping, updatedAt)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    trySend(presenceMap.toMap())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        awaitClose {
            launch {
                try {
                    channel.track(
                        buildJsonObject {
                            put("userId", currentUserId)
                            put("isOnline", false)
                            put("isTyping", false)
                            put("updatedAt", System.currentTimeMillis())
                        }
                    )
                    channel.unsubscribe()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            job.cancel()
            presenceChannel = null
        }
    }

    suspend fun updatePresenceStatus(currentUserId: String, isTyping: Boolean) {
        try {
            val channel = presenceChannel ?: SupabaseManager.client.channel("presence:study_group")
            channel.track(
                buildJsonObject {
                    put("userId", currentUserId)
                    put("isOnline", true)
                    put("isTyping", isTyping)
                    put("updatedAt", System.currentTimeMillis())
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

