package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        loadProfileData()
        setupBackButton()
        setupAccountRows()
        setupPropertyRows()
        setupAppSettings()
        setupSupportRows()
        setupSignOut()
    }

    private fun loadProfileData() {
        val name     = SessionManager.getName(this)
        val email    = SessionManager.getEmail(this)
        val initials = SessionManager.getInitials(this)
        try { findViewById<TextView>(R.id.tvAvatarLarge).text  = initials } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvProfileName).text  = name     } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvProfileEmail).text = email    } catch (e: Exception) {}
    }

    private fun setupBackButton() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    private fun setupAccountRows() {

        // ── Edit Profile ──────────────────────────────────────────────────────
        try {
            findViewById<LinearLayout>(R.id.rowEditProfile).setOnClickListener {
                showEditProfileDialog()
            }
        } catch (e: Exception) {}

        // ── Change Password ───────────────────────────────────────────────────
        try {
            findViewById<LinearLayout>(R.id.rowChangePassword).setOnClickListener {
                showChangePasswordDialog()
            }
        } catch (e: Exception) {}

        // ── Documents ─────────────────────────────────────────────────────────
        try {
            findViewById<LinearLayout>(R.id.rowDocuments).setOnClickListener {
                startActivity(Intent(this, DocumentsActivity::class.java))
            }
        } catch (e: Exception) {}
    }

    // ── Edit profile dialog ───────────────────────────────────────────────────

    private fun showEditProfileDialog() {
        val nameInput = EditText(this).apply {
            hint = "Full name"; textSize = 14f; setPadding(50, 30, 50, 10)
            setText(SessionManager.getName(this@ProfileActivity))
        }
        val phoneInput = EditText(this).apply {
            hint = "Phone number"; textSize = 14f; setPadding(50, 10, 50, 30)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(SessionManager.getPhone(this@ProfileActivity))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput); addView(phoneInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit profile")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName  = nameInput.text.toString().trim()
                val newPhone = phoneInput.text.toString().trim()

                if (newName.isEmpty()) { toast("Name cannot be empty"); return@setPositiveButton }

                val uid = SessionManager.getUid(this)
                db.collection("users").document(uid)
                    .update("name", newName, "phone", newPhone)
                    .addOnSuccessListener {
                        SessionManager.saveSession(this, uid, newName,
                            SessionManager.getEmail(this),
                            SessionManager.getRole(this), newPhone)
                        loadProfileData()
                        toast("Profile updated!")
                    }
                    .addOnFailureListener { toast("Failed: ${it.message}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Change password — requires current password for security ──────────────

    private fun showChangePasswordDialog() {
        val currentPassInput = EditText(this).apply {
            hint = "Current password"; textSize = 14f; setPadding(50, 30, 50, 10)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPassInput = EditText(this).apply {
            hint = "New password (min 6 characters)"; textSize = 14f; setPadding(50, 10, 50, 10)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmPassInput = EditText(this).apply {
            hint = "Confirm new password"; textSize = 14f; setPadding(50, 10, 50, 30)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(currentPassInput); addView(newPassInput); addView(confirmPassInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Change password")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val currentPass = currentPassInput.text.toString().trim()
                val newPass     = newPassInput.text.toString().trim()
                val confirmPass = confirmPassInput.text.toString().trim()

                when {
                    currentPass.isEmpty() -> toast("Enter your current password")
                    newPass.length < 6    -> toast("New password must be at least 6 characters")
                    newPass != confirmPass -> toast("New passwords do not match")
                    newPass == currentPass -> toast("New password must be different from current")
                    else -> changePassword(currentPass, newPass)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user  = auth.currentUser ?: return
        val email = SessionManager.getEmail(this)

        // Re-authenticate first — required by Firebase for security
        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Re-auth OK — now update password
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        AlertDialog.Builder(this)
                            .setTitle("✅ Password changed")
                            .setMessage("Your password has been updated successfully. Use your new password next time you sign in.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    .addOnFailureListener {
                        toast("Failed to update password: ${it.message}")
                    }
            }
            .addOnFailureListener {
                toast("Current password is incorrect. Please try again.")
            }
    }

    private fun setupPropertyRows() {
        try {
            findViewById<LinearLayout>(R.id.rowLease).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Lease details")
                    .setMessage("Property: Al Noor Tower\nUnit: Apt 204\nPeriod: Jan 2025 – Dec 2025\nMonthly rent: AED 4,500\nStatus: Active")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {}

        try {
            findViewById<LinearLayout>(R.id.rowPaymentHistory).setOnClickListener {
                startActivity(Intent(this, PaymentsActivity::class.java))
            }
        } catch (e: Exception) {}
    }

    private fun setupAppSettings() {
        try {
            findViewById<Switch>(R.id.switchNotifications).setOnCheckedChangeListener { _, on ->
                toast(if (on) "Notifications on" else "Notifications off")
            }
        } catch (e: Exception) {}

        try {
            findViewById<Switch>(R.id.switchReminders).setOnCheckedChangeListener { _, on ->
                toast(if (on) "Rent reminders on" else "Rent reminders off")
            }
        } catch (e: Exception) {}

        try {
            findViewById<LinearLayout>(R.id.rowLanguage).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Select language")
                    .setItems(arrayOf("English", "Arabic — العربية")) { _, which ->
                        toast(if (which == 0) "English selected" else "Arabic selected")
                    }
                    .show()
            }
        } catch (e: Exception) {}
    }

    private fun setupSupportRows() {
        try {
            findViewById<LinearLayout>(R.id.rowHelp).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Help")
                    .setMessage("For help: official.rentease@gmail.com\n\nCommon issues:\n• Can't login → use Forgot Password\n• Payment failed → contact landlord\n• App not loading → check internet")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {}

        try {
            findViewById<LinearLayout>(R.id.rowContact).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Contact us")
                    .setMessage("Email: official.rentease@gmail.com\nWe respond within 24 hours.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {}

        try {
            findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
                startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            }
        } catch (e: Exception) {}
    }

    private fun setupSignOut() {
        try {
            findViewById<LinearLayout>(R.id.rowSignOut).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Sign out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Sign out") { _, _ ->
                        auth.signOut()
                        SessionManager.clearSession(this)
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
