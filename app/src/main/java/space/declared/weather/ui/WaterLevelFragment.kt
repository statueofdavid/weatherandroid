package space.declared.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import space.declared.weather.R
import java.text.SimpleDateFormat
import java.util.Locale

class WaterLevelFragment : Fragment() {

    // Share the same ViewModel instance from the MainActivity
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var noDataTextView: TextView
    private lateinit var waterDataScrollView: NestedScrollView
    private lateinit var waterDataTitle: TextView
    private lateinit var waterDataSubtitle: TextView
    private lateinit var waterDataContent: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_water_level, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupObservers()
    }

    private fun initializeViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        noDataTextView = view.findViewById(R.id.noDataTextView)
        waterDataScrollView = view.findViewById(R.id.waterDataScrollView)
        waterDataTitle = view.findViewById(R.id.waterDataTitle)
        waterDataSubtitle = view.findViewById(R.id.waterDataSubtitle)
        waterDataContent = view.findViewById(R.id.waterDataContent)
    }

    private fun setupObservers() {
        // Observe the water data from the shared ViewModel
        viewModel.waterData.observe(viewLifecycleOwner) { data ->
            if (data == null) {
                // No data is available for this location
                waterDataScrollView.visibility = View.GONE
                noDataTextView.visibility = View.VISIBLE
            } else {
                noDataTextView.visibility = View.GONE
                waterDataScrollView.visibility = View.VISIBLE
                waterDataContent.removeAllViews() // Clear any old data

                if (data.isTideData && data.tidePredictions != null) {
                    waterDataTitle.text = "Tide Predictions"
                    // Use the main city name from the other LiveData
                    waterDataSubtitle.text = "For ${viewModel.mainScreenState.value?.cityName}"

                    data.tidePredictions.forEach { tide ->
                        val tideTextView = TextView(requireContext())
                        val tideType = if (tide.type == "H") "High" else "Low"
                        tideTextView.text = "$tideType Tide: ${formatTideTime(tide.time)} (${tide.height} ft)"
                        tideTextView.textSize = 18f
                        tideTextView.setPadding(0, 8, 0, 8)
                        waterDataContent.addView(tideTextView)
                    }
                } else if (!data.isTideData && data.waterLevel != null) {
                    waterDataTitle.text = "River/Lake Level"
                    waterDataSubtitle.text = data.waterLevel.siteName

                    val levelTextView = TextView(requireContext())
                    levelTextView.text = "${data.waterLevel.variableName}: ${data.waterLevel.value} ${data.waterLevel.unit}"
                    levelTextView.textSize = 18f
                    waterDataContent.addView(levelTextView)
                }
            }
        }

        // Also observe the loading state to show/hide the progress bar
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                noDataTextView.visibility = View.GONE
                waterDataScrollView.visibility = View.GONE
            }
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