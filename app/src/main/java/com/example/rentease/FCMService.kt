package com.example.rentease

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    // ── Called when a push notification arrives ───────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "RentEase"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val type  = message.data["type"] ?: ""

        // Route tap to correct screen based on notification type
        val intent = when (type) {
            "maintenance", "maintenance_new", "maintenance_assigned", "maintenance_update" -> {
                val role = getSharedPreferences("RentEaseSession", Context.MODE_PRIVATE)
                    .getString("role", "Tenant")
                if (role == "Landlord") Intent(this, LandlordMaintenanceActivity::class.java)
                else                    Intent(this, MaintenanceActivity::class.java)
            }
            "payment"   -> Intent(this, PaymentsActivity::class.java)
            "message"   -> Intent(this, MessagesActivity::class.java)
            else        -> {
                val role = getSharedPreferences("RentEaseSession", Context.MODE_PRIVATE)
                    .getString("role", "Tenant")
                if (role == "Landlord") Intent(this, LandlordHomeActivity::class.java)
                else                    Intent(this, HomeActivity::class.java)
            }
        }

        showNotification(title, body, intent)
    }

    // ── Called when FCM assigns a new token ───────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    // ── Show notification in system tray ──────────────────────────────────────

    private fun showNotification(title: String, body: String, intent: Intent) {
        val channelId = "rentease_channel"

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RentEase Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description    = "Rent, maintenance and message alerts"
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ── Save new FCM token to Firestore ───────────────────────────────────────

    private fun saveTokenToFirestore(token: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    android.util.Log.d("FCM", "Token updated for $uid")
                }
        }
    }
}
