package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class SignUpActivity : AppCompatActivity() {

    private var selectedRole       = "Tenant"
    private var passwordVisible    = false
    private var confirmPassVisible = false
    private var generatedOtp       = ""
    private var otpTimer: CountDownTimer? = null

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        setupBack(); setupRoleSelector(); setupPasswordToggles(); setupSubmit()
    }

    private fun setupBack() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    private fun setupPasswordToggles() {
        val etPass  = try { findViewById<EditText>(R.id.etPassword)            } catch (e: Exception) { return }
        val etConf  = try { findViewById<EditText>(R.id.etConfirmPassword)     } catch (e: Exception) { return }
        val tvShow  = try { findViewById<TextView>(R.id.tvShowPassword)        } catch (e: Exception) { return }
        val tvShowC = try { findViewById<TextView>(R.id.tvShowConfirmPassword) } catch (e: Exception) { return }

        tvShow.setOnClickListener {
            passwordVisible = !passwordVisible
            etPass.inputType = if (passwordVisible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                               else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPass.setSelection(etPass.text.length); tvShow.text = if (passwordVisible) "Hide" else "Show"
        }
        tvShowC.setOnClickListener {
            confirmPassVisible = !confirmPassVisible
            etConf.inputType = if (confirmPassVisible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                               else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etConf.setSelection(etConf.text.length); tvShowC.text = if (confirmPassVisible) "Hide" else "Show"
        }
    }

    private fun setupRoleSelector() {
        try {
            findViewById<TextView>(R.id.roleTenant).setOnClickListener {
                selectedRole = "Tenant"; setActive(R.id.roleTenant, R.id.roleLandlord)
                try { findViewById<LinearLayout>(R.id.llInviteCode).visibility = android.view.View.VISIBLE } catch (e: Exception) {}
            }
            findViewById<TextView>(R.id.roleLandlord).setOnClickListener {
                selectedRole = "Landlord"; setActive(R.id.roleLandlord, R.id.roleTenant)
                try { findViewById<LinearLayout>(R.id.llInviteCode).visibility = android.view.View.GONE } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    private fun setActive(a: Int, i: Int) {
        try {
            findViewById<TextView>(a).apply { setTextColor(getColor(R.color.blue_dark)); setBackgroundResource(R.drawable.bg_badge_blue) }
            findViewById<TextView>(i).apply { setTextColor(getColor(R.color.text_muted)); setBackgroundResource(R.drawable.bg_stat_card) }
        } catch (e: Exception) {}
    }

    private fun setupSubmit() {
        try {
            findViewById<TextView>(R.id.btnCreateAccount).setOnClickListener {
                val name    = et(R.id.etFullName);   val email = et(R.id.etEmail)
                val phone   = et(R.id.etPhone);      val pass  = et(R.id.etPassword)
                val confirm = et(R.id.etConfirmPassword)
                val code    = try { et(R.id.etInviteCode) } catch (e: Exception) { "" }

                when {
                    name.isEmpty()  -> toast("Enter your full name")
                    email.isEmpty() -> toast("Enter your email address")
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> toast("Invalid email address")
                    phone.isEmpty() -> toast("Enter your phone number")
                    pass.isEmpty()  -> toast("Enter a password")
                    pass.length < 6 -> toast("Password must be at least 6 characters")
                    pass != confirm -> toast("Passwords do not match")
                    selectedRole == "Tenant" && code.isEmpty() -> toast("Enter your invite code from your landlord")
                    else -> sendOtp(name, email, phone, pass, code)
                }
            }
        } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.tvGoToSignIn).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    // ── Send OTP ──────────────────────────────────────────────────────────────

    private fun sendOtp(name: String, email: String, phone: String, pass: String, inviteCode: String) {
        setLoading(true)
        generatedOtp = String.format("%06d", Random.nextInt(100000, 999999))

        db.collection("emailOtps").document(email).set(hashMapOf(
            "otp"       to generatedOtp,
            "email"     to email,
            "name"      to name,
            "expiresAt" to System.currentTimeMillis() + 600000L,
            "createdAt" to System.currentTimeMillis()
        )).addOnSuccessListener {
            FirebaseHelper.sendOtpEmail(email, generatedOtp, name)
            setLoading(false)
            android.util.Log.d("OTP_DEBUG", "OTP for $email: $generatedOtp")
            showOtpDialog(name, email, phone, pass, inviteCode)
        }.addOnFailureListener {
            setLoading(false); toast("Failed to send code. Check connection.")
        }
    }

    // ── OTP dialog ────────────────────────────────────────────────────────────

    private fun showOtpDialog(name: String, email: String, phone: String, pass: String, inviteCode: String) {
        val otpInput = EditText(this).apply {
            hint = "Enter 6-digit code"; textSize = 24f
            inputType = InputType.TYPE_CLASS_NUMBER; maxLines = 1
            setPadding(60, 36, 60, 36); gravity = Gravity.CENTER; letterSpacing = 0.4f
        }
        val timerTv = TextView(this).apply {
            text = "Expires in 10:00"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_muted, null)); setPadding(60, 6, 60, 6)
        }
        val demoTv = TextView(this).apply {
            text = "Demo — your code is: $generatedOtp"; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.blue_primary, null)); setPadding(60, 6, 60, 24)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@SignUpActivity).apply {
                text = "We sent a code to:\n\n$email\n\nEnter it below to verify your email."
                textSize = 13f; gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_muted, null)); setPadding(60, 36, 60, 14)
            })
            addView(otpInput); addView(timerTv); addView(demoTv)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Verify your email")
            .setView(wrapper)
            .setPositiveButton("Verify") { _, _ -> }
            .setNegativeButton("Resend") { _, _ -> otpTimer?.cancel(); sendOtp(name, email, phone, pass, inviteCode) }
            .setCancelable(false).create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val entered = otpInput.text.toString().trim()
            if (entered.isEmpty()) { toast("Enter the code"); return@setOnClickListener }

            db.collection("emailOtps").document(email).get()
                .addOnSuccessListener { doc ->
                    val saved  = doc.getString("otp")     ?: ""
                    val expiry = doc.getLong("expiresAt") ?: 0L
                    when {
                        System.currentTimeMillis() > expiry -> toast("Code expired. Tap Resend.")
                        entered != saved                    -> toast("Incorrect code. Try again.")
                        else -> {
                            db.collection("emailOtps").document(email).delete()
                            otpTimer?.cancel(); dialog.dismiss()
                            if (selectedRole == "Tenant" && inviteCode.isNotEmpty())
                                verifyInviteCode(name, email, phone, pass, inviteCode)
                            else createAccount(name, email, phone, pass, "", "", "")
                        }
                    }
                }.addOnFailureListener { toast("Verification failed.") }
        }

        otpTimer?.cancel()
        otpTimer = object : CountDownTimer(600000, 1000) {
            override fun onTick(ms: Long) { timerTv.text = "Expires in %d:%02d".format(ms / 60000, (ms % 60000) / 1000) }
            override fun onFinish() { timerTv.text = "Code expired."; timerTv.setTextColor(resources.getColor(R.color.red, null)) }
        }.start()
    }

    // ── Verify invite code ────────────────────────────────────────────────────

    private fun verifyInviteCode(name: String, email: String, phone: String, pass: String, code: String) {
        setLoading(true)
        db.collection("inviteCodes").document(code.uppercase()).get()
            .addOnSuccessListener { doc ->
                when {
                    !doc.exists()                         -> { setLoading(false); toast("Invalid invite code.") }
                    doc.getBoolean("used") == true        -> { setLoading(false); toast("This code has already been used.") }
                    System.currentTimeMillis() > (doc.getLong("expiresAt") ?: Long.MAX_VALUE) -> { setLoading(false); toast("Invite code expired.") }
                    else -> createAccount(name, email, phone, pass, code.uppercase(),
                        doc.getString("landlordId") ?: "", doc.getString("unit") ?: "")
                }
            }.addOnFailureListener { setLoading(false); toast("Could not verify: ${it.message}") }
    }

    // ── Create Firebase account ───────────────────────────────────────────────

    private fun createAccount(name: String, email: String, phone: String, pass: String,
                               inviteCode: String, landlordId: String, unit: String) {
        setLoading(true)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                db.collection("users").document(uid).set(hashMapOf(
                    "uid"                to uid,
                    "name"               to name,
                    "email"              to email,
                    "phone"              to phone,
                    "role"               to selectedRole,
                    "unit"               to unit,
                    "landlordId"         to landlordId,
                    "verificationStatus" to if (selectedRole == "Landlord") "pending" else "verified",
                    "verified"           to true,
                    "createdAt"          to System.currentTimeMillis()
                )).addOnSuccessListener {
                    if (inviteCode.isNotEmpty()) {
                        db.collection("inviteCodes").document(inviteCode)
                            .update("used", true, "usedBy", uid, "usedAt", System.currentTimeMillis())
                    }
                    SessionManager.saveSession(this, uid, name, email, selectedRole, phone)
                    setLoading(false)
                    toast("Welcome, $name!")

                    val intent = when (selectedRole) {
                        "Landlord" ->
                            // ── Landlord → building details → verification → pending ──
                            Intent(this, LandlordSignupDetailsActivity::class.java)
                        else ->
                            Intent(this, HomeActivity::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }.addOnFailureListener { setLoading(false); toast("Failed: ${it.message}") }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast(when {
                    e.message?.contains("already in use") == true -> "An account with this email already exists."
                    e.message?.contains("badly formatted") == true -> "Invalid email format."
                    else -> "Sign up failed: ${e.message}"
                })
            }
    }

    override fun onDestroy() { super.onDestroy(); otpTimer?.cancel() }

    private fun et(id: Int) = try { findViewById<EditText>(id).text.toString().trim() } catch (e: Exception) { "" }
    private fun setLoading(b: Boolean) {
        try {
            val btn = findViewById<TextView>(R.id.btnCreateAccount)
            btn.text = if (b) "Please wait..." else "Create account"; btn.isEnabled = !b
        } catch (e: Exception) {}
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
