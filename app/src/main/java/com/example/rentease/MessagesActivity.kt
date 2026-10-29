package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MessagesActivity : AppCompatActivity() {

    private val db         = FirebaseFirestore.getInstance()
    private val myUid      get() = SessionManager.getUid(this)
    private val myName     get() = SessionManager.getName(this)
    private val myRole     get() = SessionManager.getRole(this)
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        setupBottomNav()
        // Pre-create conversation with landlord/tenant so name shows immediately
        ensureDefaultConversation()
        loadConversations()
    }

    // ── For NEW tenants: pre-link with their landlord so chat is visible ──────

    private fun ensureDefaultConversation() {
        if (myRole != "Tenant") return
        val uid = myUid

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val landlordId = doc.getString("landlordId") ?: ""
                if (landlordId.isEmpty()) {
                    // Try via invite code
                    db.collection("inviteCodes")
                        .whereEqualTo("usedBy", uid)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { codes ->
                            if (!codes.isEmpty) {
                                val lId = codes.documents[0].getString("landlordId") ?: ""
                                if (lId.isNotEmpty()) {
                                    db.collection("users").document(lId).get()
                                        .addOnSuccessListener { lDoc ->
                                            val lName = lDoc.getString("name") ?: "Landlord"
                                            createOrUpdateConversation(uid, myName, lId, lName)
                                        }
                                }
                            }
                        }
                } else {
                    db.collection("users").document(landlordId).get()
                        .addOnSuccessListener { lDoc ->
                            val lName = lDoc.getString("name") ?: "Landlord"
                            createOrUpdateConversation(uid, myName, landlordId, lName)
                        }
                }
            }
    }

    private fun createOrUpdateConversation(
        uid: String, myName: String, otherId: String, otherName: String
    ) {
        val convId = listOf(uid, otherId).sorted().joinToString("_")
        val convRef = db.collection("conversations").document(convId)

        convRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                // Create conversation document so it shows in both users' message lists
                convRef.set(hashMapOf(
                    "participants"         to listOf(uid, otherId),
                    "lastMessage"          to "Tap to start chatting",
                    "lastTimestamp"        to System.currentTimeMillis(),
                    "otherName_$uid"       to otherName,   // what the tenant sees
                    "otherName_$otherId"   to myName       // what the landlord sees
                ))
            } else {
                // Ensure names are set (may have been created without them)
                val updates = mutableMapOf<String, Any>()
                if (!doc.contains("otherName_$uid"))     updates["otherName_$uid"]     = otherName
                if (!doc.contains("otherName_$otherId")) updates["otherName_$otherId"] = myName
                if (updates.isNotEmpty()) convRef.update(updates)
            }
        }
    }

    // ── Load conversations with real names ────────────────────────────────────

    private fun loadConversations() {
        val uid = myUid

        listener = db.collection("conversations")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                val container = try { findViewById<LinearLayout>(R.id.conversationsContainer) }
                catch (e: Exception) { return@addSnapshotListener }
                container.removeAllViews()

                val emptyView = try { findViewById<TextView>(R.id.tvNoMessages) } catch (e: Exception) { null }

                if (snapshots == null || snapshots.isEmpty) {
                    emptyView?.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                emptyView?.visibility = View.GONE

                val sorted = snapshots.documents.sortedByDescending { it.getLong("lastTimestamp") ?: 0L }

                sorted.forEach { doc ->
                    val participants  = doc.get("participants") as? List<*> ?: return@forEach
                    val lastMessage   = doc.getString("lastMessage")   ?: "Tap to chat"
                    val convId        = doc.id
                    val otherUid      = participants.firstOrNull { it != uid }?.toString() ?: return@forEach

                    // ── Get the real name stored in the conversation doc ──────
                    // otherName_{myUid} stores what I see as the other person's name
                    val otherName = doc.getString("otherName_$uid")
                        ?: doc.getString("otherName_$otherUid")
                        ?: "Unknown"

                    addConversationRow(container, convId, otherUid, otherName, lastMessage)
                }
            }
    }

    private fun addConversationRow(
        container: LinearLayout,
        convId: String,
        otherUid: String,
        otherName: String,
        lastMessage: String
    ) {
        val initials = otherName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2).joinToString("").uppercase()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 28, 42, 28)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true; isFocusable = true
            setBackgroundResource(android.R.color.transparent)
        }

        val sz = (46 * resources.displayMetrics.density).toInt()
        val avatar = TextView(this).apply {
            text = initials; textSize = 14f; gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_dark, null))
            setBackgroundResource(R.drawable.bg_avatar)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (14 * resources.displayMetrics.density).toInt() }
        }

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        col.addView(TextView(this).apply {
            text = otherName; textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
        })
        col.addView(TextView(this).apply {
            text = lastMessage; textSize = 12f; maxLines = 1
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 2 }
        })

        row.addView(avatar); row.addView(col)

        row.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("receiverId",     otherUid)
                putExtra("receiverName",   otherName)
                putExtra("conversationId", convId)
            })
        }

        container.addView(row)

        // Divider
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })
    }

    override fun onDestroy() { super.onDestroy(); listener?.remove() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_messages
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val home = if (myRole == "Landlord") LandlordHomeActivity::class.java else HomeActivity::class.java
                    startActivity(Intent(this, home)); overridePendingTransition(0,0); true
                }
                R.id.nav_requests -> {
                    val req = if (myRole == "Landlord") LandlordMaintenanceActivity::class.java else MaintenanceActivity::class.java
                    startActivity(Intent(this, req)); overridePendingTransition(0,0); true
                }
                R.id.nav_payments -> {
                    val pay = if (myRole == "Landlord") LandlordPaymentsActivity::class.java else PaymentsActivity::class.java
                    startActivity(Intent(this, pay)); overridePendingTransition(0,0); true
                }
                R.id.nav_messages -> true
                else -> false
            }
        }
    }
}
