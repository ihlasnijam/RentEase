package com.example.rentease

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class LandlordPropertiesActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landlord_properties)
        setupBackButton()
        setupBottomNav()
        loadPropertyDetails()
    }

    private fun setupBackButton() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    // ── Load real property from Firestore ─────────────────────────────────────

    private fun loadPropertyDetails() {
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { doc ->
                val buildingName  = doc.getString("buildingName")  ?: ""
                val area          = doc.getString("area")          ?: ""
                val emirate       = doc.getString("emirate")       ?: ""
                val floors        = doc.getLong("numberOfFloors")  ?: 0L
                val units         = doc.getLong("numberOfUnits")   ?: 0L
                val propType      = doc.getString("propertyType")  ?: ""
                val amenities     = doc.get("amenities") as? List<*> ?: emptyList<Any>()

                if (buildingName.isEmpty()) {
                    // ── Landlord has no property details — ask them to fill in ──
                    showAddDetailsPrompt()
                } else {
                    showPropertyCard(buildingName, area, emirate, floors, units, propType, amenities)
                }
            }
            .addOnFailureListener {
                showAddDetailsPrompt()
            }
    }

    // ── Show real property details ────────────────────────────────────────────

    private fun showPropertyCard(
        buildingName: String, area: String, emirate: String,
        floors: Long, units: Long, propType: String, amenities: List<*>
    ) {
        val container = try { findViewById<LinearLayout>(R.id.propertyContainer) }
        catch (e: Exception) { return }
        container.removeAllViews()

        // Hero card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            setPadding(48, 48, 48, 48)
        }

        // Building name
        card.addView(TextView(this).apply {
            text = buildingName; textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        })

        // Location
        val location = listOf(area, emirate).filter { it.isNotEmpty() }.joinToString(", ")
        if (location.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "📍 $location"; textSize = 13f
                setTextColor(resources.getColor(R.color.blue_primary, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
            })
        }

        // Divider
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = 16 }
            setBackgroundColor(resources.getColor(R.color.border, null))
        })

        // Stats row
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        fun statChip(value: String, label: String) = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity      = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_stat_card)
            setPadding(16, 16, 16, 16)
            addView(TextView(this@LandlordPropertiesActivity).apply {
                text = value; textSize = 20f; gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.blue_primary, null))
            })
            addView(TextView(this@LandlordPropertiesActivity).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.text_muted, null))
            })
        }

        statsRow.addView(statChip(floors.toString(), "Floors").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        })
        statsRow.addView(statChip(units.toString(), "Units").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        })
        if (propType.isNotEmpty()) {
            statsRow.addView(statChip(propType.split(" ").first(), "Type").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        card.addView(statsRow)

        // Amenities
        if (amenities.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "AMENITIES"; textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.text_muted, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10 }
            })

            val amenRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            // Wrap amenities in flow-style layout
            val amenText = amenities.joinToString("   •   ")
            card.addView(TextView(this).apply {
                text = amenText; textSize = 12f
                setTextColor(resources.getColor(R.color.navy, null))
            })
        }

        container.addView(card)

        // Edit button
        val editBtn = TextView(this).apply {
            text = "✏️  Edit property details"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_primary, null))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true; isFocusable = true
        }
        editBtn.setOnClickListener { openEditDetails() }
        container.addView(editBtn)
    }

    // ── Prompt landlords who signed up before property details screen ─────────

    private fun showAddDetailsPrompt() {
        val container = try { findViewById<LinearLayout>(R.id.propertyContainer) }
        catch (e: Exception) { return }
        container.removeAllViews()

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 60, 48, 60)
        }

        wrapper.addView(TextView(this).apply {
            text = "🏢"; textSize = 48f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        })

        wrapper.addView(TextView(this).apply {
            text = "Add your property details"
            textSize = 18f; gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        })

        wrapper.addView(TextView(this).apply {
            text = "You haven't added your property details yet.\nThis helps tenants know where they live\nand helps you track your portfolio."
            textSize = 13f; gravity = Gravity.CENTER
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })

        val addBtn = TextView(this).apply {
            text = "Add property details"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            setBackgroundResource(R.drawable.bg_button_primary)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true; isFocusable = true
        }
        addBtn.setOnClickListener { openEditDetails() }
        wrapper.addView(addBtn)

        container.addView(wrapper)
    }

    // ── Open the property details activity for editing ────────────────────────

    private fun openEditDetails() {
        startActivity(Intent(this, LandlordSignupDetailsActivity::class.java).apply {
            putExtra("editMode", true)
        })
    }

    private fun setupBottomNav() {
        val nav = try { findViewById<BottomNavigationView>(R.id.bottomNav) } catch (e: Exception) { return }
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

    override fun onResume() { super.onResume(); loadPropertyDetails() }
}
