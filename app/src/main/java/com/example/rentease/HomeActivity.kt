package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setupGreeting()
        setupClickListeners()
        setupBottomNav()
        loadAllRealData()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11  -> "Good morning"
            in 12..16 -> "Good afternoon"
            else      -> "Good evening"
        }
        try { findViewById<TextView>(R.id.tvGreeting).text   = greeting                         } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvTenantName).text = SessionManager.getName(this)     } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvAvatar).text     = SessionManager.getInitials(this) } catch (e: Exception) {}
    }

    private fun loadAllRealData() {
        val uid = myUid
        if (uid.isEmpty()) return

        // Load unit + building info
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val unit         = doc.getString("unit")         ?: ""
                val landlordId   = doc.getString("landlordId")   ?: ""
                val buildingName = doc.getString("buildingName") ?: ""
                val area         = doc.getString("area")         ?: ""
                buildLocationLine(unit, landlordId, buildingName, area)
            }

        loadPaymentStats(uid)
        loadMaintenanceStats(uid)
    }

    private fun buildLocationLine(unit: String, landlordId: String, buildingName: String, area: String) {
        if (buildingName.isNotEmpty()) {
            val unitPart = if (unit.isNotEmpty()) "$unit · " else ""
            val areaPart = if (area.isNotEmpty()) ", $area" else ""
            try { findViewById<TextView>(R.id.tvUnitBuilding).text = "$unitPart$buildingName$areaPart" } catch (e: Exception) {}
            return
        }
        if (landlordId.isNotEmpty()) {
            db.collection("users").document(landlordId).get()
                .addOnSuccessListener { doc ->
                    val lb = doc.getString("buildingName") ?: ""
                    val la = doc.getString("area")         ?: ""
                    val unitPart = if (unit.isNotEmpty()) "$unit · " else ""
                    val line = when {
                        lb.isNotEmpty() -> "$unitPart$lb${if (la.isNotEmpty()) ", $la" else ""}"
                        unit.isNotEmpty() -> unit
                        else -> "My apartment"
                    }
                    try { findViewById<TextView>(R.id.tvUnitBuilding).text = line } catch (e: Exception) {}
                }
        } else if (unit.isNotEmpty()) {
            try { findViewById<TextView>(R.id.tvUnitBuilding).text = unit } catch (e: Exception) {}
        }
    }

    private fun loadPaymentStats(uid: String) {
        val currentMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(Calendar.getInstance().time)

        db.collection("payments").whereEqualTo("tenantId", uid).get()
            .addOnSuccessListener { docs ->
                val totalPaid     = docs.size()
                val paidThisMonth = docs.documents.any {
                    it.getString("month") == currentMonthLabel && it.getString("status") == "Paid"
                }
                try { findViewById<TextView>(R.id.tvMonthsPaid).text = totalPaid.toString() } catch (e: Exception) {}

                if (paidThisMonth) {
                    val nextCal   = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                    val nextMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(nextCal.time)
                    try {
                        findViewById<TextView>(R.id.tvDueLabel).text  = "✅ $currentMonthLabel paid"
                        findViewById<TextView>(R.id.tvRentMonth).text = nextMonth
                        findViewById<TextView>(R.id.tvPayNowLabel).text = "Pay next →"
                        findViewById<TextView>(R.id.tvPayNowSub).text   = "Due $nextMonth"
                        findViewById<TextView>(R.id.tvPaymentStatusBadge).text = "Up to date ✓"
                    } catch (e: Exception) {}
                } else {
                    val cal      = Calendar.getInstance()
                    val lastDay  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val today    = cal.get(Calendar.DAY_OF_MONTH)
                    val daysLeft = lastDay - today
                    val dueText  = if (daysLeft == 0) "Rent due today!" else "Rent due in $daysLeft days"
                    try {
                        findViewById<TextView>(R.id.tvDueLabel).text  = dueText
                        findViewById<TextView>(R.id.tvRentMonth).text = currentMonthLabel
                        val badge = findViewById<TextView>(R.id.tvPaymentStatusBadge)
                        badge.text = if (daysLeft <= 3) "Due soon!" else "Due this month"
                        badge.setTextColor(resources.getColor(R.color.amber_dark, null))
                        badge.setBackgroundResource(R.drawable.bg_badge_amber)
                    } catch (e: Exception) {}
                }
            }
            .addOnFailureListener {
                try {
                    val m = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    findViewById<TextView>(R.id.tvDueLabel).text  = "Rent due this month"
                    findViewById<TextView>(R.id.tvRentMonth).text = m
                    findViewById<TextView>(R.id.tvMonthsPaid).text = "0"
                } catch (e: Exception) {}
            }
    }

    private fun loadMaintenanceStats(uid: String) {
        db.collection("maintenanceRequests").whereEqualTo("tenantId", uid).get()
            .addOnSuccessListener { docs ->
                val openCount       = docs.documents.count { (it.getString("status") ?: "Open") != "Resolved" }
                val inProgressCount = docs.documents.count { it.getString("status") == "In Progress" }
                try { findViewById<TextView>(R.id.tvOpenCount).text       = openCount.toString()       } catch (e: Exception) {}
                try { findViewById<TextView>(R.id.tvInProgressBadge).text = "$inProgressCount in progress" } catch (e: Exception) {}
                buildMaintenanceRows(docs.documents.sortedByDescending { it.getLong("createdAt") ?: 0L })
            }
    }

    private fun buildMaintenanceRows(requests: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val container = try { findViewById<LinearLayout>(R.id.recentMaintenanceContainer) } catch (e: Exception) { return }
        container.removeAllViews()

        if (requests.isEmpty()) {
            container.addView(TextView(this).apply {
                text     = "No maintenance requests yet.\nTap 'Report issue' to submit one."
                textSize = 12f; gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_muted, null))
                setPadding(32, 40, 32, 40)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }

        requests.take(2).forEachIndexed { idx, doc ->
            val category  = doc.getString("category")      ?: "General"
            val location  = doc.getString("location")      ?: ""
            val status    = doc.getString("status")        ?: "Open"
            val vendor    = doc.getString("assignedVendor")?: ""
            val createdAt = doc.getLong("createdAt")       ?: 0L
            addMaintenanceRow(container, category, location, status, vendor, createdAt)
            if (idx == 0 && requests.size > 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(56, 0, 14, 0) }
                    setBackgroundColor(resources.getColor(R.color.border, null))
                })
            }
        }
    }

    private fun addMaintenanceRow(
        container: LinearLayout, category: String, location: String,
        status: String, vendor: String, createdAt: Long
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 14, 14, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true; isFocusable = true
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener { startActivity(Intent(this@HomeActivity, MaintenanceActivity::class.java)) }
        }

        val iconBg = when (status) {
            "Resolved"    -> R.drawable.bg_icon_green
            "In Progress" -> R.drawable.bg_icon_amber
            else          -> R.drawable.bg_icon_blue
        }
        val icon = when {
            category.contains("AC", ignoreCase = true) ||
            category.contains("HVAC", ignoreCase = true)     -> "❄️"
            category.contains("Plumbing", ignoreCase = true) ||
            category.contains("Water", ignoreCase = true)    -> "🚿"
            category.contains("Electrical", ignoreCase = true) -> "⚡"
            else -> "🔧"
        }

        val sz = (40 * resources.displayMetrics.density).toInt()
        row.addView(TextView(this).apply {
            text = icon; textSize = 18f; gravity = Gravity.CENTER
            setBackgroundResource(iconBg)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = (12 * resources.displayMetrics.density).toInt() }
        })

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = "$category — $location"; textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
        })
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        val dateStr = if (createdAt > 0) sdf.format(java.util.Date(createdAt)) else ""
        col.addView(TextView(this).apply {
            text = when {
                vendor.isNotEmpty()    -> "$vendor assigned"
                dateStr.isNotEmpty()   -> "Submitted $dateStr"
                else                   -> status
            }
            textSize = 11f; setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 2 }
        })

        if (status == "In Progress") {
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = 40
                progressDrawable.setColorFilter(resources.getColor(R.color.amber, null), android.graphics.PorterDuff.Mode.SRC_IN)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8).apply { topMargin = 8 }
            }
            col.addView(pb)
        }
        row.addView(col)

        row.addView(TextView(this).apply {
            text = status; textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(22, 8, 22, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
            }
            when (status) {
                "Resolved"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                "In Progress" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else          -> { setTextColor(resources.getColor(R.color.blue_dark,  null)); setBackgroundResource(R.drawable.bg_badge_blue)  }
            }
        })

        container.addView(row)
    }

    // ── ALL click listeners ───────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Hero card pay now
        try { findViewById<LinearLayout>(R.id.chipPayNow).setOnClickListener    { startActivity(Intent(this, PaymentsActivity::class.java))    } } catch (e: Exception) {}
        // Metric tiles
        try { findViewById<LinearLayout>(R.id.cardRequests).setOnClickListener  { startActivity(Intent(this, MaintenanceActivity::class.java)) } } catch (e: Exception) {}
        // Quick actions
        try { findViewById<LinearLayout>(R.id.qaReportIssue).setOnClickListener { startActivity(Intent(this, NewRequestActivity::class.java))  } } catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.qaPayRent).setOnClickListener     { startActivity(Intent(this, PaymentsActivity::class.java))    } } catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.qaDocuments).setOnClickListener   { startActivity(Intent(this, DocumentsActivity::class.java))   } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvAvatar).setOnClickListener          { startActivity(Intent(this, ProfileActivity::class.java))     } } catch (e: Exception) {}

        // ── Rent & Bills button ──────────────────────────────────────────────
        try {
            findViewById<LinearLayout>(R.id.qaRentSplit).setOnClickListener {
                startActivity(Intent(this, RentSplittingActivity::class.java))
            }
        } catch (e: Exception) {}

        // Message — landlord chat
        try {
            findViewById<LinearLayout>(R.id.qaMessage).setOnClickListener {
                openLandlordChat()
            }
        } catch (e: Exception) {}
    }

    private fun openLandlordChat() {
        val uid = myUid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val landlordId = doc.getString("landlordId") ?: ""
                if (landlordId.isNotEmpty()) {
                    db.collection("users").document(landlordId).get()
                        .addOnSuccessListener { ld ->
                            openChat(landlordId, ld.getString("name") ?: "Landlord")
                        }
                        .addOnFailureListener { openChat(landlordId, "Landlord") }
                } else {
                    db.collection("inviteCodes").whereEqualTo("usedBy", uid).limit(1).get()
                        .addOnSuccessListener { codes ->
                            if (!codes.isEmpty) {
                                val lId = codes.documents[0].getString("landlordId") ?: ""
                                if (lId.isNotEmpty()) {
                                    db.collection("users").document(lId).get()
                                        .addOnSuccessListener { ld -> openChat(lId, ld.getString("name") ?: "Landlord") }
                                        .addOnFailureListener { openChat(lId, "Landlord") }
                                    return@addOnSuccessListener
                                }
                            }
                            startActivity(Intent(this, MessagesActivity::class.java))
                        }
                        .addOnFailureListener { startActivity(Intent(this, MessagesActivity::class.java)) }
                }
            }
            .addOnFailureListener { startActivity(Intent(this, MessagesActivity::class.java)) }
    }

    private fun openChat(landlordId: String, name: String) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("receiverId", landlordId); putExtra("receiverName", name)
        })
    }

    override fun onResume() { super.onResume(); setupGreeting(); loadAllRealData() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_home
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> true
                R.id.nav_requests -> { startActivity(Intent(this, MaintenanceActivity::class.java)); overridePendingTransition(0,0); true }
                R.id.nav_payments -> { startActivity(Intent(this, PaymentsActivity::class.java));    overridePendingTransition(0,0); true }
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));    overridePendingTransition(0,0); true }
                else -> false
            }
        }
    }
}
