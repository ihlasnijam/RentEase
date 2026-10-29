package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PaymentsActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    // sdf helpers
    private val sdfMonth  = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val sdfDate   = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val sdfRow    = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payments)
        setupBottomNav()
        loadPaymentsAndUpdateCard()
    }

    // ── Load payments → decide what to show on the due card ──────────────────

    private fun loadPaymentsAndUpdateCard() {
        val uid = myUid
        if (uid.isEmpty()) {
            showDueCard(paidThisMonth = false, paymentsCount = 0)
            showNoPayments()
            return
        }

        showSpinner(true)
        val container = try { findViewById<LinearLayout>(R.id.paymentsContainer) }
        catch (e: Exception) { return }
        container.removeAllViews()

        db.collection("payments")
            .whereEqualTo("tenantId", uid)
            .get()
            .addOnSuccessListener { docs ->
                showSpinner(false)
                container.removeAllViews()

                val currentMonthLabel = sdfMonth.format(Calendar.getInstance().time)

                // Check if this month is already paid
                val paidThisMonth = docs.documents.any {
                    it.getString("month") == currentMonthLabel && it.getString("status") == "Paid"
                }

                showDueCard(paidThisMonth = paidThisMonth, paymentsCount = docs.size())

                if (docs.isEmpty) {
                    showNoPayments()
                    return@addOnSuccessListener
                }

                val sorted = docs.documents.sortedByDescending { it.getLong("paidAt") ?: 0L }
                sorted.forEach { doc ->
                    addPaymentRow(
                        container,
                        doc.getString("month")  ?: "",
                        doc.getString("amount") ?: "AED 0",
                        doc.getString("status") ?: "Paid",
                        doc.getLong("paidAt")   ?: 0L
                    )
                }
            }
            .addOnFailureListener {
                showSpinner(false)
                container.removeAllViews()
                showDueCard(paidThisMonth = false, paymentsCount = 0)
                showNoPayments()
            }
    }

    // ── Show or hide the DUE card vs PAID card ────────────────────────────────

    private fun showDueCard(paidThisMonth: Boolean, paymentsCount: Int) {
        val now        = Calendar.getInstance()
        val thisMonth  = sdfMonth.format(now.time)

        // Last day of current month as due date
        now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH))
        val dueDate    = sdfDate.format(now.time)

        // Next month label for "paid" sub text
        val nextCal    = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
        val nextMonth  = sdfMonth.format(nextCal.time)
        val nextDue    = sdfDate.format(nextCal.apply { set(Calendar.DAY_OF_MONTH, 1) }.time)

        if (paidThisMonth) {
            // Hide red card, show green paid banner
            try {
                val dueCard = getWindow().decorView.findViewWithTag<androidx.cardview.widget.CardView>("dueCard")
                dueCard?.visibility = View.GONE
            } catch (e: Exception) {}

            // Update red card to show next month instead
            try { findViewById<TextView>(R.id.tvDueMonth).text  = "$nextMonth rent"     } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.tvDueDate).text   = "Due $nextDue"        } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.tvDueAmount).text = "AED 4,500"           } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.btnPayNow).text   = "Pay AED 4,500 now"  } catch (e: Exception) {}

            // Show green paid banner
            try {
                val cardPaid = findViewById<androidx.cardview.widget.CardView>(R.id.cardPaidStatus)
                cardPaid.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvPaidTitle).text = "✅ $thisMonth is paid"
                findViewById<TextView>(R.id.tvPaidSub).text   = "Next payment: AED 4,500 due $nextDue"
            } catch (e: Exception) {}

            // Setup next month pay button
            setupPayNow(nextMonth)
        } else {
            // Show current month due
            try {
                val cardPaid = findViewById<androidx.cardview.widget.CardView>(R.id.cardPaidStatus)
                cardPaid.visibility = View.GONE
            } catch (e: Exception) {}

            try { findViewById<TextView>(R.id.tvDueMonth).text  = "$thisMonth rent"    } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.tvDueDate).text   = "Due $dueDate"       } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.tvDueAmount).text = "AED 4,500"          } catch (e: Exception) {}
            try { findViewById<TextView>(R.id.btnPayNow).text   = "Pay AED 4,500 now" } catch (e: Exception) {}

            setupPayNow(thisMonth)
        }
    }

    private fun setupPayNow(monthLabel: String) {
        try {
            findViewById<TextView>(R.id.btnPayNow).setOnClickListener {
                showPaymentGateway(monthLabel)
            }
        } catch (e: Exception) {}
    }

    // ── Payment gateway ───────────────────────────────────────────────────────

    private fun showPaymentGateway(monthLabel: String) {
        val dialogView = try { layoutInflater.inflate(R.layout.dialog_payment_gateway, null) }
        catch (e: Exception) {
            // Fallback: show simple confirm dialog if layout not found
            AlertDialog.Builder(this)
                .setTitle("Confirm payment")
                .setMessage("Pay AED 4,500 for $monthLabel?")
                .setPositiveButton("Confirm") { _, _ -> processPayment(monthLabel) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
        dialog.show()

        dialogView.findViewById<TextView>(R.id.btnConfirmPayment)?.setOnClickListener {
            val cardNum = dialogView.findViewById<EditText>(R.id.etCardNumber)?.text.toString().trim()
            val expiry  = dialogView.findViewById<EditText>(R.id.etExpiry)?.text.toString().trim()
            val cvv     = dialogView.findViewById<EditText>(R.id.etCVV)?.text.toString().trim()
            val name    = dialogView.findViewById<EditText>(R.id.etCardName)?.text.toString().trim()
            when {
                cardNum.length < 16 -> toast("Enter a valid 16-digit card number")
                expiry.isEmpty()    -> toast("Enter expiry e.g. 12/27")
                cvv.length < 3      -> toast("Enter a valid CVV")
                name.isEmpty()      -> toast("Enter cardholder name")
                else                -> { dialog.dismiss(); processPayment(monthLabel) }
            }
        }
        dialogView.findViewById<TextView>(R.id.btnCancelPayment)?.setOnClickListener { dialog.dismiss() }
    }

    private fun processPayment(monthLabel: String) {
        val procView   = try { layoutInflater.inflate(R.layout.dialog_payment_processing, null) } catch (e: Exception) { null }
        val procDialog = if (procView != null) {
            AlertDialog.Builder(this).setView(procView).setCancelable(false).create().also { it.show() }
        } else null

        Handler(Looper.getMainLooper()).postDelayed({
            procDialog?.dismiss()

            FirebaseHelper.savePayment(
                tenantId   = myUid,
                tenantName = SessionManager.getName(this),
                amount     = "AED 4,500",
                month      = monthLabel,
                status     = "Paid",
                onSuccess  = {
                    // Figure out next month for the success message
                    val nextCal  = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                    val nextMonth = sdfMonth.format(nextCal.time)
                    val nextDue   = sdfDate.format(nextCal.apply { set(Calendar.DAY_OF_MONTH, 1) }.time)

                    AlertDialog.Builder(this)
                        .setTitle("✅ Payment successful!")
                        .setMessage(
                            "AED 4,500 paid for $monthLabel.\n\n" +
                            "Your receipt has been saved.\n\n" +
                            "Next payment: AED 4,500\nDue: $nextDue ($nextMonth)"
                        )
                        .setPositiveButton("Done") { _, _ -> loadPaymentsAndUpdateCard() }
                        .setCancelable(false)
                        .show()
                },
                onError = { loadPaymentsAndUpdateCard() }
            )
        }, 2000)
    }

    // ── Payment history row ───────────────────────────────────────────────────

    private fun addPaymentRow(container: LinearLayout, month: String, amount: String, status: String, paidAt: Long) {
        val dateStr = if (paidAt > 0) sdfRow.format(Date(paidAt)) else "—"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 32, 42, 32); isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = month; textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
        })
        col.addView(TextView(this).apply {
            text = "Paid $dateStr"; textSize = 11f
            setTextColor(resources.getColor(R.color.text_muted, null))
        })

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        right.addView(TextView(this).apply {
            text = amount; textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        })
        right.addView(TextView(this).apply {
            text = status; textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
            when (status) {
                "Paid"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                "Pending" -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                else      -> { setTextColor(resources.getColor(R.color.red_dark,   null)); setBackgroundResource(R.drawable.bg_badge_red)   }
            }
        })

        row.addView(col); row.addView(right)
        row.setOnClickListener {
            if (status == "Paid") showReceipt(month, amount, dateStr)
            else toast("Payment not yet completed")
        }

        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })
    }

    private fun showNoPayments() {
        val c = try { findViewById<LinearLayout>(R.id.paymentsContainer) } catch (e: Exception) { return }
        c.removeAllViews()
        c.addView(TextView(this).apply {
            text = "No payment history yet.\nMake your first payment above."
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, 60, 0, 60)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun showReceipt(month: String, amount: String, dateStr: String) {
        val name = SessionManager.getName(this)
        AlertDialog.Builder(this)
            .setTitle("Payment receipt")
            .setMessage(
                "════════════════════\n     PAYMENT RECEIPT     \n════════════════════\n\n" +
                "Tenant:  $name\nMonth:   $month\nAmount:  $amount\nPaid on: $dateStr\nStatus:  ✅ Paid\n\n" +
                "════════════════════\n  RentEase Platform  \n════════════════════"
            )
            .setPositiveButton("Close", null)
            .setNeutralButton("Share") { _, _ ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Payment Receipt — $month")
                    putExtra(android.content.Intent.EXTRA_TEXT,
                        "RentEase Receipt\nTenant: $name\nMonth: $month\nAmount: $amount\nDate: $dateStr\nStatus: Paid")
                }
                startActivity(android.content.Intent.createChooser(share, "Share receipt"))
            }
            .show()
    }

    private fun showSpinner(show: Boolean) {
        try { findViewById<ProgressBar>(R.id.pbPayments).visibility = if (show) View.VISIBLE else View.GONE }
        catch (e: Exception) {}
    }

    override fun onResume() { super.onResume(); loadPaymentsAndUpdateCard() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_payments
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { startActivity(Intent(this, HomeActivity::class.java));        overridePendingTransition(0,0); true }
                R.id.nav_requests -> { startActivity(Intent(this, MaintenanceActivity::class.java)); overridePendingTransition(0,0); true }
                R.id.nav_payments -> true
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));    overridePendingTransition(0,0); true }
                else -> false
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
