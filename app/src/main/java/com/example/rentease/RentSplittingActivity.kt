package com.example.rentease

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RentSplittingActivity : AppCompatActivity() {

    private val db    = FirebaseFirestore.getInstance()
    private val myUid get() = SessionManager.getUid(this)
    private val myName get() = SessionManager.getName(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rent_splitting)
        setupBack()
        loadSplittingData()
        setupAddRoommateButton()
        setupAddBillButton()
    }

    private fun setupBack() {
        try { findViewById<LinearLayout>(R.id.btnBack).setOnClickListener { finish() } } catch (e: Exception) {}
    }

    // ── Load all data for this unit's split ───────────────────────────────────

    private fun loadSplittingData() {
        loadRoommates()
        loadBills()
    }

    // ── ROOMMATES ─────────────────────────────────────────────────────────────

    private fun loadRoommates() {
        db.collection("rentSplitting")
            .whereEqualTo("primaryTenantId", myUid)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    // Create a split group for this tenant
                    createSplitGroup()
                } else {
                    val doc      = docs.documents[0]
                    val groupId  = doc.id
                    val members  = doc.get("members") as? List<Map<String, Any>> ?: emptyList()
                    val totalRent = doc.getDouble("totalRent") ?: 4500.0
                    displayRoommates(groupId, members, totalRent)
                }
            }
    }

    private fun createSplitGroup() {
        val groupData = hashMapOf(
            "primaryTenantId" to myUid,
            "primaryName"     to myName,
            "totalRent"       to 4500.0,
            "members"         to listOf(
                hashMapOf("uid" to myUid, "name" to myName, "share" to 4500.0, "status" to "Primary")
            ),
            "createdAt"       to System.currentTimeMillis()
        )
        db.collection("rentSplitting").add(groupData)
            .addOnSuccessListener { ref ->
                displayRoommates(ref.id, listOf(
                    mapOf("uid" to myUid, "name" to myName, "share" to 4500.0, "status" to "Primary")
                ), 4500.0)
            }
    }

    private fun displayRoommates(groupId: String, members: List<Map<String, Any>>, totalRent: Double) {
        val container = try { findViewById<LinearLayout>(R.id.roommatesContainer) } catch (e: Exception) { return }
        container.removeAllViews()

        val perPerson = if (members.isNotEmpty()) totalRent / members.size else totalRent

        // Summary chip
        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_badge_blue)
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        summaryRow.addView(TextView(this).apply {
            text = "Total rent: AED ${String.format("%,.0f", totalRent)}"
            textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.blue_dark, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        summaryRow.addView(TextView(this).apply {
            text = "${members.size} ${if (members.size == 1) "person" else "people"}"
            textSize = 12f; setTextColor(resources.getColor(R.color.blue_dark, null))
        })
        container.addView(summaryRow)

        members.forEach { member ->
            val name   = member["name"]   as? String ?: "Unknown"
            val share  = member["share"]  as? Double ?: perPerson
            val status = member["status"] as? String ?: "Active"
            val uid    = member["uid"]    as? String ?: ""

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding(32, 28, 32, 28)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10 }
            }

            val sz       = (40 * resources.displayMetrics.density).toInt()
            val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            row.addView(TextView(this).apply {
                text = initials; textSize = 12f; gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.blue_dark, null))
                setBackgroundResource(R.drawable.bg_avatar)
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { marginEnd = 24 }
            })

            val col = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = name; textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.navy, null))
            })
            col.addView(TextView(this).apply {
                text = "AED ${String.format("%,.0f", share)} / month"
                textSize = 12f; setTextColor(resources.getColor(R.color.blue_primary, null))
            })

            row.addView(col)
            row.addView(TextView(this).apply {
                text = status; textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
                when (status) {
                    "Primary" -> { setTextColor(resources.getColor(R.color.blue_dark,  null)); setBackgroundResource(R.drawable.bg_badge_blue)  }
                    "Paid"    -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                    else      -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
                }
            })

            container.addView(row)
        }

        // Set up add roommate button with current group info
        try {
            findViewById<TextView>(R.id.btnAddRoommate).setOnClickListener {
                showAddRoommateDialog(groupId, members, totalRent)
            }
        } catch (e: Exception) {}

        try {
            findViewById<TextView>(R.id.btnSetTotalRent).setOnClickListener {
                showSetTotalRentDialog(groupId, totalRent)
            }
        } catch (e: Exception) {}
    }

    private fun showAddRoommateDialog(groupId: String, currentMembers: List<Map<String, Any>>, totalRent: Double) {
        val nameInput = EditText(this).apply {
            hint = "Roommate's full name"; textSize = 14f; setPadding(50, 30, 50, 10)
        }
        val emailInput = EditText(this).apply {
            hint = "Roommate's email (optional)"; textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(50, 10, 50, 30)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(nameInput); addView(emailInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Add roommate")
            .setMessage("Split the rent with someone sharing your unit.")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) { toast("Enter the roommate's name"); return@setPositiveButton }

                val newMembers    = currentMembers.toMutableList()
                val newShare      = totalRent / (newMembers.size + 1)
                // Recalculate all shares equally
                val updatedMembers = newMembers.map { m ->
                    (m as? Map<String, Any>)?.toMutableMap()?.also { it["share"] = newShare } ?: m
                }.toMutableList()

                updatedMembers.add(hashMapOf(
                    "uid"    to "",
                    "name"   to name,
                    "share"  to newShare,
                    "status" to "Pending"
                ))

                db.collection("rentSplitting").document(groupId)
                    .update("members", updatedMembers)
                    .addOnSuccessListener {
                        toast("$name added. Each person owes AED ${String.format("%,.0f", newShare)}/month.")
                        loadRoommates()
                    }
                    .addOnFailureListener { toast("Failed: ${it.message}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSetTotalRentDialog(groupId: String, currentTotal: Double) {
        val input = EditText(this).apply {
            hint = "e.g. 4500"; textSize = 14f; setPadding(50, 30, 50, 30)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(currentTotal.toInt().toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Set total rent")
            .setMessage("Enter the total monthly rent for the unit:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val amount = input.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount <= 0) { toast("Enter a valid amount"); return@setPositiveButton }
                db.collection("rentSplitting").document(groupId)
                    .update("totalRent", amount)
                    .addOnSuccessListener {
                        toast("Total rent updated to AED ${String.format("%,.0f", amount)}")
                        loadRoommates()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun setupAddRoommateButton() {
        // Wired dynamically in displayRoommates above
    }

    // ── UTILITY BILLS ─────────────────────────────────────────────────────────

    private fun loadBills() {
        val container = try { findViewById<LinearLayout>(R.id.billsContainer) } catch (e: Exception) { return }
        container.removeAllViews()

        db.collection("utilityBills")
            .whereEqualTo("tenantId", myUid)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    container.addView(TextView(this).apply {
                        text = "No utility bills recorded yet.\nTap '+ Add bill' to split a bill."
                        textSize = 13f; gravity = Gravity.CENTER
                        setTextColor(resources.getColor(R.color.text_muted, null))
                        setPadding(0, 40, 0, 40)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    })
                    return@addOnSuccessListener
                }

                val sorted = docs.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                sorted.forEach { doc ->
                    val type    = doc.getString("type")    ?: "Bill"
                    val amount  = doc.getDouble("amount")  ?: 0.0
                    val month   = doc.getString("month")   ?: ""
                    val status  = doc.getString("status")  ?: "Unpaid"
                    val split   = doc.getDouble("splitAmount") ?: amount
                    val people  = doc.getLong("splitBetween")?.toInt() ?: 1

                    addBillRow(container, type, amount, month, status, split, people)
                }
            }
    }

    private fun addBillRow(
        container: LinearLayout, type: String, amount: Double,
        month: String, status: String, splitAmount: Double, people: Int
    ) {
        val icon = when {
            type.contains("Electricity", ignoreCase = true) -> "⚡"
            type.contains("Water",       ignoreCase = true) -> "💧"
            type.contains("Gas",         ignoreCase = true) -> "🔥"
            type.contains("Internet",    ignoreCase = true) -> "📶"
            else -> "🧾"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(32, 28, 32, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }

        card.addView(TextView(this).apply {
            text = icon; textSize = 24f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ).apply { marginEnd = 20 }
        })

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = "$type — $month"; textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.navy, null))
        })
        col.addView(TextView(this).apply {
            text = "Total: AED ${String.format("%,.0f", amount)} · Your share: AED ${String.format("%,.0f", splitAmount)} ($people ${if (people == 1) "person" else "people"})"
            textSize = 11f; setTextColor(resources.getColor(R.color.text_muted, null))
        })

        card.addView(col)
        card.addView(TextView(this).apply {
            text = status; textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(16, 6, 16, 6)
            when (status) {
                "Paid"   -> { setTextColor(resources.getColor(R.color.green_dark, null)); setBackgroundResource(R.drawable.bg_badge_green) }
                "Unpaid" -> { setTextColor(resources.getColor(R.color.red_dark,   null)); setBackgroundResource(R.drawable.bg_badge_red)   }
                else     -> { setTextColor(resources.getColor(R.color.amber_dark, null)); setBackgroundResource(R.drawable.bg_badge_amber) }
            }
        })

        container.addView(card)
    }

    private fun setupAddBillButton() {
        try {
            findViewById<TextView>(R.id.btnAddBill).setOnClickListener {
                showAddBillDialog()
            }
        } catch (e: Exception) {}
    }

    private fun showAddBillDialog() {
        val billTypes = arrayOf("⚡ Electricity", "💧 Water", "🔥 Gas", "📶 Internet / WiFi", "🧾 Other")

        AlertDialog.Builder(this)
            .setTitle("What type of bill?")
            .setItems(billTypes) { _, which ->
                val type = billTypes[which].substringAfter(" ")
                showBillAmountDialog(type)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBillAmountDialog(type: String) {
        val amountInput = EditText(this).apply {
            hint = "Total bill amount (AED)"; textSize = 14f; setPadding(50, 30, 50, 10)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val peopleInput = EditText(this).apply {
            hint = "Split between how many people?"; textSize = 14f; setPadding(50, 10, 50, 30)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText("2")
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(amountInput); addView(peopleInput)
        }

        AlertDialog.Builder(this)
            .setTitle("$type bill")
            .setMessage("Enter the total amount and how many people are splitting it:")
            .setView(wrapper)
            .setPositiveButton("Add bill") { _, _ ->
                val amount = amountInput.text.toString().trim().toDoubleOrNull()
                val people = peopleInput.text.toString().trim().toIntOrNull() ?: 1
                if (amount == null || amount <= 0) { toast("Enter a valid amount"); return@setPositiveButton }

                val split      = amount / people
                val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time)

                db.collection("utilityBills").add(hashMapOf(
                    "tenantId"    to myUid,
                    "tenantName"  to myName,
                    "type"        to type,
                    "amount"      to amount,
                    "splitAmount" to split,
                    "splitBetween" to people,
                    "month"       to monthLabel,
                    "status"      to "Unpaid",
                    "createdAt"   to System.currentTimeMillis()
                )).addOnSuccessListener {
                    toast("$type bill added. Your share: AED ${String.format("%,.0f", split)}")
                    loadBills()
                }.addOnFailureListener { toast("Failed: ${it.message}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
