package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import com.example.model.UserPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

enum class ChatDateRangeFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days")
}

enum class ChatOptionType {
    STUDY_GROUP,
    DIRECT_MESSAGE
}

data class ChatOption(
    val id: String,
    val name: String,
    val type: ChatOptionType,
    val description: String,
    val memberUserId: String? = null,
    val iconEmoji: String = "🎓",
    val memberCount: Int = 0,
    val isOnline: Boolean = false,
    val roleTitle: String = ""
)

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    val currentUserId: String = "user_me"
) : ViewModel() {

    val studyGroups = listOf(
        ChatOption(
            id = "group_main",
            name = "Android Dev Study Group",
            type = ChatOptionType.STUDY_GROUP,
            description = "Main Community • Jetpack Compose, Kotlin, Room & Architecture",
            iconEmoji = "🎓",
            memberCount = 42
        ),
        ChatOption(
            id = "group_kotlin",
            name = "Kotlin & Coroutines Circle",
            type = ChatOptionType.STUDY_GROUP,
            description = "Deep-dive into Coroutines, Flow, Generics & OOP",
            iconEmoji = "📚",
            memberCount = 28
        ),
        ChatOption(
            id = "group_ai",
            name = "AI Studio & Gemini Tech Circle",
            type = ChatOptionType.STUDY_GROUP,
            description = "Generative AI, Prompt Engineering & Agent Systems",
            iconEmoji = "⚡",
            memberCount = 35
        ),
        ChatOption(
            id = "group_examprep",
            name = "Exam Prep & Code Reviews",
            type = ChatOptionType.STUDY_GROUP,
            description = "Algorithms, System Design & Live Practice",
            iconEmoji = "📝",
            memberCount = 19
        ),
        ChatOption(
            id = "group_lounge",
            name = "General Student Lounge",
            type = ChatOptionType.STUDY_GROUP,
            description = "Casual tech chat, project showcases & study breaks",
            iconEmoji = "💡",
            memberCount = 56
        )
    )

    val directMessageMembers = listOf(
        ChatOption(
            id = "dm_alex_dev",
            name = "Alex Dev",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Senior Android Engineer",
            memberUserId = "alex_dev",
            iconEmoji = "👨‍💻",
            isOnline = true,
            roleTitle = "Community Admin"
        ),
        ChatOption(
            id = "dm_sarah_pm",
            name = "Sarah PM",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Product Manager",
            memberUserId = "sarah_pm",
            iconEmoji = "👩‍💼",
            isOnline = true,
            roleTitle = "Group Lead"
        ),
        ChatOption(
            id = "dm_bharathi_k",
            name = "Bharathi K",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Fullstack & Mobile Developer",
            memberUserId = "bharathi_k",
            iconEmoji = "🧑‍💻",
            isOnline = true,
            roleTitle = "Peer Tutor"
        ),
        ChatOption(
            id = "dm_dev_lead",
            name = "Dev Lead",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Tech Architect",
            memberUserId = "dev_lead",
            iconEmoji = "🛡️",
            isOnline = false,
            roleTitle = "Mentor"
        ),
        ChatOption(
            id = "dm_community_user_1",
            name = "Community User 1",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Android Enthusiast",
            memberUserId = "community_user_1",
            iconEmoji = "🙋",
            isOnline = false,
            roleTitle = "Student Member"
        ),
        ChatOption(
            id = "dm_community_user_2",
            name = "Community User 2",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Kotlin Developer",
            memberUserId = "community_user_2",
            iconEmoji = "🙋",
            isOnline = true,
            roleTitle = "Student Member"
        ),
        ChatOption(
            id = "dm_qa_tester_3",
            name = "QA Tester 3",
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Quality Assurance",
            memberUserId = "qa_tester_3",
            iconEmoji = "🧪",
            isOnline = true,
            roleTitle = "Tester"
        )
    )

    private val _selectedChatOption = MutableStateFlow<ChatOption>(studyGroups[0])
    val selectedChatOption: StateFlow<ChatOption> = _selectedChatOption.asStateFlow()

    private val _showChatOptionsSheet = MutableStateFlow(false)
    val showChatOptionsSheet: StateFlow<Boolean> = _showChatOptionsSheet.asStateFlow()

    fun selectChatOption(option: ChatOption) {
        _selectedChatOption.value = option
        _showChatOptionsSheet.value = false
        ensureSampleMessagesForOption(option)
    }

    fun openChatOptionsSheet() {
        _showChatOptionsSheet.value = true
    }

    fun closeChatOptionsSheet() {
        _showChatOptionsSheet.value = false
    }

    private fun ensureSampleMessagesForOption(option: ChatOption) {
        if (option.type == ChatOptionType.DIRECT_MESSAGE) {
            // No fake sample messages generated for DM channels. DMs rely strictly on real messages sent by users/members.
            return
        }
        val current = _messages.value
        val hasMessagesForOption = current.any { msg ->
            if (option.id == "group_main") true
            else msg.text.contains("[GROUP:${option.id}]")
        }

        if (!hasMessagesForOption) {
            val sampleMsgs = mutableListOf<ChatMessage>()
            val nowTime = "Just now"
            val baseId = System.currentTimeMillis()

            when (option.id) {
                "group_kotlin" -> {
                    sampleMsgs.add(
                        ChatMessage(
                            id = baseId + 1,
                            senderId = "alex_dev",
                            text = "Welcome to the Kotlin & Coroutines Circle! 📚\n[GROUP:group_kotlin]\nFeel free to ask questions about StateFlow, SharedFlow, and Structured Concurrency.",
                            createdAt = nowTime,
                            status = "READ"
                        )
                    )
                    sampleMsgs.add(
                        ChatMessage(
                            id = baseId + 2,
                            senderId = "bharathi_k",
                            text = "Excited to study together! Working on Kotlin Flow transformations this week.\n[GROUP:group_kotlin]",
                            createdAt = nowTime,
                            status = "READ"
                        )
                    )
                }
                "group_ai" -> {
                    sampleMsgs.add(
                        ChatMessage(
                            id = baseId + 1,
                            senderId = "sarah_pm",
                            text = "Welcome to the AI Studio & Gemini Tech Circle! ⚡\n[GROUP:group_ai]\nWe discuss Gemini APIs, prompt engineering, and AI features in Android.",
                            createdAt = nowTime,
                            status = "READ"
                        )
                    )
                }
                "group_examprep" -> {
                    sampleMsgs.add(
                        ChatMessage(
                            id = baseId + 1,
                            senderId = "dev_lead",
                            text = "Welcome to Exam Prep & Code Reviews! 📝\n[GROUP:group_examprep]\nToday's topic: Data Structures & System Design practice.",
                            createdAt = nowTime,
                            status = "READ"
                        )
                    )
                }
                "group_lounge" -> {
                    sampleMsgs.add(
                        ChatMessage(
                            id = baseId + 1,
                            senderId = "community_user_2",
                            text = "Hey everyone! Welcome to the Student Lounge 💡\n[GROUP:group_lounge]\nShare what you are building today!",
                            createdAt = nowTime,
                            status = "READ"
                        )
                    )
                }
            }

            if (sampleMsgs.isNotEmpty()) {
                _messages.value = (current + sampleMsgs).distinctBy { if (it.id != 0L) it.id else it.hashCode() }
            }
        }
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSenderFilter = MutableStateFlow<String?>(null)
    val selectedSenderFilter: StateFlow<String?> = _selectedSenderFilter.asStateFlow()

    private val _selectedDateRangeFilter = MutableStateFlow(ChatDateRangeFilter.ALL)
    val selectedDateRangeFilter: StateFlow<ChatDateRangeFilter> = _selectedDateRangeFilter.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    val availableSenders: StateFlow<List<String>> = _messages.map { list ->
        list.map { it.senderId }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedMessages: StateFlow<List<ChatMessage>> = _messages.map { list ->
        list.filter { it.isPinned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _presenceMap = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val presenceMap: StateFlow<Map<String, UserPresence>> = _presenceMap.asStateFlow()

    val onlineUsers: StateFlow<List<String>> = _presenceMap.map { map ->
        map.filter { (id, presence) -> id != currentUserId && presence.isOnline }.keys.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val typingUsers: StateFlow<List<String>> = _presenceMap.map { map ->
        map.filter { (id, presence) -> id != currentUserId && presence.isOnline && presence.isTyping }.keys.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _replyingToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyingToMessage: StateFlow<ChatMessage?> = _replyingToMessage.asStateFlow()

    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> = _selectedMessageIds.asStateFlow()

    private val _isMultiSelectActive = MutableStateFlow(false)
    val isMultiSelectActive: StateFlow<Boolean> = _isMultiSelectActive.asStateFlow()

    fun setReplyingTo(message: ChatMessage?) {
        _replyingToMessage.value = message
    }

    fun cancelReply() {
        _replyingToMessage.value = null
    }

    fun toggleMessageSelection(messageId: Long) {
        val current = _selectedMessageIds.value
        if (current.contains(messageId)) {
            val updated = current - messageId
            _selectedMessageIds.value = updated
            if (updated.isEmpty()) {
                _isMultiSelectActive.value = false
            }
        } else {
            _selectedMessageIds.value = current + messageId
            _isMultiSelectActive.value = true
        }
    }

    fun startMultiSelect(initialMessageId: Long? = null) {
        _isMultiSelectActive.value = true
        _selectedMessageIds.value = if (initialMessageId != null) setOf(initialMessageId) else emptySet()
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
        _isMultiSelectActive.value = false
    }

    fun deleteSelectedMessages() {
        val idsToDelete = _selectedMessageIds.value
        if (idsToDelete.isEmpty()) return
        val currentList = _messages.value
        _messages.value = currentList.filter { it.id !in idsToDelete }
        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            idsToDelete.forEach { id ->
                try {
                    repository.deleteMessage(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getSelectedMessagesText(context: android.content.Context): String {
        val ids = _selectedMessageIds.value
        return _messages.value
            .filter { it.id in ids }
            .joinToString("\n---\n") { "${it.senderId.replace("_", " ")}: ${it.text}" }
    }

    fun onSendMediaMessage(mediaType: String, mediaPathOrUri: String, caption: String = "") {
        val cleanType = mediaType.lowercase()
        val fileId = "drive_${cleanType}_${System.currentTimeMillis()}"
        val driveShareUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        val driveDirectUrl = "https://drive.google.com/uc?export=download&id=$fileId"

        val typeTag = when (cleanType) {
            "voice", "audio" -> "VOICE"
            "image", "photo" -> "IMAGE"
            "video" -> "VIDEO"
            else -> "FILE"
        }

        val typeLabel = when (typeTag) {
            "VOICE" -> "🎙️ Voice Recording"
            "IMAGE" -> "📷 Photo Attachment"
            "VIDEO" -> "🎥 Video Attachment"
            else -> "📁 File Attachment"
        }

        val captionText = if (caption.isNotBlank()) "\n$caption" else ""
        val fullFormattedText = "$typeLabel (Google Drive link: $driveShareUrl)$captionText\n[$typeTag:$mediaPathOrUri|$driveDirectUrl]"

        onSendMessage(fullFormattedText)
    }

    val filteredMessages: StateFlow<List<ChatMessage>> = combine(
        _messages,
        _searchQuery,
        _selectedSenderFilter,
        _selectedDateRangeFilter,
        _selectedChatOption
    ) { msgList, query, sender, dateFilter, option ->
        val trimmedQuery = query.trim().lowercase()
        val now = ZonedDateTime.now(ZoneId.systemDefault())

        msgList.filter { msg ->
            // 0. Channel / Direct Message Isolation
            val matchesOption = if (option.type == ChatOptionType.STUDY_GROUP) {
                if (option.id == "group_main") {
                    !msg.text.contains("[GROUP:") && !msg.text.contains("[DM:")
                } else {
                    msg.text.contains("[GROUP:${option.id}]")
                }
            } else {
                val targetMember = option.memberUserId ?: ""
                msg.text.contains("[DM:$targetMember]") ||
                        (msg.senderId == targetMember && (msg.replyToSender == currentUserId || msg.text.contains("[DM:$currentUserId]") || msg.text.contains("[DM:$targetMember]"))) ||
                        (msg.senderId == currentUserId && (msg.replyToSender == targetMember || msg.text.contains("[DM:$targetMember]")))
            }

            // 1. Keyword search (matches message text or sender name)
            val matchesKeyword = if (trimmedQuery.isEmpty()) true else {
                msg.text.lowercase().contains(trimmedQuery) ||
                        msg.senderId.lowercase().replace("_", " ").contains(trimmedQuery)
            }

            // 2. Sender filter
            val matchesSender = if (sender.isNullOrEmpty()) true else {
                msg.senderId.equals(sender, ignoreCase = true)
            }

            // 3. Date range filter
            val matchesDate = when (dateFilter) {
                ChatDateRangeFilter.ALL -> true
                ChatDateRangeFilter.TODAY -> isSameDay(msg.createdAt, now)
                ChatDateRangeFilter.LAST_7_DAYS -> isWithinDays(msg.createdAt, now, 7)
                ChatDateRangeFilter.LAST_30_DAYS -> isWithinDays(msg.createdAt, now, 30)
            }

            matchesOption && matchesKeyword && matchesSender && matchesDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentLimit = 100
    private var canLoadMore = true

    init {
        loadInitialMessagesAndSubscribe()
        listenToRtdbChatHistory()
    }

    fun loadInitialMessagesAndSubscribe() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            currentLimit = 100
            canLoadMore = true

            // 1. Load initial 100 locally cached Room messages first for immediate display
            try {
                val cached = repository.getRecentCachedMessages(100)
                if (cached.isNotEmpty()) {
                    _messages.value = cached
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fetch last 100 messages from Supabase and sync with Room
            try {
                val synced = repository.fetchAndCacheRecentMessages(100)
                if (synced.isNotEmpty()) {
                    _messages.value = synced
                }
                // Mark unread incoming messages as READ when recipient opens chat
                markAllUnreadIncomingMessagesAsRead()
            } catch (e: Exception) {
                if (_messages.value.isEmpty()) {
                    _errorMessage.value = "Unable to fetch Supabase messages: ${e.localizedMessage ?: "Connection failed"}"
                }
            } finally {
                _isLoading.value = false
            }

            // 3. Connect Realtime WebSocket subscription for live messages & status updates
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repository.subscribeToPresence(currentUserId).collect { presenceMap ->
                        _presenceMap.value = presenceMap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                repository.subscribeToNewMessages().collect { realtimeMsg ->
                    if (realtimeMsg.text == "__DELETED__") {
                        _messages.value = _messages.value.filter { it.id != realtimeMsg.id }
                        if (_editingMessage.value?.id == realtimeMsg.id) {
                            _editingMessage.value = null
                        }
                    } else {
                        val existingList = _messages.value
                        val existingIndex = existingList.indexOfFirst { (it.id != 0L && it.id == realtimeMsg.id) }

                        if (existingIndex != -1) {
                            // Message status or text update (e.g., status changed to READ)
                            val updatedList = existingList.toMutableList()
                            updatedList[existingIndex] = realtimeMsg
                            _messages.value = updatedList
                        } else {
                            // New incoming message
                            val isFromOther = realtimeMsg.senderId != currentUserId
                            val finalMsg = if (isFromOther) realtimeMsg.copy(status = "READ") else realtimeMsg
                            
                            _messages.value = (existingList + finalMsg).distinctBy { if (it.id != 0L) it.id else it.hashCode() }

                            if (isFromOther) {
                                sendIncomingMessageNotification(realtimeMsg)
                                // Emit READ receipt update via Supabase & Room
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        repository.markMessagesAsRead(listOf(realtimeMsg.id))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenToRtdbChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                val dbUrl = com.example.api.FirebaseConfig.getDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val chatRef = database.getReference("community_chat/messages")

                chatRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val rtdbList = mutableListOf<ChatMessage>()
                            for (child in snapshot.children) {
                                try {
                                    val id = child.child("id").getValue(Long::class.java) ?: (child.key?.toLongOrNull() ?: 0L)
                                    val senderId = child.child("senderId").getValue(String::class.java) ?: "community_user"
                                    val text = child.child("text").getValue(String::class.java) ?: ""
                                    val createdAt = child.child("createdAt").getValue(String::class.java) ?: "Just now"
                                    val status = child.child("status").getValue(String::class.java) ?: "SENT"
                                    val isPinned = child.child("isPinned").getValue(Boolean::class.java) ?: false
                                    val reactions = child.child("reactions").getValue(String::class.java) ?: ""
                                    val replyToId = child.child("replyToId").getValue(Long::class.java)
                                    val replyToText = child.child("replyToText").getValue(String::class.java)
                                    val replyToSender = child.child("replyToSender").getValue(String::class.java)

                                    if (text.isNotBlank() && id != 0L) {
                                        rtdbList.add(
                                            ChatMessage(
                                                id = id,
                                                senderId = senderId,
                                                text = text,
                                                createdAt = createdAt,
                                                status = status,
                                                isPinned = isPinned,
                                                reactions = reactions,
                                                replyToId = replyToId,
                                                replyToText = replyToText,
                                                replyToSender = replyToSender
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatViewModel", "Error parsing RTDB chat msg", e)
                                }
                            }

                            if (rtdbList.isNotEmpty()) {
                                val currentList = _messages.value
                                val merged = (currentList + rtdbList).distinctBy { if (it.id != 0L) it.id else it.hashCode() }
                                _messages.value = merged

                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        repository.insertMessagesToCache(rtdbList)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Failed caching RTDB messages", e)
                                    }
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("ChatViewModel", "RTDB Chat listener error: ${error.message}")
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed setting up RTDB chat listener", e)
            }
        }
    }

    private fun syncMessageToRtdb(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                val dbUrl = com.example.api.FirebaseConfig.getDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val msgKey = if (message.id != 0L) message.id.toString() else System.currentTimeMillis().toString()
                val payload = mapOf(
                    "id" to (if (message.id != 0L) message.id else msgKey.toLongOrNull() ?: 0L),
                    "senderId" to message.senderId,
                    "text" to message.text,
                    "createdAt" to message.createdAt,
                    "status" to message.status,
                    "isPinned" to message.isPinned,
                    "reactions" to message.reactions,
                    "replyToId" to message.replyToId,
                    "replyToText" to message.replyToText,
                    "replyToSender" to message.replyToSender
                )
                database.getReference("community_chat/messages").child(msgKey).setValue(payload)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed syncing message to RTDB", e)
            }
        }
    }

    fun markAllUnreadIncomingMessagesAsRead() {
        val currentList = _messages.value
        val hasUnread = currentList.any { it.senderId != currentUserId && it.status != "READ" }
        if (hasUnread) {
            _messages.value = currentList.map {
                if (it.senderId != currentUserId && it.status != "READ") it.copy(status = "READ") else it
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repository.markIncomingMessagesAsRead(currentUserId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun sendIncomingMessageNotification(message: ChatMessage) {
        try {
            val context = com.example.MainApplication.instance
            val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val appSettings = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)

            // Check if master silent mode is active
            val masterSilent = appPrefs.getBoolean("master_silent_mode", false) || appSettings.getBoolean("master_silent_mode", false)
            val chatNotifEnabled = appPrefs.getBoolean("chat_notifications_enabled", true) && appSettings.getBoolean("chat_notifications_enabled", true)
            if (!chatNotifEnabled) return

            val soundEnabled = appPrefs.getBoolean("chat_sound_enabled", true) && appSettings.getBoolean("chat_sound_enabled", true) && !masterSilent

            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "chat_messages_channel"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val importance = if (soundEnabled) android.app.NotificationManager.IMPORTANCE_HIGH else android.app.NotificationManager.IMPORTANCE_LOW
                val channel = android.app.NotificationChannel(channelId, "Chat Messages", importance).apply {
                    description = "Notifications for incoming community chat messages"
                    enableVibration(soundEnabled)
                    if (soundEnabled) {
                        setSound(
                            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        setSound(null, null)
                    }
                }
                notificationManager.createNotificationChannel(channel)
            }

            val senderDisplayName = message.senderId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Message from $senderDisplayName")
                .setContentText(message.text)
                .setPriority(if (soundEnabled) androidx.core.app.NotificationCompat.PRIORITY_HIGH else androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)

            if (soundEnabled) {
                builder.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                builder.setVibrate(longArrayOf(0, 150, 100, 150))
            }

            notificationManager.notify((message.id % 1000000).toInt().let { if (it == 0) System.currentTimeMillis().toInt() else it }, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMoreMessages() {
        if (_isLoadingMore.value || !canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextLimit = currentLimit + 1000
            try {
                val expanded = repository.fetchAndCacheRecentMessages(nextLimit)
                if (expanded.size <= _messages.value.size) {
                    canLoadMore = false
                } else {
                    _messages.value = expanded
                    currentLimit = nextLimit
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    fun startEditingMessage(message: ChatMessage) {
        if (message.senderId == currentUserId) {
            _editingMessage.value = message
        }
    }

    fun cancelEditingMessage() {
        _editingMessage.value = null
    }

    fun onEditMessage(messageId: Long, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        _editingMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = repository.editMessage(messageId, trimmed)
                if (updated != null) {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) updated else it
                    }
                } else {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(text = trimmed) else it
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(text = trimmed) else it
                }
                _errorMessage.value = "Failed to sync message edit (${e.localizedMessage ?: "Offline"})"
            }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        val currentList = _messages.value
        val targetMsg = currentList.find { it.id == messageId } ?: return
        val currentMap = targetMsg.parseReactions().toMutableMap()
        val usersForEmoji = (currentMap[emoji] ?: emptyList()).toMutableList()

        if (usersForEmoji.contains(currentUserId)) {
            usersForEmoji.remove(currentUserId)
        } else {
            usersForEmoji.add(currentUserId)
        }

        if (usersForEmoji.isEmpty()) {
            currentMap.remove(emoji)
        } else {
            currentMap[emoji] = usersForEmoji
        }

        val updatedReactionsStr = ChatMessage.formatReactions(currentMap)

        // Optimistic UI update
        _messages.value = currentList.map {
            if (it.id == messageId) it.copy(reactions = updatedReactionsStr) else it
        }

        // Async sync to Supabase and Room
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateMessageReaction(messageId, updatedReactionsStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePinMessage(messageId: Long) {
        val currentList = _messages.value
        val targetMsg = currentList.find { it.id == messageId } ?: return
        val newPinnedState = !targetMsg.isPinned

        // Optimistic UI update
        _messages.value = currentList.map {
            if (it.id == messageId) it.copy(isPinned = newPinnedState) else it
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.pinMessage(messageId, newPinnedState)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        val currentList = _messages.value
        _messages.value = currentList.filter { it.id != messageId }
        if (_editingMessage.value?.id == messageId) {
            _editingMessage.value = null
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteMessage(messageId)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to delete message (${e.localizedMessage ?: "Offline"})"
            }
        }
    }

    fun onSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val replyMsg = _replyingToMessage.value
        _replyingToMessage.value = null

        val currentOption = _selectedChatOption.value
        val formattedText = when {
            currentOption.type == ChatOptionType.STUDY_GROUP && currentOption.id != "group_main" -> {
                if (!trimmed.contains("[GROUP:")) "$trimmed\n[GROUP:${currentOption.id}]" else trimmed
            }
            currentOption.type == ChatOptionType.DIRECT_MESSAGE -> {
                val targetMember = currentOption.memberUserId ?: ""
                if (!trimmed.contains("[DM:")) "$trimmed\n[DM:$targetMember]" else trimmed
            }
            else -> trimmed
        }

        val targetRecipient = if (currentOption.type == ChatOptionType.DIRECT_MESSAGE) currentOption.memberUserId else replyMsg?.senderId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sent = repository.sendMessage(
                    text = formattedText,
                    senderId = currentUserId,
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.text,
                    replyToSender = targetRecipient
                )
                _messages.value = (_messages.value + sent).distinctBy { if (it.id != 0L) it.id else it.hashCode() }
                syncMessageToRtdb(sent)
            } catch (e: Exception) {
                // Optimistic local fallback for smooth user feedback
                val tempMsg = ChatMessage(
                    id = System.currentTimeMillis(),
                    senderId = currentUserId,
                    text = formattedText,
                    status = "PENDING",
                    createdAt = "Just now",
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.text,
                    replyToSender = targetRecipient
                )
                _messages.value = _messages.value + tempMsg
                _errorMessage.value = "Message sent locally (${e.localizedMessage ?: "Offline"})"
                syncMessageToRtdb(tempMsg)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSenderFilter(sender: String?) {
        _selectedSenderFilter.value = sender
    }

    fun setDateRangeFilter(filter: ChatDateRangeFilter) {
        _selectedDateRangeFilter.value = filter
    }

    fun toggleSearchActive(active: Boolean? = null) {
        val next = active ?: !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) {
            clearSearchAndFilters()
        }
    }

    fun clearSearchAndFilters() {
        _searchQuery.value = ""
        _selectedSenderFilter.value = null
        _selectedDateRangeFilter.value = ChatDateRangeFilter.ALL
    }

    private var typingJob: Job? = null
    private var isCurrentlyTyping = false

    fun onUserTypingChanged(isTyping: Boolean) {
        if (isTyping) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updatePresenceStatus(currentUserId, true)
                }
            }
            typingJob?.cancel()
            typingJob = viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(3000)
                isCurrentlyTyping = false
                repository.updatePresenceStatus(currentUserId, false)
            }
        } else {
            if (isCurrentlyTyping) {
                isCurrentlyTyping = false
                typingJob?.cancel()
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updatePresenceStatus(currentUserId, false)
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun isSameDay(isoDate: String?, now: ZonedDateTime): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return try {
            val msgTime = ZonedDateTime.ofInstant(Instant.parse(isoDate), ZoneId.systemDefault())
            msgTime.toLocalDate() == now.toLocalDate()
        } catch (e: Exception) {
            false
        }
    }

    private fun isWithinDays(isoDate: String?, now: ZonedDateTime, days: Long): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return try {
            val msgTime = ZonedDateTime.ofInstant(Instant.parse(isoDate), ZoneId.systemDefault())
            val threshold = now.minusDays(days)
            !msgTime.isBefore(threshold)
        } catch (e: Exception) {
            false
        }
    }
}
