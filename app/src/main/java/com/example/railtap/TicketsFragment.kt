package com.example.railtap

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Tickets tab, refreshes every 30s
class TicketsFragment : TabFragment(R.layout.fragment_tickets) {
    private val repo = TicketRepository()
    private var passes: List<TransitPass> = emptyList()
    private var scannedPasses: List<TransitPass> = emptyList()
    private lateinit var scannedSection: LinearLayout
    private lateinit var adapter: OtherPassesAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var loadJob: Job? = null
    private var hasLoaded = false
    private var loadError: String? = null

    private val refresher = object : Runnable {
        override fun run() {
            renderPasses()
            fetchPassesFromDatabase()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = findViewById<RecyclerView>(R.id.rvOtherPasses)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView,
                state: RecyclerView.State
            ) { outRect.bottom = (16 * resources.displayMetrics.density).toInt() }
        })
        adapter = OtherPassesAdapter { pass -> openQr(pass) }
        recycler.adapter = adapter
        setupScannedSection()
        renderPasses()
    }

    override fun onTabShown() {
        handler.removeCallbacks(refresher)
        refresher.run()
    }

    override fun onTabHidden() {
        handler.removeCallbacks(refresher)
        // don't cancel loadJob here, it's not an error
    }

    override fun onDestroyView() {
        handler.removeCallbacks(refresher)
        super.onDestroyView()
    }

    private fun fetchPassesFromDatabase() {
        // one request at a time
        if (loadJob?.isActive == true) return
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val overview = repo.loadTicketOverview()
                passes = overview.active
                scannedPasses = overview.scanned
                hasLoaded = true
                loadError = null
                renderPasses()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("RailTapTickets", "Couldn't load active tickets", e)
                loadError = e.message ?: e.javaClass.simpleName
                renderPasses() // keep old tickets on error
            }
        }
    }

    private fun renderPasses() {
        renderScannedTickets()
        // only keep valid tickets
        val now = System.currentTimeMillis()
        val validPasses = passes.mapNotNull { pass ->
            val validTickets = pass.tickets.filter { it.status == "entered" || (it.status == "unused" && it.expiresAtMillis > now) }
            if (validTickets.isEmpty()) null else pass.copy(tickets = validTickets)
        }

        val empty = findViewById<View>(R.id.emptyCardContainer)
        val featured = findViewById<View>(R.id.topCardContainer)
        val others = findViewById<View>(R.id.layoutOtherPasses)
        val emptyTitle = findViewById<TextView>(R.id.emptyTitle)
        val emptySubtitle = findViewById<TextView>(R.id.emptySubtitle)
        val action = findViewById<MaterialButton>(R.id.btnBuyTicketEmpty)

        if (validPasses.isEmpty()) {
            empty.visibility = View.VISIBLE
            featured.visibility = View.GONE
            others.visibility = View.GONE
            adapter.update(emptyList())
            when {
                loadError != null -> {
                    emptyTitle.text = "Couldn't Load Tickets"
                    emptySubtitle.text = "Check your internet connection and sign-in, then try again."
                    action.text = "Try Again"
                    action.isEnabled = true
                    action.setOnClickListener { fetchPassesFromDatabase() }
                }
                !hasLoaded -> {
                    emptyTitle.text = "Loading Tickets…"
                    emptySubtitle.text = "Fetching your purchased passes securely."
                    action.text = "Loading…"
                    action.isEnabled = false
                }
                else -> {
                    emptyTitle.text = "No Active Tickets"
                    emptySubtitle.text = if (scannedPasses.isEmpty()) {
                        "You don't have any active transit passes right now. Book a ticket to begin your ride."
                    } else {
                        "Your used passes are marked SCANNED below. Buy another ticket to ride again."
                    }
                    action.text = "Buy Ticket  →"
                    action.isEnabled = true
                    action.setOnClickListener {
                        showTab(NavTab.HOME)
                    }
                }
            }
            return
        }

        empty.visibility = View.GONE
        featured.visibility = View.VISIBLE
        bindFeatured(validPasses.first())
        others.visibility = if (validPasses.size > 1) View.VISIBLE else View.GONE
        adapter.update(validPasses.drop(1))
    }

    // completed journeys section
    private fun setupScannedSection() {
        val scroll = findViewById<ScrollView>(R.id.dashboardScroll)
        val content = scroll.getChildAt(0) as LinearLayout
        scannedSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(18), 0, dp(20))
        }
        content.addView(scannedSection)
    }

    private fun renderScannedTickets() {
        if (!::scannedSection.isInitialized) return
        scannedSection.removeAllViews()
        if (scannedPasses.isEmpty()) {
            scannedSection.visibility = View.GONE
            return
        }
        scannedSection.visibility = View.VISIBLE
        scannedSection.addView(TextView(requireContext()).apply {
            text = "COMPLETED JOURNEYS"
            textSize = 13f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.plus_jakarta_sans_bold)
            setTextColor(Color.parseColor("#182138"))
            setPadding(dp(4), 0, 0, dp(12))
        })
        // same card as trip history, tap for receipt
        val inflater = android.view.LayoutInflater.from(requireContext())
        for (pass in scannedPasses) {
            val card = inflater.inflate(R.layout.item_trip_history, scannedSection, false)
            card.setOnClickListener { openReceipt(pass) }

            card.findViewById<TextView>(R.id.tripOrigin).text = pass.origin
            card.findViewById<TextView>(R.id.tripDestination).text = pass.destination
            fitStationPair(card.findViewById(R.id.tripOrigin), card.findViewById(R.id.tripDestination))

            val pill = card.findViewById<TextView>(R.id.tripLine)
            pill.text = pass.lineName
            if (pass.lineName.contains("1")) {
                pill.setBackgroundResource(R.drawable.bg_pill_green)
                pill.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_line))
            }
            scannedSection.addView(card)
        }
    }

    private fun openReceipt(pass: TransitPass) {
        val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        ReceiptActivity.open(
            from = activity,
            total = pass.tickets.sumOf { it.fare },
            lineCode = pass.lineName,
            origin = pass.origin,
            destination = pass.destination,
            referenceNo = pass.referenceNo.ifEmpty { "--" },
            paidAtMillis = pass.purchasedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
            fromHistory = true,
            regular = pass.tickets.count { it.fareType == "Regular" },
            student = pass.tickets.count { it.fareType == "Student" },
            senior = pass.tickets.count { it.fareType == "Senior Citizen" },
            pwd = pass.tickets.count { it.fareType == "PWD" }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bindFeatured(pass: TransitPass) {
        val lrt1 = pass.lineName == "LRT-1"
        val color = Color.parseColor(if (lrt1) "#00875A" else "#6B56C8")
        findViewById<TextView>(R.id.lrt2Pill).apply {
            text = pass.lineName
            // same pill as completed journeys
            setBackgroundResource(if (lrt1) R.drawable.bg_pill_green else R.drawable.bg_pill_purple_solid)
            setTextColor(if (lrt1) ContextCompat.getColor(requireContext(), R.color.green_line) else Color.WHITE)
        }
        val enteredCount = pass.tickets.count { it.status == "entered" }
        findViewById<TextView>(R.id.passengersBadge).text = if (enteredCount > 0) {
            "$enteredCount ENTERED / ${pass.passengersCount} PASSENGERS"
        } else {
            "${pass.passengersCount} PASSENGER${if (pass.passengersCount == 1) "" else "S"}"
        }
        findViewById<TextView>(R.id.originStation).text = pass.origin
        findViewById<TextView>(R.id.destinationStation).text = pass.destination
        fitStationPair(findViewById(R.id.originStation), findViewById(R.id.destinationStation))
        findViewById<ImageView>(R.id.arrow1).setColorFilter(color)
        findViewById<MaterialButton>(R.id.btnShowAllQR).apply {
            backgroundTintList = ColorStateList.valueOf(color)
            text = if (enteredCount > 0) "SHOW QR FOR EXIT" else "SHOW ALL QR CODE"
            setOnClickListener { openQr(pass) }
        }
        val warning = findViewById<View>(R.id.warningBanner)
        warning.visibility = if (pass.isExpiringSoon) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.warningText).text = pass.warningDisplay
    }

    private fun openQr(pass: TransitPass) {
        if (pass.tickets.none { it.status == "entered" || (it.status == "unused" && it.expiresAtMillis > System.currentTimeMillis()) }) {
            Toast.makeText(requireContext(), "This pass has expired", Toast.LENGTH_SHORT).show()
            fetchPassesFromDatabase()
            return
        }
        startActivity(QRPassActivity.openIntent(requireContext(), pass.transactionId))
    }
}

class OtherPassesAdapter(
    private val onClick: (TransitPass) -> Unit
) : RecyclerView.Adapter<OtherPassesAdapter.Holder>() {
    private var items = listOf<TransitPass>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val badge: TextView = view.findViewById(R.id.itemLinePill)
        val passengers: TextView = view.findViewById(R.id.itemPassengerBadge)
        val origin: TextView = view.findViewById(R.id.itemOriginStation)
        val destination: TextView = view.findViewById(R.id.itemDestinationStation)
        val arrow: ImageView = view.findViewById(R.id.itemArrow)
        val expiry: TextView = view.findViewById(R.id.itemExpiryLabel)
        val warning: TextView = view.findViewById(R.id.itemWarningBanner)
        val showQR: MaterialButton = view.findViewById(R.id.itemBtnShowQR)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_other_pass, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val pass = items[position]
        val lrt1 = pass.lineName == "LRT-1"
        val color = Color.parseColor(if (lrt1) "#00875A" else "#6B56C8")
        holder.badge.text = pass.lineName
        holder.badge.setBackgroundResource(if (lrt1) R.drawable.bg_pill_green else R.drawable.bg_pill_purple_solid)
        holder.badge.setTextColor(if (lrt1) ContextCompat.getColor(holder.itemView.context, R.color.green_line) else Color.WHITE)
        holder.passengers.text = "${pass.passengersCount} PASSENGER${if (pass.passengersCount == 1) "" else "S"}"
        holder.origin.text = pass.origin
        holder.destination.text = pass.destination
        fitStationPair(holder.origin, holder.destination)
        holder.arrow.setColorFilter(color)
        holder.expiry.text = pass.expiryDisplay
        holder.showQR.text = if (pass.tickets.any { it.status == "entered" }) "SHOW QR FOR EXIT" else "SHOW QR CODE"
        holder.warning.visibility = if (pass.isExpiringSoon) View.VISIBLE else View.GONE
        holder.warning.text = pass.warningDisplay
        holder.showQR.backgroundTintList = ColorStateList.valueOf(color)
        holder.itemView.setOnClickListener { onClick(pass) }
        holder.showQR.setOnClickListener { onClick(pass) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<TransitPass>) {
        items = newItems
        notifyDataSetChanged()
    }
}
