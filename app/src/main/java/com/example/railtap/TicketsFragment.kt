package com.example.railtap

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TransitPass(
    val id: String = "",
    val lineName: String = "",           // "LRT-1" or "LRT-2"
    val passengersCount: Int = 1,
    val origin: String = "",             // "MARIKINA\nPASIG"
    val destination: String = "",        // "RECTO"
    val expiresAtTimestamp: Long = 0L,   // Epoch millis from DB
    val customWarningText: String? = null
) {
    // Calculates "Expires in X hours / minutes" dynamically
    val expiryDisplay: String
        get() {
            val remainingMillis = expiresAtTimestamp - System.currentTimeMillis()
            if (remainingMillis <= 0) return "Expired"

            val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60

            return when {
                hours > 0 -> "Expires in $hours hour" + if (hours > 1) "s" else ""
                else -> "Expires in $minutes minute" + if (minutes > 1) "s" else ""
            }
        }

    // Displays warning banner if pass expires in under 60 minutes
    val isExpiringSoon: Boolean
        get() {
            val remainingMillis = expiresAtTimestamp - System.currentTimeMillis()
            return remainingMillis in 1..TimeUnit.MINUTES.toMillis(60)
        }

    val warningDisplay: String
        get() {
            if (!customWarningText.isNullOrEmpty()) return customWarningText
            val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(expiresAtTimestamp - System.currentTimeMillis())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "Your pass expires in $remainingMinutes minutes at ${timeFormat.format(Date(expiresAtTimestamp))}. Use it before it expires!"
        }
}

/** Tickets tab. Layout: fragment_tickets.xml */
class TicketsFragment : TabFragment(R.layout.fragment_tickets) {

    private var allPasses = mutableListOf<TransitPass>()
    private lateinit var adapter: OtherPassesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvOther = findViewById<RecyclerView>(R.id.rvOtherPasses)
        rvOther.layoutManager = LinearLayoutManager(requireContext())

        // Adds 16dp spacing between cards without using layout_marginBottom on the item root
        rvOther.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val density = view.resources.displayMetrics.density
                outRect.bottom = (16 * density).toInt()
            }
        })

        adapter = OtherPassesAdapter(mutableListOf()) { clickedPass, adapterPosition ->
            // Swap clicked item with top card (index 0)
            val currentTop = allPasses[0]
            val clickedFullIndex = adapterPosition + 1

            allPasses[0] = clickedPass
            allPasses[clickedFullIndex] = currentTop

            refreshUI()
        }
        rvOther.adapter = adapter

        // Setup empty state button
        findViewById<View>(R.id.btnBuyTicketEmpty)?.setOnClickListener {
            // Booking needs a line (LRT-1 / LRT-2), so go to Home to pick one
            showTab(NavTab.HOME)
        }

        fetchPassesFromDatabase()
    }

    private fun fetchPassesFromDatabase() {
        // Database not connected yet, so there are no passes (shows the empty "No Active Tickets" card).
        // To preview the screen with fake passes, change this to true.
        val useSampleData = false
        if (!useSampleData) {
            allPasses = mutableListOf()
            refreshUI()
            return
        }

        val now = System.currentTimeMillis()

        // Sample passes for testing only. Replace with your database query later.
        allPasses = mutableListOf(
            TransitPass(
                id = "1",
                lineName = "LRT-2",
                passengersCount = 3,
                origin = "MARIKINA\nPASIG",
                destination = "RECTO",
                expiresAtTimestamp = now + TimeUnit.MINUTES.toMillis(59),
                customWarningText = "Your pass expires in 59 minutes at 2:36 AM. Use it before it expires!"
            ),
            TransitPass(
                id = "2",
                lineName = "LRT-1",
                passengersCount = 1,
                origin = "FERNANDO\nPOE JR.",
                destination = "BACLARAN",
                expiresAtTimestamp = now + TimeUnit.HOURS.toMillis(3),
                customWarningText = null
            )
        )

        refreshUI()
    }

    private fun refreshUI() {
        val emptyCard = findViewById<View>(R.id.emptyCardContainer)
        val topContainer = findViewById<View>(R.id.topCardContainer)
        val layoutOther = findViewById<View>(R.id.layoutOtherPasses)

        // Case 1: No tickets in database
        if (allPasses.isEmpty()) {
            emptyCard.visibility = View.VISIBLE
            topContainer.visibility = View.GONE
            layoutOther.visibility = View.GONE
            return
        }

        // Case 2: At least 1 ticket exists
        emptyCard.visibility = View.GONE
        topContainer.visibility = View.VISIBLE
        bindTopCard(allPasses[0])

        // Case 3: More than 1 ticket exists
        if (allPasses.size > 1) {
            val otherList = allPasses.subList(1, allPasses.size).toMutableList()
            layoutOther.visibility = View.VISIBLE
            adapter.updateList(otherList)
        } else {
            layoutOther.visibility = View.GONE
        }
    }

    private fun bindTopCard(pass: TransitPass) {
        val pill = findViewById<TextView>(R.id.lrt2Pill)
        val passengers = findViewById<TextView>(R.id.passengersBadge)
        val origin = findViewById<TextView>(R.id.originStation)
        val destination = findViewById<TextView>(R.id.destinationStation)
        val arrow = findViewById<ImageView>(R.id.arrow1)
        val warningBanner = findViewById<View>(R.id.warningBanner)
        val warningText = findViewById<TextView>(R.id.warningText)
        val btnQR = findViewById<MaterialButton>(R.id.btnShowAllQR)

        pill.text = pass.lineName
        passengers.text = "${pass.passengersCount} PASSENGER" + if (pass.passengersCount > 1) "S" else ""
        origin.text = pass.origin
        destination.text = pass.destination

        val isLine1 = pass.lineName.contains("1")
        val themeColor = if (isLine1) Color.parseColor("#00875A") else Color.parseColor("#6B46C1")
        val bgPillRes = if (isLine1) R.drawable.bg_pill_green else R.drawable.bg_purple_pill

        pill.setBackgroundResource(bgPillRes)
        arrow.setColorFilter(themeColor)
        btnQR.backgroundTintList = ColorStateList.valueOf(themeColor)

        if (pass.isExpiringSoon || !pass.customWarningText.isNullOrEmpty()) {
            warningBanner.visibility = View.VISIBLE
            warningText.text = pass.warningDisplay
        } else {
            warningBanner.visibility = View.GONE
        }
    }
}

class OtherPassesAdapter(
    private var passes: MutableList<TransitPass>,
    private val onItemClick: (TransitPass, Int) -> Unit
) : RecyclerView.Adapter<OtherPassesAdapter.PassViewHolder>() {

    inner class PassViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val pill: TextView = view.findViewById(R.id.itemLinePill)
        val passengers: TextView = view.findViewById(R.id.itemPassengerBadge)
        val origin: TextView = view.findViewById(R.id.itemOriginStation)
        val destination: TextView = view.findViewById(R.id.itemDestinationStation)
        val arrow: ImageView = view.findViewById(R.id.itemArrow)
        val expiry: TextView = view.findViewById(R.id.itemExpiryLabel)
        val btnQR: MaterialButton = view.findViewById(R.id.itemBtnShowQR)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PassViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_other_pass, parent, false)
        return PassViewHolder(view)
    }

    override fun onBindViewHolder(holder: PassViewHolder, position: Int) {
        val pass = passes[position]

        holder.pill.text = pass.lineName
        holder.passengers.text = "${pass.passengersCount} PASSENGER" + if (pass.passengersCount > 1) "S" else ""
        holder.origin.text = pass.origin
        holder.destination.text = pass.destination
        holder.expiry.text = pass.expiryDisplay

        val isLine1 = pass.lineName.contains("1")
        val themeColor = if (isLine1) Color.parseColor("#00875A") else Color.parseColor("#6B46C1")
        val bgPillRes = if (isLine1) R.drawable.bg_pill_green else R.drawable.bg_purple_pill

        holder.pill.setBackgroundResource(bgPillRes)
        holder.arrow.setColorFilter(themeColor)
        holder.btnQR.backgroundTintList = ColorStateList.valueOf(themeColor)

        holder.itemView.setOnClickListener {
            onItemClick(pass, position)
        }
        holder.btnQR.setOnClickListener {
            onItemClick(pass, position)
        }
    }

    override fun getItemCount() = passes.size

    fun updateList(newList: MutableList<TransitPass>) {
        passes = newList
        notifyDataSetChanged()
    }
}