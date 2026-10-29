package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class MaintenanceActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance)
        setupBottomNav()
        setupNewRequestButton()
        loadMyRequests()
    }

    // ── Load ONLY this tenant's requests ──────────────────────────────────────

    private fun loadMyRequests() {
        val myUid = SessionManager.getUid(this)
        if (myUid.isEmpty()) return

        showLoading(true)

        // Query only where tenantId == myUid — no other tenant's data
        db.collection("maintenanceRequests")
            .whereEqualTo("tenantId", myUid)
            .get()
            .addOnSuccessListener { docs ->
                showLoading(false)

                val container = findViewById<LinearLayout>(R.id.requestsContainer)
                container.removeAllViews()

                if (docs.isEmpty) {
                    showEmptyState(container)
                    return@addOnSuccessListener
                }

                // Sort by createdAt descending in memory
                val requests = docs.documents
                    .sortedByDescending { it.getLong("createdAt") ?: 0L }

                requests.forEach { doc ->
                    val category    = doc.getString("category")    ?: "General"
                    val location    = doc.getString("location")    ?: ""
                    val description = doc.getString("description") ?: ""
                    val priority    = doc.getString("priority")    ?: "Medium"
                    val status      = doc.getString("status")      ?: "Open"
                    val createdAt   = doc.getLong("createdAt")     ?: 0L
                    val requestId   = doc.id

                    addRequestRow(container, requestId, category, location, description, priority, status, createdAt)
                }
            }
            .addOnFailureListener { error ->
                showLoading(false)
                Toast.makeText(this, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Empty state when no requests ──────────────────────────────────────────

    private fun showEmptyState(container: LinearLayout) {
        val wrapper = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
        }

        val icon = TextView(this).apply {
            text     = "🔧"
            textSize = 48f
            gravity  = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val title = TextView(this).apply {
            text      = "No requests yet"
            textSize  = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val sub = TextView(this).apply {
            text      = "Tap the button below to report\na maintenance issue"
            textSize  = 13f
            setTextColor(resources.getColor(R.color.text_muted, null))
            gravity   = Gravity.CENTER
        }

        wrapper.addView(icon)
        wrapper.addView(title)
        wrapper.addView(sub)
        container.addView(wrapper)
    }

    // ── Add a single request row dynamically ──────────────────────────────────

    private fun addRequestRow(
        container: LinearLayout,
        requestId: String,
        category: String,
        location: String,
        description: String,
        priority: String,
        status: String,
        createdAt: Long
    ) {
        // Outer card wrapper
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            setPadding(42, 36, 42, 36)
            isClickable = true
            isFocusable = true
        }

        // Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val categoryView = TextView(this).apply {
            text      = "$category — $location"
            textSize  = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Status badge
        val statusBadge = TextView(this).apply {
            text    = status
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 8, 20, 8)
            when (status) {
                "Resolved"    -> { setTextColor(resources.getColor(R.color.green_dark, null));  setBackgroundResource(R.drawable.bg_badge_green)  }
                "In Progress" -> { setTextColor(resources.getColor(R.color.amber_dark, null));  setBackgroundResource(R.drawable.bg_badge_amber)  }
                else          -> { setTextColor(resources.getColor(R.color.blue_dark, null));   setBackgroundResource(R.drawable.bg_badge_blue)   }
            }
        }

        titleRow.addView(categoryView)
        titleRow.addView(statusBadge)

        // Description
        val descView = TextView(this).apply {
            text      = description
            textSize  = 13f
            setTextColor(resources.getColor(R.color.text_muted, null))
            maxLines  = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        // Bottom row — priority + date
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }

        val priorityBadge = TextView(this).apply {
            text     = priority
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(16, 6, 16, 6)
            when (priority) {
                "Urgent" -> { setTextColor(resources.getColor(R.color.red_dark, null));   setBackgroundResource(R.drawable.bg_badge_red)   }
                "Medium" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else     -> { setTextColor(resources.getColor(R.color.text_muted, null)); setBackgroundResource(R.drawable.bg_stat_card)   }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
        }

        val dateView = TextView(this).apply {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            text      = sdf.format(java.util.Date(createdAt))
            textSize  = 11f
            setTextColor(resources.getColor(R.color.text_muted, null))
        }

        bottomRow.addView(priorityBadge)
        bottomRow.addView(dateView)

        card.addView(titleRow)
        card.addView(descView)
        card.addView(bottomRow)

        // Tap to see full details
        card.setOnClickListener {
            showRequestDetails(category, location, description, priority, status)
        }

        container.addView(card)
    }

    private fun showRequestDetails(
        category: String, location: String,
        description: String, priority: String, status: String
    ) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$category — $location")
            .setMessage(
                "Status: $status\n" +
                "Priority: $priority\n\n" +
                "Description:\n$description"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun setupNewRequestButton() {
        try {
            findViewById<TextView>(R.id.btnSubmitRequest).setOnClickListener {
                startActivity(Intent(this, NewRequestActivity::class.java))
            }
        } catch (e: Exception) {}
    }

    private fun showLoading(show: Boolean) {
        try {
            findViewById<View>(R.id.pbLoading).visibility =
                if (show) View.VISIBLE else View.GONE
        } catch (e: Exception) {}
    }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_requests
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    overridePendingTransition(0, 0); true
                }
                R.id.nav_requests -> true
                R.id.nav_payments -> {
                    startActivity(Intent(this, PaymentsActivity::class.java))
                    overridePendingTransition(0, 0); true
                }
                R.id.nav_messages -> {
                    startActivity(Intent(this, MessagesActivity::class.java))
                    overridePendingTransition(0, 0); true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh list when coming back from NewRequestActivity
        loadMyRequests()
    }
}
