package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LandlordPaymentsActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_payments)
        setupBottomNav()
        loadPayments()
    }

    private fun loadPayments() {
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", true)
            .get()
            .addOnSuccessListener { codes ->
                val tenantIds = codes.documents.mapNotNull { it.getString("usedBy") }.filter { it.isNotEmpty() }
                if (tenantIds.isEmpty()) { showEmpty("No tenants yet.\nPayments will appear here when tenants pay rent."); return@addOnSuccessListener }

                db.collection("payments")
                    .whereIn("tenantId", tenantIds.take(30))
                    .get()
                    .addOnSuccessListener { docs ->
                        val container = try { findViewById<LinearLayout>(R.id.paymentsContainer) } catch (e: Exception) { return@addOnSuccessListener }
                        container.removeAllViews()

                        if (docs.isEmpty) { showEmpty("No payments received yet."); return@addOnSuccessListener }

                        val payments = docs.documents.sortedByDescending { it.getLong("paidAt") ?: 0L }
                        var total    = 0.0

                        payments.forEach { doc ->
                            val tenantName = doc.getString("tenantName") ?: "Tenant"
                            val month      = doc.getString("month")      ?: ""
                            val amount     = doc.getString("amount")     ?: "AED 0"
                            val status     = doc.getString("status")     ?: "Paid"
                            val paidAt     = doc.getLong("paidAt")       ?: 0L
                            total += amount.replace("AED", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
                            addPaymentRow(container, tenantName, month, amount, status, paidAt)
                        }

                        try { findViewById<TextView>(R.id.tvTotalCollected).text = "AED ${String.format("%,.0f", total)}" } catch (e: Exception) {}
                        try { findViewById<TextView>(R.id.tvPaymentCount).text   = "${payments.size} payment(s) total"    } catch (e: Exception) {}
                    }
                    .addOnFailureListener { showEmpty("Failed to load payments.") }
            }
            .addOnFailureListener { showEmpty("Failed to load.") }
    }

    private fun addPaymentRow(container: LinearLayout, tenantName: String, month: String, amount: String, status: String, paidAt: Long) {
        val sdf     = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = if (paidAt > 0) sdf.format(Date(paidAt)) else "—"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 32, 42, 32); isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        col.addView(TextView(this).apply { text = tenantName; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(resources.getColor(R.color.navy, null)) })
        col.addView(TextView(this).apply { text = "$month · $dateStr"; textSize = 11f; setTextColor(resources.getColor(R.color.text_muted, null)) })

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        right.addView(TextView(this).apply {
            text = amount; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 }
        })
        right.addView(TextView(this).apply {
            text = status; textSize = 10f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
            when (status) {
                "Paid"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                "Pending" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else      -> { setTextColor(resources.getColor(R.color.red_dark,   null)); setBackgroundResource(R.drawable.bg_badge_red)   }
            }
        })

        row.addView(col); row.addView(right)

        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Payment receipt")
                .setMessage(
                    "════════════════════\n     PAYMENT RECEIPT     \n════════════════════\n\n" +
                    "Tenant:  $tenantName\nMonth:   $month\nAmount:  $amount\nPaid on: $dateStr\nStatus:  ✅ $status\n\n" +
                    "════════════════════\n  RentEase — Smart Rental  \n════════════════════"
                )
                .setPositiveButton("Close", null)
                .setNeutralButton("Share") { _, _ ->
                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Payment from $tenantName — $month — $amount — $dateStr") }
                    startActivity(Intent.createChooser(share, "Share receipt"))
                }
                .show()
        }

        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })
    }

    private fun showEmpty(msg: String) {
        val c = try { findViewById<LinearLayout>(R.id.paymentsContainer) } catch (e: Exception) { return }
        c.removeAllViews()
        c.addView(TextView(this).apply {
            text = msg; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, 60, 0, 60)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onResume() { super.onResume(); loadPayments() }

    // ── All nav routes correctly ──────────────────────────────────────────────
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_payments
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { startActivity(Intent(this, LandlordHomeActivity::class.java));        overridePendingTransition(0, 0); true }
                R.id.nav_requests -> { startActivity(Intent(this, LandlordMaintenanceActivity::class.java)); overridePendingTransition(0, 0); true }
                R.id.nav_payments -> true
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));             overridePendingTransition(0, 0); true }
                else -> false
            }
        }
    }
}
