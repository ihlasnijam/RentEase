package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LandlordTenantsActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_tenants)
        setupBackButton()
        setupInviteButton()
        setupBottomNav()
        loadMyTenants()
    }

    private fun setupBackButton() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    private fun setupInviteButton() {
        try {
            findViewById<TextView>(R.id.btnInviteTenant).setOnClickListener {
                showInviteOptions()
            }
        } catch (e: Exception) {}
    }

    private fun showInviteOptions() {
        AlertDialog.Builder(this)
            .setTitle("Invite a tenant")
            .setItems(arrayOf(
                "Generate new invite code",
                "View existing unused codes"
            )) { _, which ->
                when (which) {
                    0 -> showGenerateCodeDialog()
                    1 -> showExistingCodes()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGenerateCodeDialog() {
        val unitInput = EditText(this).apply {
            hint = "Unit number e.g. Apt 204"
            textSize = 14f
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Generate invite code")
            .setMessage("Enter the unit number for this tenant:")
            .setView(unitInput)
            .setPositiveButton("Generate") { _, _ ->
                val unit = unitInput.text.toString().trim()
                if (unit.isEmpty()) {
                    Toast.makeText(this, "Enter a unit number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                generateCode(unit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateCode(unit: String) {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code  = (1..8).map { chars.random() }.joinToString("")

        // ── EXPIRY SET TO 1 DAY ──────────────────────────────────────────────
        val expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 1 day

        db.collection("inviteCodes").document(code).set(hashMapOf(
            "code"         to code,
            "landlordId"   to myUid,
            "landlordName" to SessionManager.getName(this),
            "unit"         to unit,
            "used"         to false,
            "createdAt"    to System.currentTimeMillis(),
            "expiresAt"    to expiresAt
        )).addOnSuccessListener {
            val sdf     = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            val expDate = sdf.format(Date(expiresAt))

            AlertDialog.Builder(this)
                .setTitle("✅ Invite code created")
                .setMessage(
                    "Code for $unit:\n\n" +
                    "     ➤  $code\n\n" +
                    "⏰ Expires: $expDate (24 hours)\n" +
                    "• Can only be used once\n" +
                    "• Tenant enters this during sign up"
                )
                .setPositiveButton("Copy code") { _, _ ->
                    val cb = getSystemService(android.content.ClipboardManager::class.java)
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Code", code))
                    Toast.makeText(this, "Code copied: $code", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton("Share") { _, _ ->
                    val msg = "Your RentEase invite code for $unit:\n\n$code\n\n" +
                              "Sign up as Tenant and enter this code.\n⏰ Expires: $expDate (24 hours only)"
                    startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, msg)
                        }, "Share invite code"
                    ))
                }
                .setCancelable(false)
                .show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExistingCodes() {
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", false)
            .get()
            .addOnSuccessListener { docs ->
                // Filter out expired codes
                val now   = System.currentTimeMillis()
                val valid = docs.documents.filter { (it.getLong("expiresAt") ?: 0L) > now }

                if (valid.isEmpty()) {
                    Toast.makeText(this, "No unused valid codes. Generate a new one.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val sdf   = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                val items = valid.map { doc ->
                    val code    = doc.getString("code") ?: doc.id
                    val unit    = doc.getString("unit") ?: "Unknown unit"
                    val expiry  = doc.getLong("expiresAt") ?: 0L
                    "$code — $unit (expires ${sdf.format(Date(expiry))})"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Unused invite codes")
                    .setItems(items) { _, which ->
                        val code = valid[which].getString("code") ?: valid[which].id
                        val cb   = getSystemService(android.content.ClipboardManager::class.java)
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Code", code))
                        Toast.makeText(this, "Copied: $code", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
    }

    private fun loadMyTenants() {
        db.collection("inviteCodes")
            .whereEqualTo("landlordId", myUid)
            .whereEqualTo("used", true)
            .get()
            .addOnSuccessListener { codes ->
                val tenantIds = codes.documents.mapNotNull { it.getString("usedBy") }.filter { it.isNotEmpty() }
                val unitMap   = codes.documents.associate { (it.getString("usedBy") ?: "") to (it.getString("unit") ?: "") }

                if (tenantIds.isEmpty()) { showEmptyTenants(); return@addOnSuccessListener }

                db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tenantIds.take(30))
                    .get()
                    .addOnSuccessListener { userDocs ->
                        val container = try { findViewById<LinearLayout>(R.id.tenantsContainer) }
                        catch (e: Exception) { return@addOnSuccessListener }
                        container.removeAllViews()

                        if (userDocs.isEmpty) { showEmptyTenants(); return@addOnSuccessListener }

                        userDocs.documents.forEach { doc ->
                            val name  = doc.getString("name")  ?: "Unknown"
                            val email = doc.getString("email") ?: ""
                            val phone = doc.getString("phone") ?: ""
                            val uid   = doc.id
                            val unit  = unitMap[uid] ?: ""
                            addTenantRow(container, uid, name, email, phone, unit)
                        }
                    }
            }
            .addOnFailureListener { showEmptyTenants() }
    }

    private fun addTenantRow(container: LinearLayout, uid: String, name: String, email: String, phone: String, unit: String) {
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        val sz = (42 * resources.displayMetrics.density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 30, 42, 30); isClickable = true; isFocusable = true
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
        col.addView(TextView(this).apply { text = name; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(resources.getColor(R.color.navy, null)) })
        col.addView(TextView(this).apply { text = if (unit.isNotEmpty()) "$unit · $email" else email; textSize = 11f; setTextColor(resources.getColor(R.color.text_muted, null)) })

        row.addView(avatar); row.addView(col)

        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("Unit: ${if (unit.isNotEmpty()) unit else "—"}\nEmail: $email\nPhone: ${if (phone.isNotEmpty()) phone else "—"}")
                .setItems(arrayOf("💬 Send message", "🔧 View requests", "💳 View payments")) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, ChatActivity::class.java).apply {
                                putExtra("receiverId", uid); putExtra("receiverName", name)
                            })
                        1 -> startActivity(Intent(this, LandlordMaintenanceActivity::class.java))
                        2 -> startActivity(Intent(this, LandlordPaymentsActivity::class.java))
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }

        container.addView(row)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(42, 0, 42, 0) }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })
    }

    private fun showEmptyTenants() {
        val container = try { findViewById<LinearLayout>(R.id.tenantsContainer) } catch (e: Exception) { return }
        container.removeAllViews()
        container.addView(TextView(this).apply {
            text = "No tenants yet.\nTap '+ Invite' to add your first tenant."
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, 60, 0, 60)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onResume() { super.onResume(); loadMyTenants() }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> { startActivity(Intent(this, LandlordHomeActivity::class.java));        overridePendingTransition(0,0); true }
                R.id.nav_requests -> { startActivity(Intent(this, LandlordMaintenanceActivity::class.java)); overridePendingTransition(0,0); true }
                R.id.nav_payments -> { startActivity(Intent(this, LandlordPaymentsActivity::class.java));    overridePendingTransition(0,0); true }
                R.id.nav_messages -> { startActivity(Intent(this, MessagesActivity::class.java));             overridePendingTransition(0,0); true }
                else -> false
            }
        }
    }
}
