package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private var passwordVisible = false
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupPasswordToggle()
        setupEmailValidation()
        setupClickListeners()
    }

    private fun setupPasswordToggle() {
        val etPass = try { findViewById<EditText>(R.id.etPassword)   } catch (e: Exception) { return }
        val tvShow = try { findViewById<TextView>(R.id.tvShowPassword) } catch (e: Exception) { return }

        tvShow.setOnClickListener {
            passwordVisible = !passwordVisible
            etPass.inputType = if (passwordVisible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPass.setSelection(etPass.text.length)
            tvShow.text = if (passwordVisible) "Hide" else "Show"
        }
    }

    private fun setupEmailValidation() {
        val etEmail = try { findViewById<EditText>(R.id.etEmail)     } catch (e: Exception) { return }
        val tvErr   = try { findViewById<TextView>(R.id.tvEmailError) } catch (e: Exception) { return }

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val email = s.toString().trim()
                if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tvErr.text = "Invalid email address"; tvErr.visibility = View.VISIBLE
                } else {
                    tvErr.visibility = View.GONE
                }
            }
        })
    }

    private fun setupClickListeners() {
        // Sign in
        try {
            findViewById<TextView>(R.id.btnSignIn).setOnClickListener {
                val email = email(); val pass = pass()
                when {
                    email.isEmpty() -> showErr("Enter your email")
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showErr("Invalid email address")
                    pass.isEmpty()  -> showErr("Enter your password")
                    pass.length < 6 -> showErr("Password must be at least 6 characters")
                    else -> signIn(email, pass)
                }
            }
        } catch (e: Exception) {}

        // Forgot password — sends a real reset email via Firebase
        try {
            findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
                showForgotPasswordDialog()
            }
        } catch (e: Exception) {}

        // Go to sign up
        try {
            findViewById<TextView>(R.id.tvGoToSignUp).setOnClickListener {
                startActivity(Intent(this, SignUpActivity::class.java))
            }
        } catch (e: Exception) {}
    }

    // ── Sign in ───────────────────────────────────────────────────────────────

    private fun signIn(email: String, password: String) {
        setLoading(true); hideErr()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val role  = doc.getString("role")  ?: "Tenant"
                        val name  = doc.getString("name")  ?: "User"
                        val phone = doc.getString("phone") ?: ""

                        SessionManager.saveSession(this, uid, name, email, role, phone)
                        setLoading(false)

                        // Route based on role + verification status
                        val intent = when {
                            role == "Landlord" -> {
                                val vstatus = doc.getString("verificationStatus") ?: ""
                                when (vstatus) {
                                    "approved" -> Intent(this, LandlordHomeActivity::class.java)
                                    "pending"  -> Intent(this, LandlordPendingActivity::class.java)
                                    "rejected" -> Intent(this, LandlordPendingActivity::class.java)
                                    else       -> Intent(this, LandlordHomeActivity::class.java) // old accounts without status
                                }
                            }
                            else -> Intent(this, HomeActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                    .addOnFailureListener {
                        setLoading(false); showErr("Failed to load profile. Try again.")
                    }
            }
            .addOnFailureListener { error ->
                setLoading(false)
                val msg = error.message ?: ""
                when {
                    msg.contains("no user record",     ignoreCase = true) ||
                    msg.contains("user not found",     ignoreCase = true)  -> showNoAccountDialog(email)
                    msg.contains("invalid credential", ignoreCase = true) ||
                    msg.contains("password",           ignoreCase = true)  -> showErr("Incorrect password. Try again or tap Forgot password.")
                    msg.contains("badly formatted",    ignoreCase = true)  -> showErr("Invalid email address.")
                    msg.contains("too many requests",  ignoreCase = true)  -> showErr("Too many failed attempts. Wait a few minutes and try again.")
                    else -> showErr("Sign in failed: $msg")
                }
            }
    }

    // ── Forgot password — sends real Firebase password reset email ────────────

    private fun showForgotPasswordDialog() {
        val emailInput = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            textSize  = 14f; setPadding(50, 30, 50, 30)
            hint      = "your@email.com"
            // Pre-fill if already typed
            val current = email()
            if (current.isNotEmpty()) setText(current)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset your password")
            .setMessage("Enter the email you signed up with. We will send a link to create a new password.")
            .setView(emailInput)
            .setPositiveButton("Send reset link") { _, _ ->
                val e = emailInput.text.toString().trim()
                when {
                    e.isEmpty() -> toast("Enter your email address")
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> toast("Invalid email address")
                    else -> sendPasswordReset(e)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordReset(email: String) {
        // Firebase sends a real password reset email automatically
        // The link opens a Firebase-hosted page where user sets new password
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                AlertDialog.Builder(this)
                    .setTitle("📧 Reset link sent")
                    .setMessage(
                        "A password reset link has been sent to:\n\n$email\n\n" +
                        "1. Open your email inbox\n" +
                        "2. Tap the link from RentEase (noreply@rentease...)\n" +
                        "3. Set your new password on the page that opens\n" +
                        "4. Come back and sign in with your new password\n\n" +
                        "Check your spam folder if you do not see it."
                    )
                    .setPositiveButton("Open email app") { _, _ ->
                        try {
                            startActivity(Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_EMAIL)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) { toast("Could not open email app") }
                    }
                    .setNegativeButton("OK", null)
                    .show()
            }
            .addOnFailureListener { error ->
                val msg = error.message ?: ""
                toast(when {
                    msg.contains("no user record", ignoreCase = true) ||
                    msg.contains("user not found", ignoreCase = true) ->
                        "No account found with this email. Please sign up first."
                    else -> "Failed to send reset email: $msg"
                })
            }
    }

    // ── No account found dialog ───────────────────────────────────────────────

    private fun showNoAccountDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("No account found")
            .setMessage("No account found for:\n\n$email\n\nWould you like to create a new account?")
            .setPositiveButton("Sign up") { _, _ ->
                startActivity(Intent(this, SignUpActivity::class.java))
            }
            .setNegativeButton("Try again", null)
            .show()
    }

    private fun showErr(msg: String) {
        try {
            val tv = findViewById<TextView>(R.id.tvLoginError)
            tv.text = msg; tv.visibility = View.VISIBLE
        } catch (e: Exception) { toast(msg) }
    }

    private fun hideErr() {
        try { findViewById<TextView>(R.id.tvLoginError).visibility = View.GONE } catch (e: Exception) {}
    }

    private fun setLoading(b: Boolean) {
        try {
            val btn = findViewById<TextView>(R.id.btnSignIn)
            btn.text = if (b) "Signing in..." else "Sign in"
            btn.isEnabled = !b
        } catch (e: Exception) {}
    }

    private fun email() = try { findViewById<EditText>(R.id.etEmail).text.toString().trim() } catch (e: Exception) { "" }
    private fun pass()  = try { findViewById<EditText>(R.id.etPassword).text.toString().trim() } catch (e: Exception) { "" }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
