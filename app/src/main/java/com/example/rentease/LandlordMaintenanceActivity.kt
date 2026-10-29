package com.example.rentease

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LandlordMaintenanceActivity : AppCompatActivity() {

    private val db          = FirebaseFirestore.getInstance()
    private val requestList = mutableListOf<Map<String, Any>>()
    private val myUid       get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_maintenance)
        setupBackButton()
        setupBottomNav()
        loadRequests()
    }

    private fun setupBackButton() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD — only this landlord's tenants
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadRequests() {
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", true)
            .get()
            .addOnSuccessListener { codes ->
                val ids = codes.documents.mapNotNull { it.getString("usedBy") }.filter { it.isNotEmpty() }
                if (ids.isEmpty()) {
                    updateStats(0, 0, 0)
                    showEmpty("No tenants yet.\nInvite tenants from the Home screen.")
                } else {
                    fetchRequests(ids)
                }
            }
            .addOnFailureListener { fetchAll() }
    }

    private fun fetchRequests(tenantIds: List<String>) {
        db.collection("maintenanceRequests")
            .whereIn("tenantId", tenantIds.take(30))
            .get()
            .addOnSuccessListener { snap -> buildFromSnap(snap) }
            .addOnFailureListener { fetchAll() }
    }

    private fun fetchAll() {
        db.collection("maintenanceRequests").get()
            .addOnSuccessListener { snap -> buildFromSnap(snap) }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildFromSnap(snap: com.google.firebase.firestore.QuerySnapshot) {
        requestList.clear()
        snap.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
            .forEach { doc ->
                val m = doc.data?.toMutableMap() ?: return@forEach
                m["id"] = doc.id   // ← critical: always store the doc ID
                requestList.add(m)
            }
        val u = requestList.count { it["priority"] == "Urgent" }
        val i = requestList.count { it["status"]   == "In Progress" }
        val r = requestList.count { it["status"]   == "Resolved" }
        updateStats(u, i, r)
        if (requestList.isEmpty()) showEmpty("No maintenance requests from your tenants yet.") else buildList()
    }

    private fun updateStats(u: Int, i: Int, r: Int) {
        try { findViewById<TextView>(R.id.tvUrgentCount).text     = u.toString() } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvInProgressCount).text = i.toString() } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvResolvedCount).text   = r.toString() } catch (e: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BUILD CARD LIST
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildList() {
        val container = try { findViewById<LinearLayout>(R.id.dynamicRequestsContainer) } catch (e: Exception) { return }
        container.removeAllViews()
        requestList.forEach { addCard(container, it) }
    }

    private fun addCard(container: LinearLayout, req: Map<String, Any>) {
        val category       = req["category"]       as? String ?: "General"
        val location       = req["location"]       as? String ?: ""
        val description    = req["description"]    as? String ?: ""
        val priority       = req["priority"]       as? String ?: "Medium"
        val status         = req["status"]         as? String ?: "Open"
        val tenantName     = req["tenantName"]     as? String ?: "Tenant"
        val imageUrl       = req["imageUrl"]       as? String ?: ""
        val requestId      = req["id"]             as? String ?: ""
        val tenantId       = req["tenantId"]       as? String ?: ""
        val assignedVendor = req["assignedVendor"] as? String ?: ""

        if (requestId.isEmpty()) return

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            setPadding(42, 36, 42, 36)
            isClickable = true; isFocusable = true
        }

        // Title + status badge
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        }
        titleRow.addView(TextView(this).apply {
            text = "$category — $location"; textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = status; textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
            when (status) {
                "Resolved"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                "In Progress" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else          -> { setTextColor(resources.getColor(R.color.blue_dark,  null)); setBackgroundResource(R.drawable.bg_badge_blue)  }
            }
        })
        card.addView(titleRow)

        // From tenant
        card.addView(TextView(this).apply {
            text = "From: $tenantName"; textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_primary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        })

        // Description
        card.addView(TextView(this).apply {
            text = description; textSize = 13f; maxLines = 2
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        })

        // Vendor assigned label
        if (assignedVendor.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "👷 Vendor: $assignedVendor"; textSize = 12f
                setTextColor(resources.getColor(R.color.green_dark, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
            })
        }

        // Bottom row: priority + "Tap to assign vendor" prompt
        val botRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        botRow.addView(TextView(this).apply {
            text = priority; textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
            when (priority) {
                "Urgent" -> { setTextColor(resources.getColor(R.color.red_dark,   null)); setBackgroundResource(R.drawable.bg_badge_red)   }
                "Medium" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else     -> { setTextColor(resources.getColor(R.color.text_muted, null)); setBackgroundResource(R.drawable.bg_stat_card)   }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
        })

        // Always show assign prompt so landlord knows to tap
        if (status != "Resolved") {
            botRow.addView(TextView(this).apply {
                text = if (assignedVendor.isEmpty()) "👆 Tap to assign vendor" else "👆 Tap to reassign / update"
                textSize = 11f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.blue_primary, null))
            })
        }

        if (imageUrl.isNotEmpty()) {
            botRow.addView(TextView(this).apply {
                text = "  📷"; textSize = 13f
            })
        }

        card.addView(botRow)

        card.setOnClickListener {
            showAssignDialog(requestId, tenantId, tenantName, category, location, description, priority, status, imageUrl)
        }

        container.addView(card)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASSIGN VENDOR DIALOG
    // ─────────────────────────────────────────────────────────────────────────

    private fun showAssignDialog(
        requestId: String, tenantId: String, tenantName: String,
        category: String, location: String, description: String,
        priority: String, status: String, imageUrl: String
    ) {
        val hasPhoto = imageUrl.isNotEmpty()
        val options  = arrayOf(
            "👷 Ali Hassan — Plumbing",
            "👷 Raj Kumar — Electrical",
            "👷 TechCool LLC — HVAC / AC",
            "👷 Fix-It Fast — General",
            "👷 BuildRight — Structural",
            "🔄 Mark as In Progress",
            "✅ Mark as Resolved",
            if (hasPhoto) "📷 View photo" else "📷 No photo attached"
        )

        AlertDialog.Builder(this)
            .setTitle("$category — $location")
            .setMessage(
                "Tenant: $tenantName\n" +
                "Priority: $priority   Status: $status\n\n" +
                "Description:\n$description\n\n" +
                "Select action:"
            )
            .setItems(options) { _, which ->
                when (which) {
                    0 -> assignVendor(requestId, tenantId, tenantName, "Ali Hassan (Plumbing)",   category, location)
                    1 -> assignVendor(requestId, tenantId, tenantName, "Raj Kumar (Electrical)",  category, location)
                    2 -> assignVendor(requestId, tenantId, tenantName, "TechCool LLC (HVAC/AC)",  category, location)
                    3 -> assignVendor(requestId, tenantId, tenantName, "Fix-It Fast (General)",   category, location)
                    4 -> assignVendor(requestId, tenantId, tenantName, "BuildRight (Structural)", category, location)
                    5 -> updateStatus(requestId, tenantId, "In Progress", category)
                    6 -> updateStatus(requestId, tenantId, "Resolved",    category)
                    7 -> if (hasPhoto) {
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) }
                            catch (e: Exception) { toast("Cannot open photo") }
                         } else toast("No photo attached to this request")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASSIGN VENDOR — uses set(merge=true) so it can't fail on missing fields
    // ─────────────────────────────────────────────────────────────────────────

    private fun assignVendor(
        requestId: String, tenantId: String, tenantName: String,
        vendor: String, category: String, location: String
    ) {
        if (requestId.isBlank()) { toast("Error: request ID missing"); return }

        android.util.Log.d("ASSIGN", "Assigning vendor='$vendor' to requestId='$requestId' tenantId='$tenantId'")

        // Use set with merge — works even if some fields are missing or rules are strict
        db.collection("maintenanceRequests").document(requestId)
            .set(
                mapOf(
                    "assignedVendor" to vendor,
                    "status"         to "In Progress",
                    "assignedAt"     to System.currentTimeMillis(),
                    "assignedBy"     to SessionManager.getName(this)
                ),
                SetOptions.merge()     // ← merge preserves existing fields
            )
            .addOnSuccessListener {
                android.util.Log.d("ASSIGN", "Success!")
                toast("✓ $vendor assigned to $tenantName's request")
                loadRequests()
                notifyTenant(
                    tenantId = tenantId,
                    title    = "🔧 Vendor assigned — $category",
                    body     = "$vendor has been assigned to fix your $category issue in $location. They will contact you soon.",
                    type     = "maintenance_assigned"
                )
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ASSIGN", "FAILED: ${e.message} | code=${e.javaClass.simpleName}")
                toast("Failed to assign: ${e.message}")
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE STATUS
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateStatus(requestId: String, tenantId: String, newStatus: String, category: String) {
        if (requestId.isBlank()) { toast("Error: request ID missing"); return }

        db.collection("maintenanceRequests").document(requestId)
            .set(mapOf("status" to newStatus, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .addOnSuccessListener {
                toast("Marked as $newStatus")
                loadRequests()
                val (title, body) = when (newStatus) {
                    "Resolved"    -> "✅ Issue resolved — $category" to "Your $category request has been fully resolved!"
                    "In Progress" -> "🔧 Work started — $category"  to "Work on your $category issue has started."
                    else          -> "Update" to "Status: $newStatus"
                }
                notifyTenant(tenantId, title, body, "maintenance_update")
            }
            .addOnFailureListener { e -> toast("Failed: ${e.message}") }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFY TENANT
    // ─────────────────────────────────────────────────────────────────────────

    private fun notifyTenant(tenantId: String, title: String, body: String, type: String) {
        if (tenantId.isBlank()) return
        db.collection("notifications").add(
            hashMapOf(
                "userId"    to tenantId,
                "title"     to title,
                "body"      to body,
                "type"      to type,
                "read"      to false,
                "createdAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            android.util.Log.d("NOTIFY", "Notification saved for $tenantId")
        }.addOnFailureListener { e ->
            android.util.Log.e("NOTIFY", "Failed: ${e.message}")
        }

        // Local notification on device (visible immediately for demo)
        try {
            val channelId = "rentease_maint"
            val mgr = getSystemService(android.app.NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                mgr.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "Maintenance", android.app.NotificationManager.IMPORTANCE_HIGH)
                )
            }
            mgr.notify(System.currentTimeMillis().toInt(),
                androidx.core.app.NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_wrench)
                    .setContentTitle(title).setContentText(body)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build()
            )
        } catch (e: Exception) {}
    }

    private fun showEmpty(msg: String) {
        val c = try { findViewById<LinearLayout>(R.id.dynamicRequestsContainer) } catch (e: Exception) { return }
        c.removeAllViews()
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
            addView(TextView(this@LandlordMaintenanceActivity).apply {
                text = "📋"; textSize = 40f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
            })
            addView(TextView(this@LandlordMaintenanceActivity).apply {
                text = msg; textSize = 13f; gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_muted, null))
            })
        })
    }

    override fun onResume() { super.onResume(); loadRequests() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_requests
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { startActivity(Intent(this, LandlordHomeActivity::class.java));        overridePendingTransition(0,0); true }
                R.id.nav_requests -> true
                R.id.nav_payments -> { startActivity(Intent(this, LandlordPaymentsActivity::class.java));    overridePendingTransition(0,0); true }
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));             overridePendingTransition(0,0); true }
                else -> false
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
