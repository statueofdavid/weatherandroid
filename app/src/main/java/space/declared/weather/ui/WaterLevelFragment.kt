package space.declared.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import space.declared.weather.R
import space.declared.weather.data.TideData
import space.declared.weather.data.WaterLevelData
import java.text.SimpleDateFormat
import java.util.Locale

class WaterLevelFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var noDataTextView: TextView
    private lateinit var sortControlsContainer: LinearLayout
    private lateinit var sortByDistanceButton: Button
    private lateinit var sortByNameButton: Button
    private lateinit var waterLevelRecyclerView: RecyclerView
    private lateinit var waterLevelAdapter: WaterLevelAdapter

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
        progressBar = view.findViewById(R.id.progressBar)
        noDataTextView = view.findViewById(R.id.noDataTextView)
        sortControlsContainer = view.findViewById(R.id.sortControlsContainer)
        sortByDistanceButton = view.findViewById(R.id.sortByDistanceButton)
        sortByNameButton = view.findViewById(R.id.sortByNameButton)
        waterLevelRecyclerView = view.findViewById(R.id.waterLevelRecyclerView)
    }

    private fun setupRecyclerView() {
        waterLevelAdapter = WaterLevelAdapter(emptyList())
        waterLevelRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        waterLevelRecyclerView.adapter = waterLevelAdapter
    }

    private fun setupClickListeners() {
        sortByDistanceButton.setOnClickListener {
            viewModel.onSortByDistanceClicked()
        }
        sortByNameButton.setOnClickListener {
            viewModel.onSortByNameClicked()
        }
    }

    private fun setupObservers() {
        viewModel.waterData.observe(viewLifecycleOwner) { data ->
            val stationList = mutableListOf<Any>()
            data?.tideData?.let { stationList.addAll(it) }
            data?.waterLevel?.let { stationList.addAll(it) }

            if (stationList.isEmpty()) {
                waterLevelRecyclerView.visibility = View.GONE
                sortControlsContainer.visibility = View.GONE
                noDataTextView.visibility = View.VISIBLE
            } else {
                noDataTextView.visibility = View.GONE
                waterLevelRecyclerView.visibility = View.VISIBLE
                sortControlsContainer.visibility = View.VISIBLE
                waterLevelAdapter.updateData(stationList)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                noDataTextView.visibility = View.GONE
                sortControlsContainer.visibility = View.GONE
                waterLevelRecyclerView.visibility = View.GONE
            }
        }
    }
}

// --- RecyclerView Adapter for the Water Level Stations ---
class WaterLevelAdapter(private var stationList: List<Any>) :
    RecyclerView.Adapter<WaterLevelAdapter.StationViewHolder>() {

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.waterDataTitle)
        val subtitle: TextView = view.findViewById(R.id.waterDataSubtitle)
        val contentLayout: LinearLayout = view.findViewById(R.id.waterDataContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.water_station_list_item, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val item = stationList[position]
        holder.contentLayout.removeAllViews() // Clear previous content

        when (item) {
            is TideData -> {
                holder.title.text = "Tide Predictions"
                holder.subtitle.text = "For ${item.stationName}"
                item.predictions.forEach { tide ->
                    val tideTextView = TextView(holder.itemView.context)
                    val tideType = if (tide.type == "H") "High" else "Low"
                    tideTextView.text = "$tideType Tide: ${formatTideTime(tide.time)} (${tide.height} ft)"
                    tideTextView.textSize = 16f
                    tideTextView.setPadding(0, 4, 0, 4)
                    holder.contentLayout.addView(tideTextView)
                }
            }
            is WaterLevelData -> {
                holder.title.text = "River/Lake Level"
                holder.subtitle.text = item.siteName
                val levelTextView = TextView(holder.itemView.context)
                levelTextView.text = "${item.variableName}: ${item.value} ${item.unit}"
                levelTextView.textSize = 16f
                holder.contentLayout.addView(levelTextView)
            }
        }
    }

    override fun getItemCount() = stationList.size

    fun updateData(newStationList: List<Any>) {
        stationList = newStationList
        notifyDataSetChanged()
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