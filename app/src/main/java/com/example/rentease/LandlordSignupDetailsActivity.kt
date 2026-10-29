package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class LandlordSignupDetailsActivity : AppCompatActivity() {

    private val db        = FirebaseFirestore.getInstance()
    private val myUid     get() = SessionManager.getUid(this)
    private var editMode  = false

    private var selectedType      = "Residential"
    private val selectedAmenities = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_signup_details)

        editMode = intent.getBooleanExtra("editMode", false)

        // Update title for edit mode
        if (editMode) {
            try {
                (supportActionBar?.title)
                val title = try { (this as android.app.Activity)
                    .window.decorView.findViewWithTag<TextView>("pageTitle") } catch (e: Exception) { null }
                // Pre-fill existing values
                prefillExistingData()
            } catch (e: Exception) {}
        }

        setupEmirateSpinner()
        setupTypeButtons()
        setupAmenityButtons()
        setupSaveButton()
    }

    private fun prefillExistingData() {
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { doc ->
                try { findViewById<EditText>(R.id.etBuildingName).setText(doc.getString("buildingName") ?: "") } catch (e: Exception) {}
                try { findViewById<EditText>(R.id.etFloors).setText((doc.getLong("numberOfFloors") ?: "").toString().replace("0", "")) } catch (e: Exception) {}
                try { findViewById<EditText>(R.id.etUnits).setText((doc.getLong("numberOfUnits")  ?: "").toString().replace("0", "")) } catch (e: Exception) {}
                try { findViewById<EditText>(R.id.etArea).setText(doc.getString("area") ?: "") } catch (e: Exception) {}

                // Set emirate spinner
                val emirate = doc.getString("emirate") ?: ""
                if (emirate.isNotEmpty()) {
                    val emirates = listOf("Ajman","Dubai","Abu Dhabi","Sharjah","Ras Al Khaimah","Fujairah","Umm Al Quwain")
                    val idx = emirates.indexOf(emirate)
                    if (idx >= 0) try { findViewById<Spinner>(R.id.spinnerEmirate).setSelection(idx) } catch (e: Exception) {}
                }

                // Set property type
                val ptype = doc.getString("propertyType") ?: "Residential"
                selectedType = ptype
                val typeMap = mapOf(
                    R.id.typeResidential to "Residential",
                    R.id.typeCommercial  to "Commercial",
                    R.id.typeMixed       to "Mixed use"
                )
                typeMap.forEach { (id, name) ->
                    try {
                        val btn = findViewById<TextView>(id)
                        if (name == ptype) {
                            btn.setTextColor(getColor(R.color.blue_dark))
                            btn.setBackgroundResource(R.drawable.bg_badge_blue)
                        } else {
                            btn.setTextColor(getColor(R.color.text_muted))
                            btn.setBackgroundResource(R.drawable.bg_stat_card)
                        }
                    } catch (e: Exception) {}
                }

                // Set amenities
                val savedAmenities = doc.get("amenities") as? List<*> ?: emptyList<Any>()
                selectedAmenities.addAll(savedAmenities.map { it.toString() })
                val amenMap = mapOf(
                    R.id.amenParking to "Parking", R.id.amenPool to "Swimming Pool",
                    R.id.amenGym to "Gym", R.id.amenSecurity to "Security",
                    R.id.amenElevator to "Elevator", R.id.amenCCTV to "CCTV",
                    R.id.amenCentral to "Central AC"
                )
                amenMap.forEach { (id, name) ->
                    if (selectedAmenities.contains(name)) {
                        try {
                            findViewById<TextView>(id).apply {
                                setTextColor(getColor(R.color.blue_dark))
                                setBackgroundResource(R.drawable.bg_badge_blue)
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
    }

    private fun setupEmirateSpinner() {
        val emirates = listOf("Ajman","Dubai","Abu Dhabi","Sharjah","Ras Al Khaimah","Fujairah","Umm Al Quwain")
        val adapter  = ArrayAdapter(this, android.R.layout.simple_spinner_item, emirates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        try { findViewById<Spinner>(R.id.spinnerEmirate).adapter = adapter } catch (e: Exception) {}
    }

    private fun setupTypeButtons() {
        val types = mapOf(
            R.id.typeResidential to "Residential",
            R.id.typeCommercial  to "Commercial",
            R.id.typeMixed       to "Mixed use"
        )
        types.forEach { (id, name) ->
            try {
                findViewById<TextView>(id).setOnClickListener {
                    selectedType = name
                    types.keys.forEach { btnId ->
                        try {
                            val btn = findViewById<TextView>(btnId)
                            if (btnId == id) { btn.setTextColor(getColor(R.color.blue_dark)); btn.setBackgroundResource(R.drawable.bg_badge_blue) }
                            else             { btn.setTextColor(getColor(R.color.text_muted)); btn.setBackgroundResource(R.drawable.bg_stat_card) }
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupAmenityButtons() {
        val amenities = mapOf(
            R.id.amenParking to "Parking", R.id.amenPool to "Swimming Pool",
            R.id.amenGym to "Gym", R.id.amenSecurity to "Security",
            R.id.amenElevator to "Elevator", R.id.amenCCTV to "CCTV",
            R.id.amenCentral to "Central AC"
        )
        amenities.forEach { (id, name) ->
            try {
                val btn = findViewById<TextView>(id)
                btn.setOnClickListener {
                    if (selectedAmenities.contains(name)) {
                        selectedAmenities.remove(name)
                        btn.setTextColor(getColor(R.color.text_muted)); btn.setBackgroundResource(R.drawable.bg_stat_card)
                    } else {
                        selectedAmenities.add(name)
                        btn.setTextColor(getColor(R.color.blue_dark)); btn.setBackgroundResource(R.drawable.bg_badge_blue)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupSaveButton() {
        // Update button label for edit mode
        try {
            val btn = findViewById<TextView>(R.id.btnSaveDetails)
            btn.text = if (editMode) "Save changes" else "Save and continue"
        } catch (e: Exception) {}

        try {
            findViewById<TextView>(R.id.btnSaveDetails).setOnClickListener {
                val buildingName = et(R.id.etBuildingName)
                val floors       = et(R.id.etFloors)
                val units        = et(R.id.etUnits)
                val area         = et(R.id.etArea)
                val emirate      = try { findViewById<Spinner>(R.id.spinnerEmirate).selectedItem.toString() } catch (e: Exception) { "Ajman" }

                when {
                    buildingName.isEmpty() -> toast("Enter your building name")
                    floors.isEmpty()       -> toast("Enter the number of floors")
                    units.isEmpty()        -> toast("Enter the number of units")
                    area.isEmpty()         -> toast("Enter the area / location")
                    else -> saveAndContinue(buildingName, floors, units, area, emirate)
                }
            }
        } catch (e: Exception) {}
    }

    private fun saveAndContinue(buildingName: String, floors: String, units: String, area: String, emirate: String) {
        val btn = try { findViewById<TextView>(R.id.btnSaveDetails) } catch (e: Exception) { null }
        btn?.text = "Saving..."; btn?.isEnabled = false

        val propertyData = hashMapOf<String, Any>(
            "buildingName"    to buildingName,
            "numberOfFloors"  to (floors.toIntOrNull() ?: 0),
            "numberOfUnits"   to (units.toIntOrNull()  ?: 0),
            "area"            to area,
            "emirate"         to emirate,
            "propertyType"    to selectedType,
            "amenities"       to selectedAmenities.toList(),
            "propertyAddress" to "$buildingName, $area, $emirate"
        )

        db.collection("users").document(myUid).update(propertyData)
            .addOnSuccessListener {
                // Also update landlordVerifications
                db.collection("landlordVerifications").document(myUid)
                    .set(hashMapOf(
                        "uid"             to myUid,
                        "name"            to SessionManager.getName(this),
                        "email"           to SessionManager.getEmail(this),
                        "phone"           to SessionManager.getPhone(this),
                        "buildingName"    to buildingName,
                        "numberOfFloors"  to (floors.toIntOrNull() ?: 0),
                        "numberOfUnits"   to (units.toIntOrNull()  ?: 0),
                        "area"            to area,
                        "emirate"         to emirate,
                        "propertyType"    to selectedType,
                        "amenities"       to selectedAmenities.toList(),
                        "propertyAddress" to "$buildingName, $area, $emirate",
                        "status"          to if (editMode) "approved" else "pending",
                        "updatedAt"       to System.currentTimeMillis()
                    ))

                btn?.isEnabled = true
                toast(if (editMode) "Property details updated!" else "Details saved!")

                if (editMode) {
                    finish() // Go back to properties screen
                } else {
                    // Notify admin and go to verification
                    FirebaseHelper.notifyAdminLandlordVerification(
                        SessionManager.getName(this), SessionManager.getEmail(this), "Building: $buildingName, $area"
                    )
                    startActivity(Intent(this, LandlordVerificationActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                btn?.text = if (editMode) "Save changes" else "Save and continue"
                btn?.isEnabled = true
                toast("Failed: ${e.message}")
            }
    }

    private fun et(id: Int) = try { findViewById<EditText>(id).text.toString().trim() } catch (e: Exception) { "" }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
