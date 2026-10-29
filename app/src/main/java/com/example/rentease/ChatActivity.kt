package com.example.rentease

import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private var listenerReg: ListenerRegistration? = null
    private val db            = FirebaseFirestore.getInstance()
    private var receiverId    = ""
    private var receiverName  = ""
    private var conversationId = ""
    private val myUid  get() = SessionManager.getUid(this)
    private val myName get() = SessionManager.getName(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        receiverId     = intent.getStringExtra("receiverId")     ?: ""
        receiverName   = intent.getStringExtra("receiverName")   ?: "Chat"
        conversationId = intent.getStringExtra("conversationId") ?:
            listOf(myUid, receiverId).sorted().joinToString("_")

        setupTopBar()
        setupSendButton()
        listenToMessages()
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private fun setupTopBar() {
        val initials = receiverName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2).joinToString("").uppercase()

        try {
            findViewById<TextView>(R.id.tvChatName).text     = receiverName
            findViewById<TextView>(R.id.tvChatInitials).text = initials
        } catch (e: Exception) { /* layout IDs not found — skip */ }

        try {
            findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() }
        } catch (e: Exception) { /* no back button in layout */ }
    }

    // ── Real-time message listener ────────────────────────────────────────────

    private fun listenToMessages() {
        listenerReg = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                val container = findViewById<LinearLayout>(R.id.messagesContainer)
                container.removeAllViews()

                snapshots?.documents?.forEach { doc ->
                    val text      = doc.getString("text")     ?: return@forEach
                    val senderId  = doc.getString("senderId") ?: return@forEach
                    val timestamp = doc.getLong("timestamp")  ?: 0L
                    val isMe      = senderId == myUid

                    addBubble(container, text, isMe, timestamp)
                }

                // Auto-scroll to bottom
                val scroll = findViewById<ScrollView>(R.id.scrollMessages)
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    // ── Add a message bubble dynamically ─────────────────────────────────────

    private fun addBubble(container: LinearLayout, text: String, isMe: Boolean, timestamp: Long) {
        // Outer wrapper — controls alignment
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            gravity = if (isMe) Gravity.END else Gravity.START
        }

        // Bubble text view
        val bubble = TextView(this).apply {
            this.text = text
            textSize  = 13f
            setPadding(32, 22, 32, 22)
            maxWidth  = resources.displayMetrics.widthPixels * 7 / 10
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (isMe) marginStart = resources.displayMetrics.widthPixels / 4
                else      marginEnd   = resources.displayMetrics.widthPixels / 4
            }
            if (isMe) {
                setBackgroundResource(R.drawable.bg_button_primary)
                setTextColor(android.graphics.Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_stat_card)
                setTextColor(resources.getColor(R.color.navy, null))
            }
        }

        // Time label
        val time = TextView(this).apply {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            this.text = sdf.format(Date(timestamp))
            textSize  = 10f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        wrapper.addView(bubble)
        wrapper.addView(time)
        container.addView(wrapper)
    }

    // ── Send message ──────────────────────────────────────────────────────────

    private fun setupSendButton() {
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend   = findViewById<TextView>(R.id.btnSend)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (myUid.isEmpty()) {
                Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            etMessage.setText("")
            sendMessage(text)
        }
    }

    private fun sendMessage(text: String) {
        val message = hashMapOf(
            "text"       to text,
            "senderId"   to myUid,
            "senderName" to myName,
            "receiverId" to receiverId,
            "timestamp"  to System.currentTimeMillis()
        )

        // Save message to subcollection
        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                // Update conversation summary so it shows in the list
                updateConversationSummary(text)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateConversationSummary(lastText: String) {
        val summary = hashMapOf(
            "participants"         to listOf(myUid, receiverId),
            "lastMessage"          to lastText,
            "lastSenderName"       to myName,
            "lastTimestamp"        to System.currentTimeMillis(),
            "otherName_$myUid"     to myName,
            "otherName_$receiverId" to receiverName
        )
        db.collection("conversations")
            .document(conversationId)
            .set(summary)
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerReg?.remove()
    }
}
