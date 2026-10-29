package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var dotStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        animateDots()
        handler.postDelayed({ checkAndRoute() }, 2500)
    }

    private fun checkAndRoute() {
        val auth = FirebaseAuth.getInstance()

        if (!SessionManager.isLoggedIn(this) || auth.currentUser == null) {
            go(LoginActivity::class.java)
            return
        }

        val uid  = SessionManager.getUid(this)
        val role = SessionManager.getRole(this)

        // For landlords — check verification status from Firestore
        if (role == "Landlord") {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val status = doc.getString("verificationStatus") ?: "pending"
                    when (status) {
                        "approved" -> go(LandlordHomeActivity::class.java)
                        "pending"  -> go(LandlordPendingActivity::class.java)
                        else       -> go(LandlordPendingActivity::class.java)
                    }
                }
                .addOnFailureListener {
                    // Network error — let them in anyway
                    go(LandlordHomeActivity::class.java)
                }
        } else {
            go(HomeActivity::class.java)
        }
    }

    private fun go(destination: Class<*>) {
        startActivity(Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun animateDots() {
        val dot1 = try { findViewById<View>(R.id.dot1) } catch (e: Exception) { return }
        val dot2 = try { findViewById<View>(R.id.dot2) } catch (e: Exception) { return }
        val dot3 = try { findViewById<View>(R.id.dot3) } catch (e: Exception) { return }

        val r = object : Runnable {
            override fun run() {
                dotStep = (dotStep + 1) % 3
                dot1.setBackgroundResource(if (dotStep == 0) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
                dot2.setBackgroundResource(if (dotStep == 1) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
                dot3.setBackgroundResource(if (dotStep == 2) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
                handler.postDelayed(this, 400)
            }
        }
        handler.postDelayed(r, 400)
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
