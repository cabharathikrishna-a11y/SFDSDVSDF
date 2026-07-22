package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val appSettings = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val masterSilent = prefs.getBoolean("master_silent_mode", false) || appSettings.getBoolean("master_silent_mode", false)
        val isBackgroundSilent = prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false)

        if (masterSilent || isBackgroundSilent) {
            Log.d(TAG, "Silent/Sleep mode is active. Suppressing push notification.")
            return
        }

        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message is a chat message via data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            val messageType = remoteMessage.data["type"] ?: remoteMessage.data["category"]
            if (messageType == "chat" || remoteMessage.data.containsKey("sender_id") || remoteMessage.data.containsKey("chat_message")) {
                val senderId = remoteMessage.data["sender_id"] ?: remoteMessage.data["sender"] ?: "Community Member"
                val text = remoteMessage.data["text"] ?: remoteMessage.data["chat_message"] ?: remoteMessage.data["body"] ?: ""
                sendChatNotification(senderId, text)
                return
            }
        }

        // Check if message contains a standard notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "Notification", it.body ?: "")
        }
    }

    private fun sendChatNotification(senderId: String, text: String) {
        val context = applicationContext
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val chatNotifEnabled = prefs.getBoolean("chat_notifications_enabled", true) && appSettings.getBoolean("chat_notifications_enabled", true)
        if (!chatNotifEnabled) return

        val masterSilent = prefs.getBoolean("master_silent_mode", false) || appSettings.getBoolean("master_silent_mode", false)
        val soundEnabled = prefs.getBoolean("chat_sound_enabled", true) && appSettings.getBoolean("chat_sound_enabled", true) && !masterSilent

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "MESSAGES")
            putExtra("navigate_to", "chat")
            putExtra("IS_CHAT_NOTIFICATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "chat_messages_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (soundEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, "Chat Messages", importance).apply {
                description = "Notifications for incoming community chat messages"
                enableVibration(soundEnabled)
                if (soundEnabled) {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
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

        val senderDisplayName = senderId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Message from $senderDisplayName")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(if (soundEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            notificationBuilder.setVibrate(longArrayOf(0, 150, 100, 150))
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        if (token.isNullOrEmpty()) return
        try {
            val context = applicationContext
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("user_email", "") ?: ""
            if (email.isBlank()) return

            val dbUrl = com.example.api.FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = com.example.api.DevicePresenceManager.sanitizeEmail(email)
            val deviceKey = com.example.api.DevicePresenceManager.getDeviceKey(context)

            val tokenRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("DEVICES_LOGGED_IN")
                .child(deviceKey)
                .child("fcm token number")

            tokenRef.setValue(token).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Successfully synchronized newly refreshed FCM token to RTDB under $deviceKey")
                } else {
                    Log.e(TAG, "Failed to synchronize refreshed FCM token", task.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendRegistrationToServer", e)
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            val lower = "$title $messageBody".lowercase()
            if (lower.contains("chat") || lower.contains("message")) {
                putExtra("NAVIGATE_TO", "MESSAGES")
                putExtra("navigate_to", "chat")
                putExtra("IS_CHAT_NOTIFICATION", true)
            } else {
                putExtra("NAVIGATE_TO", "DEEPA_AI")
                putExtra("IS_NOTIFICATION", true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
