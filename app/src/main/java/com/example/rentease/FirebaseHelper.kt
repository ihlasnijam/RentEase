package com.example.rentease

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseHelper {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun currentUserId(): String      = auth.currentUser?.uid ?: ""
    fun isLoggedIn(): Boolean        = auth.currentUser != null
    fun signOut()                    = auth.signOut()

    // ── Sign up ───────────────────────────────────────────────────────────────

    fun signUp(
        name: String, email: String, phone: String,
        password: String, role: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else ""
                    val verificationStatus = if (role == "Landlord") "pending" else "verified"
                    val data = hashMapOf(
                        "uid"                to uid,
                        "name"               to name,
                        "email"              to email,
                        "phone"              to phone,
                        "role"               to role,
                        "fcmToken"           to (token ?: ""),
                        "verificationStatus" to verificationStatus,
                        "createdAt"          to System.currentTimeMillis()
                    )
                    db.collection("users").document(uid).set(data)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it.message ?: "Save failed") }
                }
            }
            .addOnFailureListener { onError(it.message ?: "Sign up failed") }
    }

    // ── Sign in ───────────────────────────────────────────────────────────────

    fun signIn(
        email: String, password: String,
        onSuccess: (role: String, name: String) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    db.collection("users").document(uid).update("fcmToken", token)
                }
                getUserData(uid,
                    onSuccess = { data ->
                        val role = data["role"] as? String ?: "Tenant"
                        val name = data["name"] as? String ?: "User"
                        onSuccess(role, name)
                    },
                    onError = onError
                )
            }
            .addOnFailureListener { onError(it.message ?: "Sign in failed") }
    }

    fun getUserData(uid: String, onSuccess: (Map<String, Any>) -> Unit, onError: (String) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) onSuccess(doc.data ?: emptyMap())
                else onError("User not found")
            }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    // ── Notify admin by email when landlord submits verification docs ─────────
    // This saves to Firestore. If you have "Trigger Email" Firebase Extension
    // configured, it will automatically send an email to official.rentease@gmail.com

    fun notifyAdminLandlordVerification(
        landlordName: String,
        landlordEmail: String,
        emiratesId: String
    ) {
        // Save admin notification
        db.collection("adminNotifications").add(hashMapOf(
            "type"          to "landlord_verification",
            "to"            to "official.rentease@gmail.com",
            "subject"       to "New Landlord Verification — $landlordName",
            "message"       to (
                "A new landlord has submitted verification documents.\n\n" +
                "Name: $landlordName\n" +
                "Email: $landlordEmail\n" +
                "Emirates ID: $emiratesId\n\n" +
                "Log in to the admin panel to review and approve."
            ),
            "landlordEmail" to landlordEmail,
            "landlordName"  to landlordName,
            "timestamp"     to System.currentTimeMillis(),
            "read"          to false
        ))

        // Also save as a Firestore "mail" document for Trigger Email Extension
        // The extension watches the "mail" collection and sends real emails
        db.collection("mail").add(hashMapOf(
            "to"      to "official.rentease@gmail.com",
            "message" to hashMapOf(
                "subject" to "🏠 RentEase — New Landlord Verification: $landlordName",
                "text"    to (
                    "New landlord verification request:\n\n" +
                    "Name: $landlordName\n" +
                    "Email: $landlordEmail\n" +
                    "Emirates ID: $emiratesId\n\n" +
                    "Open the admin panel to review their documents and approve or reject.\n\n" +
                    "RentEase Admin"
                )
            )
        ))
    }

    // ── Send OTP notification email ───────────────────────────────────────────
    // Saves OTP to Firestore for Trigger Email Extension to send

    fun sendOtpEmail(email: String, otp: String, name: String) {
        db.collection("mail").add(hashMapOf(
            "to"      to email,
            "message" to hashMapOf(
                "subject" to "Your RentEase verification code: $otp",
                "text"    to (
                    "Hi $name,\n\n" +
                    "Your RentEase email verification code is:\n\n" +
                    "  $otp\n\n" +
                    "This code expires in 10 minutes.\n" +
                    "Do not share this code with anyone.\n\n" +
                    "If you did not request this, ignore this email.\n\n" +
                    "RentEase Team\n" +
                    "official.rentease@gmail.com"
                )
            )
        ))
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    fun submitMaintenanceRequest(
        tenantId: String, tenantName: String, category: String,
        location: String, description: String, priority: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val req = hashMapOf(
            "tenantId"    to tenantId,
            "tenantName"  to tenantName,
            "category"    to category,
            "location"    to location,
            "description" to description,
            "priority"    to priority,
            "status"      to "Open",
            "createdAt"   to System.currentTimeMillis()
        )
        db.collection("maintenanceRequests").add(req)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    fun getTenantRequests(
        tenantId: String,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("maintenanceRequests")
            .whereEqualTo("tenantId", tenantId)
            .get()
            .addOnSuccessListener { docs ->
                val list = docs.documents.map { d ->
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = d.id; m
                }.sortedByDescending { it["createdAt"] as? Long ?: 0L }
                onSuccess(list)
            }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    fun getAllRequests(
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("maintenanceRequests").get()
            .addOnSuccessListener { docs ->
                val list = docs.documents.map { d ->
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = d.id; m
                }.sortedByDescending { it["createdAt"] as? Long ?: 0L }
                onSuccess(list)
            }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    fun getAllTenants(
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("users").whereEqualTo("role", "Tenant").get()
            .addOnSuccessListener { docs ->
                val list = docs.documents.map { d ->
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = d.id; m
                }
                onSuccess(list)
            }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    fun savePayment(
        tenantId: String, tenantName: String,
        amount: String, month: String, status: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        db.collection("payments").add(hashMapOf(
            "tenantId"   to tenantId,
            "tenantName" to tenantName,
            "amount"     to amount,
            "month"      to month,
            "status"     to status,
            "paidAt"     to System.currentTimeMillis()
        )).addOnSuccessListener { onSuccess() }
          .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    fun getTenantPayments(
        tenantId: String,
        onSuccess: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection("payments")
            .whereEqualTo("tenantId", tenantId)
            .get()
            .addOnSuccessListener { docs ->
                val list = docs.documents.map { d ->
                    val m = d.data?.toMutableMap() ?: mutableMapOf()
                    m["id"] = d.id; m
                }.sortedByDescending { it["paidAt"] as? Long ?: 0L }
                onSuccess(list)
            }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun sendMessage(
        senderId: String, senderName: String,
        receiverId: String, message: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val convId = listOf(senderId, receiverId).sorted().joinToString("_")
        db.collection("conversations").document(convId)
            .collection("messages")
            .add(hashMapOf(
                "text"       to message,
                "senderId"   to senderId,
                "senderName" to senderName,
                "receiverId" to receiverId,
                "timestamp"  to System.currentTimeMillis()
            ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed") }
    }
}
