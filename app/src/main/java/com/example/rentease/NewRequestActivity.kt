package com.example.rentease

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class NewRequestActivity : AppCompatActivity() {

    // ── Multi-category selection ──────────────────────────────────────────────
    private val selectedCategories = mutableSetOf<String>()   // up to 2
    private var selectedPriority   = "Medium"
    private var selectedImageUri: Uri? = null

    private val db      = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Category button map: id → label
    private val categoryMap = mapOf(
        R.id.catPlumbing   to "Plumbing",
        R.id.catElectrical to "Electrical",
        R.id.catHVAC       to "HVAC / AC",
        R.id.catAppliances to "Appliances",
        R.id.catStructural to "Structural",
        R.id.catOther      to "Other"
    )

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_request)
        setupBack()
        setupLocationSpinner()
        setupCategoryButtons()
        setupPriorityButtons()
        setupImagePicker()
        setupSubmitButton()
    }

    private fun setupBack() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    private fun setupLocationSpinner() {
        val locs    = listOf("Bathroom", "Kitchen", "Bedroom", "Living room", "Hallway", "Balcony", "Parking", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        try { findViewById<Spinner>(R.id.spinnerLocation).adapter = adapter } catch (e: Exception) {}
    }

    // ── Category buttons — allow up to 2 selections ───────────────────────────

    private fun setupCategoryButtons() {
        categoryMap.forEach { (id, name) ->
            try {
                val btn = findViewById<TextView>(id)
                btn.setOnClickListener {
                    if (selectedCategories.contains(name)) {
                        // Deselect
                        selectedCategories.remove(name)
                        btn.setTextColor(getColor(R.color.text_muted))
                        btn.setBackgroundResource(R.drawable.bg_stat_card)
                    } else {
                        if (selectedCategories.size >= 2) {
                            Toast.makeText(this, "You can select up to 2 categories", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        selectedCategories.add(name)
                        btn.setTextColor(getColor(R.color.blue_dark))
                        btn.setBackgroundResource(R.drawable.bg_badge_blue)
                    }
                    updateCategoryHint()
                }
            } catch (e: Exception) {}
        }
    }

    private fun updateCategoryHint() {
        try {
            val hint = when (selectedCategories.size) {
                0    -> "CATEGORY (select 1 or 2)"
                1    -> "CATEGORY — ${selectedCategories.first()}"
                else -> "CATEGORIES — ${selectedCategories.joinToString(" + ")}"
            }
            findViewById<TextView>(R.id.tvCategoryLabel).text = hint
        } catch (e: Exception) {}
    }

    // ── Priority buttons ──────────────────────────────────────────────────────

    private fun setupPriorityButtons() {
        listOf(R.id.priLow to "Low", R.id.priMedium to "Medium", R.id.priUrgent to "Urgent")
            .forEach { (id, name) ->
                try {
                    findViewById<TextView>(id).setOnClickListener {
                        selectedPriority = name
                        resetPriority()
                        (it as TextView).apply {
                            when (name) {
                                "Urgent" -> { setTextColor(getColor(R.color.red_dark));   setBackgroundResource(R.drawable.bg_badge_red)   }
                                "Medium" -> { setTextColor(getColor(R.color.amber_dark)); setBackgroundResource(R.drawable.bg_badge_amber) }
                                else     -> { setTextColor(getColor(R.color.text_muted)); setBackgroundResource(R.drawable.bg_stat_card)   }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
    }

    private fun resetPriority() {
        listOf(R.id.priLow, R.id.priMedium, R.id.priUrgent).forEach {
            try {
                findViewById<TextView>(it).apply {
                    setTextColor(getColor(R.color.text_muted))
                    setBackgroundResource(R.drawable.bg_stat_card)
                }
            } catch (e: Exception) {}
        }
    }

    // ── Image picker ──────────────────────────────────────────────────────────

    private fun setupImagePicker() {
        try {
            findViewById<LinearLayout>(R.id.uploadBox).setOnClickListener {
                imagePicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                })
            }
        } catch (e: Exception) {}
    }

    private fun showImagePreview(uri: Uri) {
        try {
            val img = findViewById<ImageView>(R.id.imgPreview)
            img.setImageURI(uri); img.visibility = View.VISIBLE
        } catch (e: Exception) {}
        try {
            val label = findViewById<TextView>(R.id.tvUploadLabel)
            label.text = "✓ ${getFileName(uri)} selected"
            label.setTextColor(getColor(R.color.green_dark))
        } catch (e: Exception) {}
    }

    private fun getFileName(uri: Uri): String {
        var name = "image.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) name = c.getString(i)
        }
        return name
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun setupSubmitButton() {
        try {
            findViewById<TextView>(R.id.btnSubmit).setOnClickListener {
                val description = try { findViewById<EditText>(R.id.etDescription).text.toString().trim() } catch (e: Exception) { "" }
                val location    = try { findViewById<Spinner>(R.id.spinnerLocation).selectedItem.toString() } catch (e: Exception) { "Not specified" }

                when {
                    selectedCategories.isEmpty() -> toast("Please select at least one category")
                    description.isEmpty()        -> toast("Please describe the issue")
                    else -> {
                        setLoading(true)
                        if (selectedImageUri != null) uploadImageThenSubmit(description, location)
                        else submitRequest(description, location, null)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun uploadImageThenSubmit(description: String, location: String) {
        val uid  = SessionManager.getUid(this)
        val ref  = storage.reference.child("maintenance_images/$uid/${System.currentTimeMillis()}.jpg")
        ref.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url -> submitRequest(description, location, url.toString()) }
            }
            .addOnFailureListener {
                toast("Photo upload failed — submitting without photo")
                submitRequest(description, location, null)
            }
    }

    private fun submitRequest(description: String, location: String, imageUrl: String?) {
        val uid      = SessionManager.getUid(this)
        val name     = SessionManager.getName(this)

        // Join up to 2 categories with " + "
        val categoryLabel = selectedCategories.joinToString(" + ")

        val data = hashMapOf<String, Any>(
            "tenantId"    to uid,
            "tenantName"  to name,
            "category"    to categoryLabel,
            "location"    to location,
            "description" to description,
            "priority"    to selectedPriority,
            "status"      to "Open",
            "createdAt"   to System.currentTimeMillis()
        )
        if (imageUrl != null) data["imageUrl"] = imageUrl

        db.collection("maintenanceRequests").add(data)
            .addOnSuccessListener {
                setLoading(false)
                notifyLandlords(name, categoryLabel, location, description, selectedPriority)
                toast("Request submitted! Your landlord has been notified.")
                finish()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                toast("Failed: ${e.message}")
            }
    }

    private fun notifyLandlords(
        tenantName: String, category: String, location: String,
        description: String, priority: String
    ) {
        // Notify the landlord linked to this tenant via invite code
        val myUid = SessionManager.getUid(this)
        db.collection("inviteCodes")
            .whereEqualTo("usedBy", myUid)
            .limit(1)
            .get()
            .addOnSuccessListener { codes ->
                if (!codes.isEmpty) {
                    val landlordId = codes.documents[0].getString("landlordId") ?: ""
                    if (landlordId.isNotEmpty()) {
                        db.collection("notifications").add(hashMapOf(
                            "userId"    to landlordId,
                            "title"     to "🔧 New $priority request — $category",
                            "body"      to "$tenantName reported: $description in $location",
                            "type"      to "maintenance_new",
                            "read"      to false,
                            "createdAt" to System.currentTimeMillis()
                        ))
                    }
                }
            }
    }

    private fun setLoading(b: Boolean) {
        try {
            val btn = findViewById<TextView>(R.id.btnSubmit)
            btn.text = if (b) "Submitting..." else "Submit request"; btn.isEnabled = !b
        } catch (e: Exception) {}
        try {
            findViewById<ProgressBar>(R.id.pbSubmit).visibility = if (b) View.VISIBLE else View.GONE
        } catch (e: Exception) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
