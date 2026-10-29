package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setupBackButton()
        loadNotifications()
    }

    private fun setupBackButton() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.btnClearAll).setOnClickListener { clearAllNotifications() } } catch (e: Exception) {}
    }

    private fun loadNotifications() {
        val uid = SessionManager.getUid(this)
        if (uid.isEmpty()) return

        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val container = try {
                    findViewById<LinearLayout>(R.id.notificationsContainer)
                } catch (e: Exception) { return@addOnSuccessListener }

                container.removeAllViews()

                if (docs.isEmpty) {
                    val empty = TextView(this).apply {
                        text      = "No notifications yet"
                        textSize  = 14f
                        setTextColor(resources.getColor(R.color.text_muted, null))
                        gravity   = Gravity.CENTER
                        setPadding(0, 80, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    container.addView(empty)
                    return@addOnSuccessListener
                }

                // Sort by createdAt descending in memory
                val sorted = docs.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }

                sorted.forEach { doc ->
                    val title     = doc.getString("title")  ?: return@forEach
                    val body      = doc.getString("body")   ?: ""
                    val type      = doc.getString("type")   ?: ""
                    val read      = doc.getBoolean("read")  ?: false
                    val ts        = doc.getLong("createdAt") ?: 0L
                    val docId     = doc.id

                    addNotificationRow(container, docId, title, body, type, read, ts)
                }

                // Mark all as read
                markAllRead(uid)
            }
    }

    private fun addNotificationRow(
        container: LinearLayout,
        docId: String,
        title: String,
        body: String,
        type: String,
        read: Boolean,
        timestamp: Long
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 32, 40, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity    = Gravity.TOP
            isClickable  = true
            isFocusable  = true
            if (!read) setBackgroundColor(resources.getColor(R.color.blue_light_bg, null))
        }

        // Icon
        val icon = TextView(this).apply {
            text     = when {
                type.contains("maintenance") -> "🔧"
                type.contains("payment")     -> "💳"
                type.contains("message")     -> "💬"
                else                          -> "🔔"
            }
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16 }
        }

        // Text block
        val textBlock = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            this.text = title
            textSize  = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
        }

        val bodyView = TextView(this).apply {
            this.text = body
            textSize  = 12f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        val timeView = TextView(this).apply {
            this.text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
            textSize  = 10f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        textBlock.addView(titleView)
        textBlock.addView(bodyView)
        textBlock.addView(timeView)
        row.addView(icon)
        row.addView(textBlock)

        // Tap to go to relevant screen
        row.setOnClickListener {
            val role = SessionManager.getRole(this)
            val intent = when {
                type.contains("maintenance") -> {
                    if (role == "Landlord") Intent(this, LandlordMaintenanceActivity::class.java)
                    else                    Intent(this, MaintenanceActivity::class.java)
                }
                type.contains("payment") -> Intent(this, PaymentsActivity::class.java)
                type.contains("message") -> Intent(this, MessagesActivity::class.java)
                else -> {
                    if (role == "Landlord") Intent(this, LandlordHomeActivity::class.java)
                    else                    Intent(this, HomeActivity::class.java)
                }
            }
            startActivity(intent)
        }

        // Add divider between rows
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(40, 0, 40, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        }

        container.addView(row)
        container.addView(divider)
    }

    private fun markAllRead(uid: String) {
        db.collection("notifications")
            .whereEqualTo("userId", uid)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { docs ->
                val batch = db.batch()
                docs.forEach { batch.update(it.reference, "read", true) }
                batch.commit()
            }
    }

    private fun clearAllNotifications() {
        val uid = SessionManager.getUid(this)
        db.collection("notifications").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { docs ->
                val batch = db.batch()
                docs.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener {
                    loadNotifications()
                }
            }
    }
}
