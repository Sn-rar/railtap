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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TicketsActivity : AppCompatActivity() {
    private val repo = TicketRepository()
    private var passes: List<TransitPass> = emptyList()
    private lateinit var adapter: OtherPassesAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var loadJob: Job? = null
    private var hasLoaded = false
    private var loadError: String? = null

    private val refresher = object : Runnable {
        override fun run() {
            renderPasses()                 // countdown and expiry change immediately
            fetchPassesFromDatabase()      // also check server for used/paid tickets
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)
        applySystemBarPadding()
        setupBottomNav(this, NavTab.TICKETS)

        // Development-only gate scanner. Server verifies scanner role independently.
        findViewById<View>(R.id.btnGateScanner).apply {
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(this@TicketsActivity, GateScannerActivity::class.java))
            }
        }

        val recycler = findViewById<RecyclerView>(R.id.rvOtherPasses)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView,
                state: RecyclerView.State
            ) { outRect.bottom = (16 * resources.displayMetrics.density).toInt() }
        })
        adapter = OtherPassesAdapter { pass -> openQr(pass) }
        recycler.adapter = adapter
        renderPasses()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresher)
        refresher.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        // Don't cancel loadJob here: cancellation is normal when the screen changes,
        // and an interrupted request must not be shown as a database error.
        super.onPause()
    }

    private fun fetchPassesFromDatabase() {
        // One request at a time; don't continually cancel an in-flight request.
        if (loadJob?.isActive == true) return
        loadJob = lifecycleScope.launch {
            try {
                val result = repo.loadActivePasses()
                passes = result
                hasLoaded = true
                loadError = null
                renderPasses()
            } catch (cancelled: CancellationException) {
                throw cancelled // normal lifecycle cancellation is NOT a load error
            } catch (e: Exception) {
                Log.e("RailTapTickets", "Couldn't load active tickets", e)
                loadError = e.message ?: e.javaClass.simpleName
                renderPasses() // preserve previously loaded tickets on transient errors
            }
        }
    }

    private fun renderPasses() {
        // Every passenger must still be valid. A group can contain more than one.
        val now = System.currentTimeMillis()
        val validPasses = passes.mapNotNull { pass ->
            val validTickets = pass.tickets.filter { it.expiresAtMillis > now }
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
                    emptySubtitle.text = "You don't have any active transit passes right now. Book a ticket to begin your ride."
                    action.text = "Buy Ticket  →"
                    action.isEnabled = true
                    action.setOnClickListener {
                        startActivity(Intent(this, DashboardActivity::class.java))
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

    private fun bindFeatured(pass: TransitPass) {
        val lrt1 = pass.lineName == "LRT-1"
        val color = Color.parseColor(if (lrt1) "#00875A" else "#6B56C8")
        findViewById<TextView>(R.id.lrt2Pill).apply {
            text = pass.lineName
            setBackgroundResource(if (lrt1) R.drawable.bg_rt_lrt1_label else R.drawable.bg_purple_pill)
            setTextColor(Color.WHITE)
        }
        findViewById<TextView>(R.id.passengersBadge).text =
            "${pass.passengersCount} PASSENGER${if (pass.passengersCount == 1) "" else "S"}"
        findViewById<TextView>(R.id.originStation).text = pass.origin
        findViewById<TextView>(R.id.destinationStation).text = pass.destination
        findViewById<ImageView>(R.id.arrow1).setColorFilter(color)
        findViewById<MaterialButton>(R.id.btnShowAllQR).apply {
            backgroundTintList = ColorStateList.valueOf(color)
            setOnClickListener { openQr(pass) }
        }
        val warning = findViewById<View>(R.id.warningBanner)
        warning.visibility = if (pass.isExpiringSoon) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.warningText).text = pass.warningDisplay
    }

    private fun openQr(pass: TransitPass) {
        if (pass.tickets.none { it.expiresAtMillis > System.currentTimeMillis() }) {
            Toast.makeText(this, "This pass has expired", Toast.LENGTH_SHORT).show()
            fetchPassesFromDatabase()
            return
        }
        startActivity(QRPassActivity.openIntent(this, pass.transactionId))
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
        holder.badge.setBackgroundResource(if (lrt1) R.drawable.bg_rt_lrt1_label else R.drawable.bg_purple_pill)
        holder.badge.setTextColor(Color.WHITE)
        holder.passengers.text = "${pass.passengersCount} PASSENGER${if (pass.passengersCount == 1) "" else "S"}"
        holder.origin.text = pass.origin
        holder.destination.text = pass.destination
        holder.arrow.setColorFilter(color)
        holder.expiry.text = pass.expiryDisplay
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
