package space.declared.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import space.declared.weather.R
import space.declared.weather.data.TideData
import space.declared.weather.data.WaterData
import space.declared.weather.data.WaterLevelData
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * This fragment now displays a list of water stations and shows a detail card
 * for a selected station on the same screen.
 */
class WaterLevelFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    // --- UI Elements ---
    private lateinit var listProgressBar: ProgressBar
    private lateinit var noStationsTextView: TextView
    private lateinit var sortControlsContainer: LinearLayout
    private lateinit var sortByDistanceButton: Button
    private lateinit var sortByNameButton: Button
    private lateinit var stationRecyclerView: RecyclerView
    private lateinit var stationListAdapter: StationListAdapter

    // --- Detail Card UI Elements ---
    private lateinit var detailCard: CardView
    private lateinit var detailProgressBar: ProgressBar
    private lateinit var detailContentLayout: LinearLayout
    private lateinit var detailTitle: TextView
    private lateinit var detailSubtitle: TextView
    private lateinit var detailDataContainer: LinearLayout // To add TextViews dynamically
    private lateinit var closeDetailButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // You will need to update your fragment_water_level.xml layout
        // to include all the views defined above.
        return inflater.inflate(R.layout.fragment_water_level, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
    }

    private fun initializeViews(view: View) {
        // List Views
        listProgressBar = view.findViewById(R.id.listProgressBar)
        noStationsTextView = view.findViewById(R.id.noStationsTextView)
        sortControlsContainer = view.findViewById(R.id.sortControlsContainer)
        sortByDistanceButton = view.findViewById(R.id.sortByDistanceButton)
        sortByNameButton = view.findViewById(R.id.sortByNameButton)
        stationRecyclerView = view.findViewById(R.id.stationRecyclerView)

        // Detail Card Views
        detailCard = view.findViewById(R.id.detailCard)
        detailProgressBar = view.findViewById(R.id.detailProgressBar)
        detailContentLayout = view.findViewById(R.id.detailContentLayout)
        detailTitle = view.findViewById(R.id.detailTitle)
        detailSubtitle = view.findViewById(R.id.detailSubtitle)
        detailDataContainer = view.findViewById(R.id.detailDataContainer)
        closeDetailButton = view.findViewById(R.id.closeDetailButton)
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter { stationItem ->
            // When a station is clicked, fetch its details.
            // The observer will handle showing the card.
            viewModel.onStationSelected(stationItem)
        }
        stationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        stationRecyclerView.adapter = stationListAdapter
    }

    private fun setupClickListeners() {
        sortByDistanceButton.setOnClickListener {
            viewModel.onSortStationsByDistance()
        }
        sortByNameButton.setOnClickListener {
            viewModel.onSortStationsByName()
        }
        closeDetailButton.setOnClickListener {
            // Add a function to the ViewModel to clear the selection
            // For now, we can just hide the card. A VM function is better.
            detailCard.visibility = View.GONE
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // --- Handle List Display ---
                    listProgressBar.visibility = if (state.isLoading && state.stationListItems.isEmpty()) View.VISIBLE else View.GONE
                    stationListAdapter.submitList(state.stationListItems)

                    val isListEmpty = state.stationListItems.isEmpty() && !state.isLoading
                    noStationsTextView.visibility = if (isListEmpty) View.VISIBLE else View.GONE
                    sortControlsContainer.visibility = if (isListEmpty) View.GONE else View.VISIBLE
                    stationRecyclerView.visibility = if (isListEmpty) View.GONE else View.VISIBLE


                    // --- Handle Detail Card Display ---
                    if (state.selectedStationDetails != null) {
                        // Details are available, populate and show the card
                        detailCard.visibility = View.VISIBLE
                        detailProgressBar.visibility = View.GONE
                        detailContentLayout.visibility = View.VISIBLE
                        populateDetailCard(state.selectedStationDetails)
                    } else if (state.isFetchingDetails) { // You'll need to add this flag to your VM
                        // A station was selected, but we are waiting for data
                        detailCard.visibility = View.VISIBLE
                        detailProgressBar.visibility = View.VISIBLE
                        detailContentLayout.visibility = View.GONE
                    } else {
                        // No station selected or card was closed
                        detailCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun populateDetailCard(data: WaterData) {
        detailDataContainer.removeAllViews() // Clear previous data

        val tideInfo = data.tideData
        val levelInfo = data.waterLevel

        if (tideInfo != null && tideInfo.isNotEmpty()) {
            val firstTide = tideInfo.first()
            detailTitle.text = "Tide Predictions"
            detailSubtitle.text = "For ${firstTide.stationName}"
            firstTide.predictions.forEach { tide ->
                val tideTextView = TextView(requireContext())
                val tideType = if (tide.type == "H") "High" else "Low"
                tideTextView.text = "$tideType Tide: ${formatTideTime(tide.time)} (${tide.height} ft)"
                tideTextView.textSize = 16f
                tideTextView.setPadding(0, 4, 0, 4)
                detailDataContainer.addView(tideTextView)
            }
        } else if (levelInfo != null && levelInfo.isNotEmpty()) {
            val firstLevel = levelInfo.first()
            detailTitle.text = "River/Lake Level"
            detailSubtitle.text = firstLevel.siteName
            val levelTextView = TextView(requireContext())
            levelTextView.text = "${firstLevel.variableName}: ${firstLevel.value} ${firstLevel.unit}"
            levelTextView.textSize = 16f
            detailDataContainer.addView(levelTextView)
        }
    }

    private fun formatTideTime(dateTimeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTimeString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }
}

// --- RecyclerView Adapter for the list of water stations ---
class StationListAdapter(private val onStationClicked: (StationListItem) -> Unit) :
    ListAdapter<StationListItem, StationListAdapter.StationViewHolder>(StationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.station_list_item, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station)
        holder.itemView.setOnClickListener { onStationClicked(station) }
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        private val distanceTextView: TextView = itemView.findViewById(R.id.stationDistanceTextView)

        fun bind(stationItem: StationListItem) {
            nameTextView.text = stationItem.entity.name
            distanceTextView.text = String.format("%.1f miles", stationItem.distance)
        }
    }
}

// --- DiffUtil for efficient list updates ---
class StationDiffCallback : DiffUtil.ItemCallback<StationListItem>() {
    override fun areItemsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        return oldItem.entity.id == newItem.entity.id
    }

    override fun areContentsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        return oldItem == newItem
    }
}