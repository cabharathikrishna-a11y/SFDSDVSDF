package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.ChatMessage
import com.example.ui.AppViewModel
import com.example.ui.chat.ChatDateRangeFilter
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.*
import com.example.util.VideoPlayerDialog
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class VoiceRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File? {
        try {
            outputFile = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            outputFile?.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder = null
            outputFile?.absolutePath
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }
}

fun downloadMediaFile(context: Context, mediaUrlOrPath: String, fileName: String) {
    try {
        if (mediaUrlOrPath.startsWith("http://") || mediaUrlOrPath.startsWith("https://")) {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(mediaUrlOrPath))
                .setTitle(fileName)
                .setDescription("Downloading media file from Google Drive")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } else {
            val srcFile = File(mediaUrlOrPath)
            if (srcFile.exists()) {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                srcFile.copyTo(destFile, overwrite = true)
                Toast.makeText(context, "Saved to Downloads: ${destFile.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Media saved to device storage", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Saved to device storage: $fileName", Toast.LENGTH_SHORT).show()
    }
}

fun shareMediaContent(context: Context, textOrUrl: String, mimeType: String = "text/plain") {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, textOrUrl)
            if (!textOrUrl.startsWith("http") && File(textOrUrl).exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    File(textOrUrl)
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share media", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTabScreen(
    appViewModel: AppViewModel? = null,
    chatViewModel: ChatViewModel = remember { ChatViewModel() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val messages by chatViewModel.messages.collectAsState()
    val filteredMessages by chatViewModel.filteredMessages.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val selectedSenderFilter by chatViewModel.selectedSenderFilter.collectAsState()
    val selectedDateRangeFilter by chatViewModel.selectedDateRangeFilter.collectAsState()
    val isSearchActive by chatViewModel.isSearchActive.collectAsState()
    val availableSenders by chatViewModel.availableSenders.collectAsState()
    val editingMessage by chatViewModel.editingMessage.collectAsState()
    val pinnedMessages by chatViewModel.pinnedMessages.collectAsState()
    val onlineUsers by chatViewModel.onlineUsers.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()
    val replyingToMessage by chatViewModel.replyingToMessage.collectAsState()

    val isMultiSelectActive by chatViewModel.isMultiSelectActive.collectAsState()
    val selectedMessageIds by chatViewModel.selectedMessageIds.collectAsState()

    val isLoading by chatViewModel.isLoading.collectAsState()
    val isLoadingMore by chatViewModel.isLoadingMore.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val currentUserId = chatViewModel.currentUserId

    var textInput by remember { mutableStateOf("") }
    var reactionPickerMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val voiceRecorderHelper = remember(context) { VoiceRecorderHelper(context) }

    val listState = rememberLazyListState()

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
            }
        }
    }

    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            textInput = editingMessage?.text ?: ""
        }
    }

    // Photo Capture Activity Launcher
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                chatViewModel.onSendMediaMessage("photo", photoFile.absolutePath)
                Toast.makeText(context, "Photo captured & uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to process captured photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Media File Picker Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "file"
                val mediaType = when {
                    mime.startsWith("image/") -> "photo"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "voice"
                    else -> "file"
                }
                val inputStream = context.contentResolver.openInputStream(uri)
                val destFile = File(context.cacheDir, "picker_media_${System.currentTimeMillis()}")
                if (inputStream != null) {
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    chatViewModel.onSendMediaMessage(mediaType, destFile.absolutePath)
                    Toast.makeText(context, "Media attached & uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to process picked file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val headerBg = Charcoal
    val screenBg = DeepSlate
    val outgoingBubbleColor = Color(0xFF2E240D)
    val incomingBubbleColor = SurfaceCard
    val textColorOutgoing = Color(0xFFFFF8E1)
    val textColorIncoming = TextPrimary
    val sendButtonBg = WaterBlue

    val totalItems = filteredMessages.size
    val lastVisibleIndex = remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(lastVisibleIndex.value, firstVisibleIndex.value, totalItems) {
        if (totalItems >= 50 && !isLoadingMore && !isLoading && searchQuery.isEmpty()) {
            val remainingEnd = totalItems - 1 - lastVisibleIndex.value
            val remainingStart = firstVisibleIndex.value
            if (remainingEnd <= 50 || remainingStart <= 50) {
                chatViewModel.loadMoreMessages()
            }
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty() && !isSearchActive && searchQuery.isEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            chatViewModel.clearError()
        }
    }

    val isFilterActive = searchQuery.isNotEmpty() || selectedSenderFilter != null || selectedDateRangeFilter != ChatDateRangeFilter.ALL

    Scaffold(
        modifier = modifier.testTag("chat_tab_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isMultiSelectActive) {
                // Multi-Select Top Bar Header
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B),
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = { chatViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Multi-Select", tint = Color.White)
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedMessageIds.size} Selected",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    actions = {
                        // Copy Selected
                        IconButton(onClick = {
                            val text = chatViewModel.getSelectedMessagesText(context)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copied selected messages", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected", tint = Color.White)
                        }
                        // Delete Selected
                        IconButton(onClick = {
                            chatViewModel.deleteSelectedMessages()
                            Toast.makeText(context, "Deleted selected messages", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFFF5252))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerBg,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WaterBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "Group Avatar",
                                    tint = WaterBlue
                                )
                            }
                            Column {
                                Text(
                                    text = "Community Chatroom",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            typingUsers.isNotEmpty() -> "${typingUsers.joinToString(", ") { it.replace("_", " ") }} is typing..."
                                            onlineUsers.isNotEmpty() -> "${onlineUsers.size + 1} online • ${messages.size} msgs"
                                            isLoading -> "Connecting..."
                                            isFilterActive -> "${filteredMessages.size}/${messages.size} filtered"
                                            else -> "1 online • Realtime Active"
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { chatViewModel.toggleSearchActive() },
                            modifier = Modifier.testTag("search_chat_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search Chat",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { chatViewModel.loadInitialMessagesAndSubscribe() },
                            modifier = Modifier.testTag("refresh_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextPrimary
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(screenBg)
        ) {
            // Expandable Search & Filter Panel
            if (isSearchActive || isFilterActive) {
                Surface(
                    color = Charcoal,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = sendButtonBg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { chatViewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text("Search keywords or senders...", fontSize = 13.sp, color = TextSecondary)
                                    },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_input_field")
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { chatViewModel.setSearchQuery("") },
                                        modifier = Modifier.size(28.dp).testTag("clear_search_input")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Date Filter Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Date:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ChatDateRangeFilter.values()) { range ->
                                    val isSelected = selectedDateRangeFilter == range
                                    Surface(
                                        onClick = { chatViewModel.setDateRangeFilter(range) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) sendButtonBg else SurfaceCard,
                                        modifier = Modifier.testTag("date_filter_${range.name}")
                                    ) {
                                        Text(
                                            text = range.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF0F0F11) else TextPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pinned Messages Banner
            if (pinnedMessages.isNotEmpty()) {
                val latestPinned = pinnedMessages.last()
                Surface(
                    color = Color(0xFF221D12),
                    border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().testTag("pinned_messages_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned Message",
                                tint = sendButtonBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Pinned Message (${pinnedMessages.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sendButtonBg
                                )
                                Text(
                                    text = latestPinned.text,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { chatViewModel.togglePinMessage(latestPinned.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unpin",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Chat Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && messages.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = sendButtonBg)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading chat history...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                } else if (filteredMessages.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .align(Alignment.Center),
                        colors = CardDefaults.cardColors(containerColor = Charcoal),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isFilterActive) Icons.Default.SearchOff else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = sendButtonBg,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isFilterActive) "No messages match search filters." else "No messages yet.\nSend a message or media file to start!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = TextPrimary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("messages_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(filteredMessages) { index, message ->
                            val isOutgoing = message.senderId == currentUserId
                            val isFirstInGroup = (index == 0 || messages[index - 1].senderId != message.senderId)
                            val showSenderDetails = isFirstInGroup && !isOutgoing
                            val isSelected = selectedMessageIds.contains(message.id)

                            Spacer(modifier = Modifier.height(if (isFirstInGroup && index > 0) 8.dp else 2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMultiSelectActive) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { chatViewModel.toggleMessageSelection(message.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = WaterBlue,
                                            uncheckedColor = Color.Gray
                                        ),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    ChatBubble(
                                        message = message,
                                        isOutgoing = isOutgoing,
                                        showSenderDetails = showSenderDetails,
                                        outgoingBg = outgoingBubbleColor,
                                        incomingBg = incomingBubbleColor,
                                        textOutgoing = textColorOutgoing,
                                        textIncoming = textColorIncoming,
                                        currentUserId = currentUserId,
                                        isMultiSelectActive = isMultiSelectActive,
                                        onEditClick = { chatViewModel.startEditingMessage(it) },
                                        onLongClickMessage = {
                                            if (isMultiSelectActive) {
                                                chatViewModel.toggleMessageSelection(it.id)
                                            } else {
                                                reactionPickerMessage = it
                                            }
                                        },
                                        onToggleReaction = { msgId, emoji -> chatViewModel.toggleReaction(msgId, emoji) },
                                        onSwipeReply = { chatViewModel.setReplyingTo(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reaction Picker & Message Options Dialog
            if (reactionPickerMessage != null) {
                val targetMsg = reactionPickerMessage!!
                AlertDialog(
                    onDismissRequest = { reactionPickerMessage = null },
                    containerColor = Charcoal,
                    title = {
                        Text(
                            text = "Message Actions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "\"${targetMsg.text}\"",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Quick Emojis
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val quickEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉")
                                val parsed = targetMsg.parseReactions()
                                quickEmojis.forEach { emoji ->
                                    val hasReacted = parsed[emoji]?.contains(currentUserId) == true
                                    Surface(
                                        onClick = {
                                            chatViewModel.toggleReaction(targetMsg.id, emoji)
                                            reactionPickerMessage = null
                                        },
                                        shape = CircleShape,
                                        color = if (hasReacted) sendButtonBg.copy(alpha = 0.25f) else Color.Transparent,
                                        border = BorderStroke(1.dp, if (hasReacted) sendButtonBg else SurfaceCard.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(text = emoji, fontSize = 18.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Copy Text Button
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(targetMsg.text))
                                    Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Text", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Select Multiple
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.startMultiSelect(targetMsg.id)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Checklist, contentDescription = "Select Multiple", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Multiple Messages", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reply Button
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.setReplyingTo(targetMsg)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reply to Message", fontSize = 13.sp, color = sendButtonBg)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pin / Delete
                            val isCurrentlyPinned = targetMsg.isPinned
                            OutlinedButton(
                                onClick = {
                                    chatViewModel.togglePinMessage(targetMsg.id)
                                    reactionPickerMessage = null
                                },
                                border = BorderStroke(1.dp, sendButtonBg.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = sendButtonBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isCurrentlyPinned) "Unpin Message" else "Pin Message", fontSize = 13.sp, color = sendButtonBg)
                            }

                            if (targetMsg.senderId == currentUserId) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        chatViewModel.deleteMessage(targetMsg.id)
                                        reactionPickerMessage = null
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Message", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { reactionPickerMessage = null }) {
                            Text("Cancel", color = sendButtonBg)
                        }
                    }
                )
            }

            // Replying Banner
            val currentReplyMsg = replyingToMessage
            if (currentReplyMsg != null) {
                Surface(
                    color = SurfaceCard,
                    border = BorderStroke(0.5.dp, sendButtonBg.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(sendButtonBg)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Replying to ${currentReplyMsg.senderId.replace("_", " ")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sendButtonBg
                                )
                                Text(
                                    text = currentReplyMsg.text,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { chatViewModel.cancelReply() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Bottom Input Toolbar
            Surface(
                color = Charcoal,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecordingVoice) {
                        // Voice Note Recording UI
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Recording Voice Note... %02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        voiceRecorderHelper.cancelRecording()
                                        isRecordingVoice = false
                                        Toast.makeText(context, "Voice note cancelled", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel Recording", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Voice Note Button
                        FloatingActionButton(
                            onClick = {
                                val recordedPath = voiceRecorderHelper.stopRecording()
                                isRecordingVoice = false
                                if (recordedPath != null) {
                                    chatViewModel.onSendMediaMessage("voice", recordedPath)
                                    Toast.makeText(context, "Voice Note uploaded to Google Drive & sent!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            containerColor = sendButtonBg,
                            contentColor = Color.Black,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Note", tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // Normal Text Input UI
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp)),
                            color = SurfaceCard
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                // Camera Button
                                IconButton(
                                    onClick = { takePhotoLauncher.launch(null) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Take Photo",
                                        tint = sendButtonBg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                TextField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        chatViewModel.onUserTypingChanged(it.isNotEmpty())
                                    },
                                    placeholder = {
                                        Text(
                                            if (editingMessage != null) "Edit message..." else "Type a message...",
                                            color = TextSecondary,
                                            fontSize = 15.sp
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("message_input_field")
                                )

                                // Attachment Button
                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach Media",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (textInput.trim().isEmpty()) {
                            // Mic Button for Recording Voice Notes
                            FloatingActionButton(
                                onClick = {
                                    val temp = voiceRecorderHelper.startRecording()
                                    if (temp != null) {
                                        isRecordingVoice = true
                                    } else {
                                        Toast.makeText(context, "Unable to start microphone recording", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                containerColor = SurfaceCard,
                                contentColor = sendButtonBg,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = sendButtonBg, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            // Send Text Message Button
                            FloatingActionButton(
                                onClick = {
                                    val trimmed = textInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        val currentEditing = editingMessage
                                        if (currentEditing != null) {
                                            chatViewModel.onEditMessage(currentEditing.id, trimmed)
                                        } else {
                                            chatViewModel.onSendMessage(trimmed)
                                        }
                                        textInput = ""
                                        chatViewModel.onUserTypingChanged(false)
                                    }
                                },
                                containerColor = sendButtonBg,
                                contentColor = Color.Black,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag(if (editingMessage != null) "confirm_edit_button" else "send_message_button")
                            ) {
                                Icon(
                                    imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (editingMessage != null) "Save Edit" else "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachment Chooser Sheet / Dialog
    if (showAttachmentSheet) {
        AlertDialog(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = Charcoal,
            title = {
                Text("Attach Media via Google Drive", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a media type to upload and share via Google Drive link:", fontSize = 12.sp, color = TextSecondary)

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            takePhotoLauncher.launch(null)
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Camera Photo", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("image/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photo from Gallery", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("video/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Video from Gallery", color = sendButtonBg)
                    }

                    OutlinedButton(
                        onClick = {
                            showAttachmentSheet = false
                            pickMediaLauncher.launch("*/*")
                        },
                        border = BorderStroke(1.dp, sendButtonBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = sendButtonBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick File / Document", color = sendButtonBg)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentSheet = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    showSenderDetails: Boolean,
    outgoingBg: Color,
    incomingBg: Color,
    textOutgoing: Color,
    textIncoming: Color,
    currentUserId: String = "",
    isMultiSelectActive: Boolean = false,
    onEditClick: ((ChatMessage) -> Unit)? = null,
    onLongClickMessage: ((ChatMessage) -> Unit)? = null,
    onToggleReaction: ((Long, String) -> Unit)? = null,
    onSwipeReply: ((ChatMessage) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleBg = if (isOutgoing) outgoingBg else incomingBg
    val textColor = if (isOutgoing) textOutgoing else textIncoming

    val senderNameFormatted = remember(message.senderId) {
        message.senderId.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    val senderAvatarColor = remember(message.senderId) {
        val colors = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
            Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFB8C00)
        )
        colors[abs(message.senderId.hashCode()) % colors.size]
    }

    val senderInitial = remember(senderNameFormatted) {
        senderNameFormatted.firstOrNull()?.uppercase() ?: "U"
    }

    val shape = if (isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(),
        label = "swipe_reply_offset"
    )

    // Media Embed Detection
    val voiceTag = remember(message.text) { extractMediaTag(message.text, "VOICE") }
    val imageTag = remember(message.text) { extractMediaTag(message.text, "IMAGE") }
    val videoTag = remember(message.text) { extractMediaTag(message.text, "VIDEO") }
    val fileTag = remember(message.text) { extractMediaTag(message.text, "FILE") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}"),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (showSenderDetails) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(senderAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderInitial,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = senderNameFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = senderAvatarColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 70f) {
                                onSwipeReply?.invoke(message)
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0 || offsetX > 0) {
                                offsetX = (offsetX + dragAmount * 0.5f).coerceIn(0f, 140f)
                            }
                        }
                    )
                }
        ) {
            if (animatedOffsetX > 10f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Swipe Reply",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) },
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
                ) {
                    if (isOutgoing && onEditClick != null && voiceTag == null && imageTag == null && videoTag == null) {
                        IconButton(
                            onClick = { onEditClick(message) },
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Message",
                                tint = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        color = bubbleBg,
                        shape = shape,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(start = if (!isOutgoing && !showSenderDetails) 36.dp else 0.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectActive) {
                                        onLongClickMessage?.invoke(message)
                                    }
                                },
                                onLongClick = { onLongClickMessage?.invoke(message) }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Quoted Message Box
                            if (!message.replyToText.isNullOrBlank()) {
                                Surface(
                                    color = if (isOutgoing) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isOutgoing) WaterBlue else WaterBlueAccent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            val replySenderName = message.replyToSender?.replace("_", " ") ?: "User"
                                            Text(
                                                text = replySenderName.split(" ").joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOutgoing) WaterBlue else WaterBlueAccent
                                            )
                                            Text(
                                                text = message.replyToText,
                                                fontSize = 11.sp,
                                                color = textColor.copy(alpha = 0.9f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Render Embedded Drive Media Players or Text
                            when {
                                voiceTag != null -> {
                                    val (path, drive) = voiceTag
                                    ChatAudioPlayer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                imageTag != null -> {
                                    val (path, drive) = imageTag
                                    ChatImageViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                videoTag != null -> {
                                    val (path, drive) = videoTag
                                    ChatVideoViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                fileTag != null -> {
                                    val (path, drive) = fileTag
                                    ChatFileViewer(mediaPathOrUrl = path, driveLink = drive, isOutgoing = isOutgoing)
                                }
                                else -> {
                                    Text(
                                        text = message.text,
                                        fontSize = 14.sp,
                                        color = textColor,
                                        lineHeight = 19.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (message.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                Text(
                                    text = formatChatTimestamp(message.createdAt),
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.65f)
                                )

                                if (isOutgoing) {
                                    Icon(
                                        imageVector = if (message.status == "PENDING") Icons.Default.AccessTime else Icons.Default.DoneAll,
                                        contentDescription = message.status,
                                        tint = if (message.status == "READ") WaterBlueAccent else if (message.status == "PENDING") TextSecondary else TextSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            val parsedReactions = remember(message.reactions) { message.parseReactions() }
                            if (parsedReactions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.align(if (isOutgoing) Alignment.End else Alignment.Start),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    parsedReactions.forEach { (emoji, userList) ->
                                        val hasReacted = userList.contains(currentUserId)
                                        Surface(
                                            onClick = { onToggleReaction?.invoke(message.id, emoji) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (hasReacted) WaterBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, if (hasReacted) WaterBlue else Color.Transparent)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(text = emoji, fontSize = 12.sp)
                                                if (userList.size > 1) {
                                                    Text(
                                                        text = "${userList.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to extract [TAG:localPath|driveUrl] tags from text
private fun extractMediaTag(text: String, tag: String): Pair<String, String>? {
    val pattern = "\\[$tag:(.*?)\\]".toRegex()
    val match = pattern.find(text) ?: return null
    val content = match.groupValues.getOrNull(1) ?: return null
    val parts = content.split("|")
    val local = parts.getOrNull(0) ?: ""
    val drive = parts.getOrNull(1) ?: ""
    return Pair(local, drive)
}

@Composable
fun ChatAudioPlayer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(mediaPathOrUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    currentPos = mediaPlayer?.currentPosition ?: 0
                    duration = mediaPlayer?.duration ?: 0
                } catch (e: Exception) { }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    val togglePlay = {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    if (mediaPathOrUrl.startsWith("http://") || mediaPathOrUrl.startsWith("https://")) {
                        setDataSource(mediaPathOrUrl)
                    } else {
                        setDataSource(context, android.net.Uri.parse(mediaPathOrUrl))
                    }
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPos = 0
                    }
                }
            } else {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error playing audio note", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isOutgoing) Color(0xFF382C10) else Color(0xFF25252A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaterBlue.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
                Text("Google Drive Voice Note", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { togglePlay() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WaterBlue)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = if (duration > 0) currentPos.toFloat() / duration else 0f,
                        onValueChange = { frac ->
                            mediaPlayer?.let { mp ->
                                val target = (frac * duration).toInt()
                                mp.seekTo(target)
                                currentPos = target
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = WaterBlue,
                            activeTrackColor = WaterBlue,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMs(currentPos.toLong()), fontSize = 10.sp, color = TextSecondary)
                        Text(formatMs(duration.toLong()), fontSize = 10.sp, color = TextSecondary)
                    }
                }

                // Download Button
                IconButton(
                    onClick = {
                        val fileName = "Voice_Note_${System.currentTimeMillis()}.m4a"
                        downloadMediaFile(context, mediaPathOrUrl, fileName)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                // Share Button
                IconButton(
                    onClick = {
                        val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                        shareMediaContent(context, shareText, "audio/*")
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ChatImageViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var showFullscreen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WaterBlue.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
            Text("Google Drive Photo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable { showFullscreen = true }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = if (mediaPathOrUrl.startsWith("http")) mediaPathOrUrl else File(mediaPathOrUrl),
                    contentDescription = "Photo Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val fileName = "Photo_${System.currentTimeMillis()}.jpg"
                            downloadMediaFile(context, mediaPathOrUrl, fileName)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                            shareMediaContent(context, shareText, "image/*")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showFullscreen) {
        Dialog(onDismissRequest = { showFullscreen = false }) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = if (mediaPathOrUrl.startsWith("http")) mediaPathOrUrl else File(mediaPathOrUrl),
                        contentDescription = "Fullscreen Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    IconButton(
                        onClick = { showFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatVideoViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    var showPlayerDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WaterBlue.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = WaterBlue, modifier = Modifier.size(12.dp))
            Text("Google Drive Video", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable { showPlayerDialog = true }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WaterBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.Black, modifier = Modifier.size(32.dp))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val fileName = "Video_${System.currentTimeMillis()}.mp4"
                            downloadMediaFile(context, mediaPathOrUrl, fileName)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                            shareMediaContent(context, shareText, "video/*")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showPlayerDialog) {
        VideoPlayerDialog(filePath = mediaPathOrUrl, onDismiss = { showPlayerDialog = false })
    }
}

@Composable
fun ChatFileViewer(
    mediaPathOrUrl: String,
    driveLink: String = "",
    isOutgoing: Boolean = false
) {
    val context = LocalContext.current
    val fileName = remember(mediaPathOrUrl) { File(mediaPathOrUrl).name }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isOutgoing) Color(0xFF382C10) else Color(0xFF25252A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WaterBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = WaterBlue)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Google Drive Document", fontSize = 10.sp, color = WaterBlue)
            }
            IconButton(
                onClick = { downloadMediaFile(context, mediaPathOrUrl, fileName) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    val shareText = if (driveLink.isNotBlank()) driveLink else mediaPathOrUrl
                    shareMediaContent(context, shareText, "*/*")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

fun formatChatTimestamp(isoDate: String?): String {
    if (isoDate.isNullOrBlank() || isoDate == "Just now") return "Just now"
    return try {
        val instant = Instant.parse(isoDate)
        val localTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        localTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    } catch (e: Exception) {
        if (isoDate.length >= 16 && isoDate.contains("T")) {
            isoDate.substring(11, 16)
        } else {
            isoDate
        }
    }
}
