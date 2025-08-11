package space.declared.weather.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import space.declared.weather.R
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherFragment : Fragment() {

    // Use the refactored WeatherViewModel
    private val viewModel: MainViewModel by activityViewModels()

    // --- UI Elements ---
    private lateinit var progressBar: ProgressBar
    private lateinit var weatherDataContainer: LinearLayout
    private lateinit var citySearch: EditText
    private lateinit var locationButton: Button
    private lateinit var forecastTabs: TabLayout
    private lateinit var cityName: TextView
    private lateinit var temperature: TextView
    private lateinit var weatherDescription: TextView
    private lateinit var uvIndex: TextView
    private lateinit var sunrise: TextView
    private lateinit var sunset: TextView
    private lateinit var daylight: TextView
    private lateinit var pressure: TextView
    private lateinit var humidity: TextView
    private lateinit var precipitation: TextView
    private lateinit var cloudCover: TextView
    private lateinit var wind: TextView
    private lateinit var windGusts: TextView
    private lateinit var citySearchRecyclerView: RecyclerView
    private lateinit var citySearchAdapter: CitySearchAdapter
    private lateinit var citySearchCardContainer: CardView
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private lateinit var locationPermissionRequest:
            androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> getCurrentLocationWeather()
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> getCurrentLocationWeather()
                else -> Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_weather, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        setupSearch()
        setupObservers()

        // Trigger initial data load if the ViewModel has no data yet
        if (viewModel.uiState.value.cityName == null && !viewModel.uiState.value.isLoading) {
            getCurrentLocationWeather()
        }
    }

    private fun initializeViews(view: View) {
        // This function remains the same, binding all the view elements
        progressBar = view.findViewById(R.id.progressBar)
        weatherDataContainer = view.findViewById(R.id.weatherDataContainer)
        citySearch = view.findViewById(R.id.citySearch)
        citySearchRecyclerView = view.findViewById(R.id.citySearchRecyclerView)
        citySearchCardContainer = view.findViewById(R.id.citySearchCardContainer)
        locationButton = view.findViewById(R.id.locationButton)
        forecastTabs = view.findViewById(R.id.forecastTabs)
        cityName = view.findViewById(R.id.cityName)
        temperature = view.findViewById(R.id.temperature)
        weatherDescription = view.findViewById(R.id.weatherDescription)
        uvIndex = view.findViewById(R.id.uvIndex)
        sunrise = view.findViewById(R.id.sunrise)
        sunset = view.findViewById(R.id.sunset)
        daylight = view.findViewById(R.id.daylight)
        pressure = view.findViewById(R.id.pressure)
        humidity = view.findViewById(R.id.humidity)
        precipitation = view.findViewById(R.id.precipitation)
        cloudCover = view.findViewById(R.id.cloudCover)
        wind = view.findViewById(R.id.wind)
        windGusts = view.findViewById(R.id.windGusts)
    }

    private fun setupClickListeners() {
        locationButton.setOnClickListener { getCurrentLocationWeather() }
    }

    private fun setupSearch() {
        citySearchAdapter = CitySearchAdapter { city ->
            val displayName = if (!city.state.isNullOrEmpty()) "${city.name}, ${city.state}" else city.name
            viewModel.fetchInitialData(city.latitude, city.longitude, displayName)

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
            citySearch.clearFocus()
            citySearch.text.clear()
            viewModel.onCitySelectionDialogShown() // Hides the search results
        }
        citySearchRecyclerView.adapter = citySearchAdapter

        citySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
            }
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length > 2) {
                    searchRunnable = Runnable { viewModel.onCitySearch(query) }
                    searchHandler.postDelayed(searchRunnable!!, 500L) // 500ms debounce
                } else {
                    viewModel.onCitySelectionDialogShown() // Clear results for short queries
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // --- Loading State ---
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    weatherDataContainer.visibility = if (state.isLoading || state.error != null) View.GONE else View.VISIBLE

                    // --- Error State ---
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    }

                    // --- City Search Results ---
                    citySearchAdapter.submitList(state.cityList)
                    citySearchCardContainer.visibility = if (state.cityList.isNotEmpty()) View.VISIBLE else View.GONE

                    // --- Main Weather Data ---
                    state.cityName?.let { cityName.text = it }
                    if (state.fullForecast.isNotEmpty()) {
                        setupForecastTabs(state.fullForecast)
                    }

                    // --- Detailed Daily Weather ---
                    state.selectedDayWeather?.let { details ->
                        temperature.text = "${details.tempMax} / ${details.tempMin}"
                        weatherDescription.text = details.weatherDescription
                        uvIndex.text = details.uvIndex
                        sunrise.text = details.sunrise
                        sunset.text = details.sunset
                        daylight.text = details.daylight
                        pressure.text = details.pressure ?: "Pressure: N/A"
                        humidity.text = details.humidity ?: "Humidity: N/A"
                        precipitation.text = details.precipitationChance ?: "Precipitation Chance: N/A"
                        cloudCover.text = details.cloudCover ?: "Cloud Cover: N/A"
                        wind.text = details.wind ?: "Wind: N/A"
                        windGusts.text = details.windGusts ?: "Gusts: N/A"
                    }
                }
            }
        }
    }

    private fun setupForecastTabs(forecast: List<DailyForecast>) {
        if (forecastTabs.tabCount == forecast.size) return // Avoid re-adding tabs unnecessarily

        forecastTabs.removeAllTabs()
        forecast.forEachIndexed { index, day ->
            val tab = forecastTabs.newTab().setText(formatDayForTab(day.date, index))
            forecastTabs.addTab(tab)
        }
        forecastTabs.clearOnTabSelectedListeners()
        forecastTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { viewModel.onDaySelected(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun getCurrentLocationWeather() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.fetchInitialData(location.latitude, location.longitude, "Current Location")
                } else {
                    Toast.makeText(requireContext(), "Could not retrieve location. Is GPS on?", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to get location.", Toast.LENGTH_SHORT).show()
            }
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun formatDayForTab(dateString: String, index: Int): String {
        if (index == 0) return "Today"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }
}

// You will need a CitySearchAdapter similar to this:
/*
class CitySearchAdapter(private val onCityClicked: (CityResult) -> Unit) :
    ListAdapter<CityResult, CitySearchAdapter.CityViewHolder>(CityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.city_search_item, parent, false) // Ensure you have this layout
        return CityViewHolder(view)
    }

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        val city = getItem(position)
        holder.bind(city)
        holder.itemView.setOnClickListener { onCityClicked(city) }
    }

    class CityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.cityNameTextView) // Adjust ID

        fun bind(city: CityResult) {
            val displayName = if (!city.state.isNullOrEmpty()) {
                "${city.name}, ${city.state}, ${city.country}"
            } else {
                "${city.name}, ${city.country}"
            }
            nameTextView.text = displayName
        }
    }
}

class CityDiffCallback : DiffUtil.ItemCallback<CityResult>() {
    override fun areItemsTheSame(oldItem: CityResult, newItem: CityResult): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CityResult, newItem: CityResult): Boolean {
        return oldItem == newItem
    }
}
*/
