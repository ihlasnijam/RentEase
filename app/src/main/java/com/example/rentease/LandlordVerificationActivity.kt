package com.example.rentease

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class LandlordVerificationActivity : AppCompatActivity() {

    private val db      = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val myUid   get() = SessionManager.getUid(this)

    private var eidUri:  Uri? = null
    private var deedUri: Uri? = null

    private val eidPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                eidUri = uri
                val name = getFileName(uri)
                try {
                    val tv = findViewById<TextView>(R.id.tvEidFileName)
                    tv.text = "✓ $name"; tv.visibility = View.VISIBLE
                } catch (e: Exception) {}
            }
        }
    }

    private val deedPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                deedUri = uri
                val name = getFileName(uri)
                try {
                    val tv = findViewById<TextView>(R.id.tvDeedFileName)
                    tv.text = "✓ $name"; tv.visibility = View.VISIBLE
                } catch (e: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_verification)

        try { findViewById<LinearLayout>(R.id.btnUploadEid).setOnClickListener  { openPicker(eidPicker)  } } catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnUploadDeed).setOnClickListener { openPicker(deedPicker) } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.btnSubmitVerification).setOnClickListener { submit() } } catch (e: Exception) {}
        try { findViewById<TextView>(R.id.btnSkipForNow).setOnClickListener { goToPending() } } catch (e: Exception) {}
    }

    private fun openPicker(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg","image/png","application/pdf"))
            addCategory(Intent.CATEGORY_OPENABLE)
        })
    }

    private fun submit() {
        val eidNumber = try { findViewById<EditText>(R.id.etEmiratesIdNumber).text.toString().trim() } catch (e: Exception) { "" }
        val address   = try { findViewById<EditText>(R.id.etPropertyAddress).text.toString().trim() }  catch (e: Exception) { "" }

        when {
            eidNumber.isEmpty()      -> toast("Enter your Emirates ID number")
            eidNumber.length < 15    -> toast("Emirates ID must be 15 digits (784-XXXX-XXXXXXX-X)")
            address.isEmpty()        -> toast("Enter your property address")
            eidUri  == null          -> toast("Upload your Emirates ID photo")
            deedUri == null          -> toast("Upload your title deed or RERA certificate")
            else -> uploadAndSave(eidNumber, address)
        }
    }

    private fun uploadAndSave(eidNumber: String, address: String) {
        setLoading(true)

        val uid       = myUid
        val name      = SessionManager.getName(this)
        val email     = SessionManager.getEmail(this)
        var eidUrl    = ""
        var deedUrl   = ""
        var uploaded  = 0

        fun checkDone() {
            if (uploaded < 2) return
            // Both files uploaded — save to Firestore
            val verif = hashMapOf(
                "uid"              to uid,
                "name"             to name,
                "email"            to email,
                "phone"            to SessionManager.getPhone(this),
                "emiratesIdNumber" to eidNumber,
                "propertyAddress"  to address,
                "emiratesIdUrl"    to eidUrl,
                "titleDeedUrl"     to deedUrl,
                "status"           to "pending",
                "submittedAt"      to System.currentTimeMillis(),
                "adminNote"        to ""
            )

            db.collection("landlordVerifications").document(uid).set(verif)
                .addOnSuccessListener {
                    // Update user record
                    db.collection("users").document(uid)
                        .update(mapOf(
                            "verificationStatus" to "pending",
                            "emiratesIdNumber"   to eidNumber,
                            "propertyAddress"    to address
                        ))

                    // Notify admin by email (uses Firebase Trigger Email Extension)
                    FirebaseHelper.notifyAdminLandlordVerification(name, email, eidNumber)

                    setLoading(false)
                    toast("Documents submitted! Admin will review within 24 hours.")
                    goToPending()
                }
                .addOnFailureListener {
                    setLoading(false)
                    toast("Failed to submit: ${it.message}")
                }
        }

        // Upload Emirates ID
        storage.reference.child("verification/$uid/emirates_id_${System.currentTimeMillis()}")
            .putFile(eidUri!!)
            .addOnSuccessListener { task ->
                task.storage.downloadUrl.addOnSuccessListener { url ->
                    eidUrl = url.toString(); uploaded++; checkDone()
                }
            }
            .addOnFailureListener { setLoading(false); toast("Emirates ID upload failed: ${it.message}") }

        // Upload Title Deed
        storage.reference.child("verification/$uid/title_deed_${System.currentTimeMillis()}")
            .putFile(deedUri!!)
            .addOnSuccessListener { task ->
                task.storage.downloadUrl.addOnSuccessListener { url ->
                    deedUrl = url.toString(); uploaded++; checkDone()
                }
            }
            .addOnFailureListener { setLoading(false); toast("Deed upload failed: ${it.message}") }
    }

    private fun goToPending() {
        startActivity(Intent(this, LandlordPendingActivity::class.java))
        finish()
    }

    private fun getFileName(uri: Uri): String {
        var name = "document"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun setLoading(b: Boolean) {
        try {
            val btn = findViewById<TextView>(R.id.btnSubmitVerification)
            btn.text = if (b) "Uploading documents..." else "Submit for verification"
            btn.isEnabled = !b
        } catch (e: Exception) {}
        try {
            findViewById<ProgressBar>(R.id.pbVerification).visibility =
                if (b) View.VISIBLE else View.GONE
        } catch (e: Exception) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
