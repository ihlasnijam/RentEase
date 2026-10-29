package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LandlordPendingActivity : AppCompatActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_pending)

        val name = SessionManager.getName(this)
        try { findViewById<TextView>(R.id.tvPendingName).text = "Hi $name — account under review" } catch (e: Exception) {}

        // Check status immediately on open
        checkStatus()

        try {
            findViewById<TextView>(R.id.btnCheckStatus).setOnClickListener {
                it.alpha = 0.6f
                (it as TextView).text = "Checking..."
                checkStatus {
                    it.alpha = 1f
                    it.text  = "Check approval status"
                }
            }
        } catch (e: Exception) {}

        try {
            findViewById<TextView>(R.id.btnSubmitDocs).setOnClickListener {
                startActivity(Intent(this, LandlordVerificationActivity::class.java))
            }
        } catch (e: Exception) {}

        try {
            findViewById<TextView>(R.id.btnSignOut).setOnClickListener {
                auth.signOut()
                SessionManager.clearSession(this)
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        } catch (e: Exception) {}
    }

    // ── Block back button — cannot leave without being approved ───────────────

    override fun onBackPressed() {
        // Do nothing — landlord cannot navigate away until approved
        Toast.makeText(this, "Your account is under review. Please wait for admin approval.", Toast.LENGTH_SHORT).show()
    }

    // ── Check status from Firestore ───────────────────────────────────────────

    private fun checkStatus(onComplete: (() -> Unit)? = null) {
        val uid = SessionManager.getUid(this)
        if (uid.isEmpty()) { onComplete?.invoke(); return }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                onComplete?.invoke()
                val status = doc.getString("verificationStatus") ?: "pending"

                when (status) {
                    "approved" -> {
                        // ── Approved — go to full dashboard ──────────────────
                        Toast.makeText(this, "🎉 Your account has been approved!", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, LandlordHomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }

                    "rejected" -> {
                        // ── Rejected — show reason, keep blocked ──────────────
                        val note = doc.getString("adminNote") ?: "Please contact support."
                        showRejectedState(note)
                    }

                    else -> {
                        // Still pending
                        showPendingState()
                        Toast.makeText(this, "Still under review. We'll notify you by email.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                onComplete?.invoke()
                Toast.makeText(this, "Could not check status. Check your connection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPendingState() {
        try {
            findViewById<TextView>(R.id.tvStatusIcon).text    = "⏳"
            findViewById<TextView>(R.id.tvPendingName).text   = "Account under review"
            findViewById<TextView>(R.id.tvPendingStatus).text =
                "Your account is under review. Our team is verifying your details.\n\n" +
                "This usually takes up to 24 hours.\n\n" +
                "You will receive an email once your account is approved."
            // Blue card
            try {
                findViewById<LinearLayout>(R.id.statusCard)
                    .setBackgroundResource(R.drawable.bg_badge_blue)
            } catch (e: Exception) {}
        } catch (e: Exception) {}
    }

    private fun showRejectedState(reason: String) {
        try {
            findViewById<TextView>(R.id.tvStatusIcon).text    = "❌"
            findViewById<TextView>(R.id.tvPendingName).text   = "Verification rejected"
            findViewById<TextView>(R.id.tvPendingStatus).text =
                "Your verification was not approved.\n\n" +
                "Reason: $reason\n\n" +
                "Please upload updated documents or contact us at:\nofficial.rentease@gmail.com"
            // Red card
            try {
                val card = findViewById<LinearLayout>(R.id.statusCard)
                card.setBackgroundResource(R.drawable.bg_badge_red)
                // Make text dark for red background
                findViewById<TextView>(R.id.tvPendingStatus).setTextColor(
                    resources.getColor(R.color.red_dark, null)
                )
            } catch (e: Exception) {}
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // Always re-check status when coming back to this screen
        checkStatus()
    }
}
