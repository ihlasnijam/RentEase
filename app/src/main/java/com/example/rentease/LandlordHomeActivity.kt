package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LandlordHomeActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_home)
        setupGreeting()
        setupClickListeners()
        setupBottomNav()
        // ── Reset all stats to zero first so nothing is showing while loading ──
        resetAllStatsToZero()
        loadRealStats()
    }

    // ── Reset all to zero before loading — prevents stale data showing ────────

    private fun resetAllStatsToZero() {
        try { findViewById<TextView>(R.id.tvTenantsCount).text     = "0"   } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvTenantsStatCount).text = "0"   } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvOccupancyPct).text     = "0%"  } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvPropertiesCount).text  = "0"   } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvOpenRequestsCount).text = "0"  } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvOpenRequestsChip).text  = "0"  } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvTotalRevenue).text    = "AED 0"  } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvPaymentSummary).text  = "No payments yet" } catch (e: Exception) {}
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        try { findViewById<TextView>(R.id.tvGreeting).text     = greeting                         } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvLandlordName).text = SessionManager.getName(this)    } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvAvatar).text       = SessionManager.getInitials(this) } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvCurrentMonth).text = month                            } catch (e: Exception) {}
    }

    // ── Load all real stats — always starts from zero ─────────────────────────

    private fun loadRealStats() {
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", true)
            .get()
            .addOnSuccessListener { codes ->
                val tenantIds = codes.documents.mapNotNull { it.getString("usedBy") }.filter { it.isNotEmpty() }
                val unitMap   = codes.documents.associate { (it.getString("usedBy") ?: "") to (it.getString("unit") ?: "") }
                val count     = tenantIds.size

                // Update counts — 0 if no tenants
                try { findViewById<TextView>(R.id.tvTenantsCount).text     = count.toString() } catch (e: Exception) {}
                try { findViewById<TextView>(R.id.tvTenantsStatCount).text = count.toString() } catch (e: Exception) {}

                // Occupancy — 0% until tenants exist
                // Uses actual numberOfUnits from Firestore if set, else defaults to count * 1
                db.collection("users").document(myUid).get().addOnSuccessListener { userDoc ->
                    val totalUnits = userDoc.getLong("numberOfUnits")?.toInt() ?: 0
                    val occupancy  = if (totalUnits > 0 && count > 0)
                        "${((count.toFloat() / totalUnits) * 100).toInt()}%"
                    else if (count > 0)
                        "${(count * 10).coerceAtMost(100)}%"
                    else "0%"

                    try { findViewById<TextView>(R.id.tvOccupancyPct).text = occupancy } catch (e: Exception) {}

                    // Properties count
                    val propCount = if (count > 0 || userDoc.contains("buildingName")) 1 else 0
                    try { findViewById<TextView>(R.id.tvPropertiesCount).text = propCount.toString() } catch (e: Exception) {}
                }

                if (tenantIds.isEmpty()) {
                    // Everything is 0 — show empty states
                    showNoTenants()
                    showNoRequests()
                } else {
                    loadTenants(tenantIds, unitMap)
                    loadPaymentStats(tenantIds)
                    loadMaintenanceStats(tenantIds)
                }
            }
            .addOnFailureListener {
                // On failure keep zeros
                showNoTenants()
                showNoRequests()
            }
    }

    private fun loadTenants(tenantIds: List<String>, unitMap: Map<String, String>) {
        db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tenantIds.take(30))
            .get()
            .addOnSuccessListener { docs ->
                val container = try { findViewById<LinearLayout>(R.id.tenantsContainer) } catch (e: Exception) { return@addOnSuccessListener }
                container.removeAllViews()

                if (docs.isEmpty) { showNoTenants(); return@addOnSuccessListener }

                docs.documents.forEach { doc ->
                    val name  = doc.getString("name")  ?: "Unknown"
                    val email = doc.getString("email") ?: ""
                    val phone = doc.getString("phone") ?: ""
                    val uid   = doc.id
                    val unit  = unitMap[uid] ?: ""
                    addTenantRow(container, uid, name, email, phone, unit)
                }
            }
    }

    private fun addTenantRow(container: LinearLayout, uid: String, name: String, email: String, phone: String, unit: String) {
        val sz       = (42 * resources.displayMetrics.density).toInt()
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 28, 42, 28); isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val avatar = TextView(this).apply {
            text = initials; textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_dark, null))
            setBackgroundResource(R.drawable.bg_avatar); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = 28 }
        }

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply { text = name; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(resources.getColor(R.color.navy, null)) })
        col.addView(TextView(this).apply {
            text = if (unit.isNotEmpty()) "$unit · $email" else email
            textSize = 11f; setTextColor(resources.getColor(R.color.text_muted, null))
        })

        val msgBtn = TextView(this).apply {
            text = "Message"; textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_dark, null))
            setBackgroundResource(R.drawable.bg_badge_blue); setPadding(20, 8, 20, 8)
        }

        row.addView(avatar); row.addView(col); row.addView(msgBtn)

        row.setOnClickListener { showTenantDetail(uid, name, email, phone, unit) }
        msgBtn.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("receiverId", uid); putExtra("receiverName", name)
            })
        }

        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })
    }

    private fun showTenantDetail(uid: String, name: String, email: String, phone: String, unit: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage("Unit: ${if (unit.isNotEmpty()) unit else "—"}\nEmail: $email\nPhone: ${if (phone.isNotEmpty()) phone else "—"}")
            .setItems(arrayOf("💬 Send message", "🔧 View requests", "💳 View payments")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ChatActivity::class.java).apply { putExtra("receiverId", uid); putExtra("receiverName", name) })
                    1 -> startActivity(Intent(this, LandlordMaintenanceActivity::class.java))
                    2 -> startActivity(Intent(this, LandlordPaymentsActivity::class.java))
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun showNoTenants() {
        try {
            val c = findViewById<LinearLayout>(R.id.tenantsContainer)
            c.removeAllViews()
            c.addView(TextView(this).apply {
                text = "No tenants yet.\nTap '+ Invite tenant' to add your first tenant."
                textSize = 13f; setTextColor(resources.getColor(R.color.text_muted, null))
                gravity = Gravity.CENTER; setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        } catch (e: Exception) {}
    }

    private fun loadPaymentStats(tenantIds: List<String>) {
        db.collection("payments").whereIn("tenantId", tenantIds.take(30)).get()
            .addOnSuccessListener { docs ->
                var total = 0.0
                docs.forEach { doc -> total += (doc.getString("amount") ?: "0").replace("AED", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0 }
                try { findViewById<TextView>(R.id.tvTotalRevenue).text   = "AED ${String.format("%,.0f", total)}" } catch (e: Exception) {}
                try { findViewById<TextView>(R.id.tvPaymentSummary).text = "${docs.size()} payment(s) received"  } catch (e: Exception) {}
            }
    }

    private fun loadMaintenanceStats(tenantIds: List<String>) {
        db.collection("maintenanceRequests").whereIn("tenantId", tenantIds.take(30)).get()
            .addOnSuccessListener { docs ->
                val openCount = docs.documents.count { (it.getString("status") ?: "Open") != "Resolved" }
                try { findViewById<TextView>(R.id.tvOpenRequestsCount).text = openCount.toString() } catch (e: Exception) {}
                try { findViewById<TextView>(R.id.tvOpenRequestsChip).text  = openCount.toString() } catch (e: Exception) {}

                val container = try { findViewById<LinearLayout>(R.id.recentRequestsContainer) } catch (e: Exception) { return@addOnSuccessListener }
                container.removeAllViews()
                val recent = docs.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }.take(2)

                if (recent.isEmpty()) { showNoRequests(); return@addOnSuccessListener }

                recent.forEach { doc ->
                    val cat    = doc.getString("category")   ?: "Request"
                    val tenant = doc.getString("tenantName") ?: "Tenant"
                    val status = doc.getString("status")     ?: "Open"

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(42, 24, 42, 24); isClickable = true; isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        setOnClickListener { startActivity(Intent(this@LandlordHomeActivity, LandlordMaintenanceActivity::class.java)) }
                    }
                    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    col.addView(TextView(this).apply { text = "$cat — $tenant"; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(resources.getColor(R.color.navy, null)) })
                    val badge = TextView(this).apply {
                        text = status; textSize = 10f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
                        when (status) {
                            "Resolved"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                            "In Progress" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                            else          -> { setTextColor(resources.getColor(R.color.blue_dark,  null)); setBackgroundResource(R.drawable.bg_badge_blue)  }
                        }
                    }
                    row.addView(col); row.addView(badge); container.addView(row)
                    container.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
                        setBackgroundColor(resources.getColor(R.color.border, null))
                    })
                }
            }
    }

    private fun showNoRequests() {
        try {
            val c = findViewById<LinearLayout>(R.id.recentRequestsContainer)
            c.removeAllViews()
            c.addView(TextView(this).apply {
                text = "No maintenance requests yet"; textSize = 13f
                setTextColor(resources.getColor(R.color.text_muted, null))
                gravity = Gravity.CENTER; setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        } catch (e: Exception) {}
    }

    // ── Invite dialog ─────────────────────────────────────────────────────────

    private fun showInviteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Invite a tenant")
            .setItems(arrayOf("Generate new invite code", "View existing unused codes")) { _, which ->
                when (which) {
                    0 -> showGenerateCodeDialog()
                    1 -> showExistingCodes()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showGenerateCodeDialog() {
        val unitInput = EditText(this).apply { hint = "Unit number e.g. Apt 204"; textSize = 14f; setPadding(50, 30, 50, 30) }
        AlertDialog.Builder(this)
            .setTitle("Generate invite code")
            .setMessage("Enter the unit number for this tenant:")
            .setView(unitInput)
            .setPositiveButton("Generate") { _, _ ->
                val unit = unitInput.text.toString().trim()
                if (unit.isEmpty()) { android.widget.Toast.makeText(this, "Enter a unit number", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                generateCode(unit)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun generateCode(unit: String) {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code  = (1..8).map { chars.random() }.joinToString("")
        val exp   = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 1 day

        db.collection("inviteCodes").document(code).set(hashMapOf(
            "code" to code, "landlordId" to myUid,
            "landlordName" to SessionManager.getName(this),
            "unit" to unit, "used" to false,
            "createdAt" to System.currentTimeMillis(), "expiresAt" to exp
        )).addOnSuccessListener {
            val expDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(exp))
            AlertDialog.Builder(this)
                .setTitle("✅ Code created")
                .setMessage("Code for $unit:\n\n     ➤  $code\n\n⏰ Expires: $expDate (24 hours)\nCan only be used once.")
                .setPositiveButton("Copy") { _, _ ->
                    val cb = getSystemService(android.content.ClipboardManager::class.java)
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Code", code))
                    android.widget.Toast.makeText(this, "Copied: $code", android.widget.Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("Share") { _, _ ->
                    val msg = "Your RentEase invite code for $unit:\n\n$code\n\nExpires: $expDate"
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg) }, "Share code"))
                }
                .setCancelable(false).show()
        }.addOnFailureListener {
            android.widget.Toast.makeText(this, "Failed: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExistingCodes() {
        val now = System.currentTimeMillis()
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", false)
            .get()
            .addOnSuccessListener { docs ->
                val valid = docs.documents.filter { (it.getLong("expiresAt") ?: 0L) > now }
                if (valid.isEmpty()) {
                    android.widget.Toast.makeText(this, "No unused valid codes. Generate a new one.", android.widget.Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val sdf   = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                val items = valid.map { doc ->
                    val code   = doc.getString("code") ?: doc.id
                    val unit   = doc.getString("unit") ?: "Unknown unit"
                    val expiry = doc.getLong("expiresAt") ?: 0L
                    "$code — $unit (expires ${sdf.format(java.util.Date(expiry))})"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Unused invite codes")
                    .setItems(items) { _, which ->
                        val code = valid[which].getString("code") ?: valid[which].id
                        val cb   = getSystemService(android.content.ClipboardManager::class.java)
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Code", code))
                        android.widget.Toast.makeText(this, "Copied: $code", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Close", null).show()
            }
    }

    private fun setupClickListeners() {
        try { findViewById<LinearLayout>(R.id.cardTotalProperties).setOnClickListener { startActivity(Intent(this, LandlordPropertiesActivity::class.java)) } } catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.cardOpenRequests).setOnClickListener   { startActivity(Intent(this, LandlordMaintenanceActivity::class.java)) } } catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.cardTenants).setOnClickListener        { /* on home */ } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvAvatar).setOnClickListener               { startActivity(Intent(this, ProfileActivity::class.java)) } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.btnInviteTenant).setOnClickListener        { showInviteDialog() } } catch (e: Exception) {}
    }

    override fun onResume() { super.onResume(); setupGreeting(); resetAllStatsToZero(); loadRealStats() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_home
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> true
                R.id.nav_requests -> { startActivity(Intent(this, LandlordMaintenanceActivity::class.java)); overridePendingTransition(0,0); true }
                R.id.nav_payments -> { startActivity(Intent(this, LandlordPaymentsActivity::class.java));    overridePendingTransition(0,0); true }
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));             overridePendingTransition(0,0); true }
                else -> false
            }
        }
    }
}
