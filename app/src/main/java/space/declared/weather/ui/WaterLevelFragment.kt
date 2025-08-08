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
import space.declared.weather.data.WaterData
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * This fragment displays a list of water stations and shows a detail card
 * for a selected station on the same screen.
 */
class WaterLevelFragment : Fragment() {

    // Correctly reference WeatherViewModel
    private val viewModel: MainViewModel by activityViewModels()

    // --- List UI Elements ---
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
    private lateinit var closeDetailButton: Button

    // --- Detail Content Views ---
    private lateinit var tideDataContainer: LinearLayout
    private lateinit var tideStationName: TextView
    private lateinit var tidePredictions: TextView
    private lateinit var riverDataContainer: LinearLayout
    private lateinit var riverSiteName: TextView
    private lateinit var riverLevel: TextView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
        closeDetailButton = view.findViewById(R.id.closeDetailButton)

        // Specific Detail Content Views
        tideDataContainer = view.findViewById(R.id.tideDataContainer)
        tideStationName = view.findViewById(R.id.tideStationName)
        tidePredictions = view.findViewById(R.id.tidePredictions)
        riverDataContainer = view.findViewById(R.id.riverDataContainer)
        riverSiteName = view.findViewById(R.id.riverSiteName)
        riverLevel = view.findViewById(R.id.riverLevel)
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter { stationItem ->
            viewModel.onStationSelected(stationItem)
        }
        stationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        stationRecyclerView.adapter = stationListAdapter
    }

    private fun setupClickListeners() {
        sortByDistanceButton.setOnClickListener { viewModel.onSortStationsByDistance() }
        sortByNameButton.setOnClickListener { viewModel.onSortStationsByName() }
        // Correctly call the ViewModel function to handle closing the card
        closeDetailButton.setOnClickListener { viewModel.onDetailCardClosed() }
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
                    if (state.isFetchingDetails) {
                        detailCard.visibility = View.VISIBLE
                        detailProgressBar.visibility = View.VISIBLE
                        detailContentLayout.visibility = View.GONE
                    } else if (state.selectedStationDetails != null) {
                        detailCard.visibility = View.VISIBLE
                        detailProgressBar.visibility = View.GONE
                        detailContentLayout.visibility = View.VISIBLE
                        populateDetailCard(state.selectedStationDetails)
                    } else {
                        detailCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun populateDetailCard(data: WaterData) {
        val tideInfo = data.tideData
        val levelInfo = data.waterLevel

        if (tideInfo != null && tideInfo.isNotEmpty()) {
            // Show tide data
            tideDataContainer.visibility = View.VISIBLE
            riverDataContainer.visibility = View.GONE

            val firstTide = tideInfo.first()
            tideStationName.text = "Tide Predictions: ${firstTide.stationName}"
            tidePredictions.text = firstTide.predictions.joinToString("\n") { tide ->
                val tideType = if (tide.type == "H") "High" else "Low"
                "$tideType Tide: ${formatTideTime(tide.time)} (${tide.height} ft)"
            }
        } else if (levelInfo != null && levelInfo.isNotEmpty()) {
            // Show river/lake data
            tideDataContainer.visibility = View.GONE
            riverDataContainer.visibility = View.VISIBLE

            val firstLevel = levelInfo.first()
            riverSiteName.text = firstLevel.siteName
            riverLevel.text = "${firstLevel.variableName}: ${firstLevel.value} ${firstLevel.unit}"
        } else {
            // No data to show, hide both containers
            tideDataContainer.visibility = View.GONE
            riverDataContainer.visibility = View.GONE
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

// --- RecyclerView Adapter and DiffUtil ---
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
            // Use the correct property 'stationName' from the entity
            nameTextView.text = stationItem.entity.name
            distanceTextView.text = String.format("%.1f miles", stationItem.distance)
        }
    }
}

class StationDiffCallback : DiffUtil.ItemCallback<StationListItem>() {
    override fun areItemsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        // Use the correct property 'stationId' for comparison
        return oldItem.entity.id == newItem.entity.id
    }

    override fun areContentsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        return oldItem == newItem
    }
}