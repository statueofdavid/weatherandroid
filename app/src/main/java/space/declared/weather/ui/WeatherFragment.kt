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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import space.declared.weather.R
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherFragment : Fragment() {

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

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> getCurrentLocationWeather()
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> getCurrentLocationWeather()
            else -> Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
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
    }

    private fun initializeViews(view: View) {
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
            // When a city is clicked, fetch its weather
            val displayName = if (city.state != null && city.state.isNotEmpty()) "${city.name}, ${city.state}" else city.name
            viewModel.fetchWeatherAndWaterData(city.latitude, city.longitude, displayName)

            // Hide the keyboard and the search results list
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
            citySearch.clearFocus()
            citySearch.text.clear()
            viewModel.onCitySelectionDone()
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
                    searchHandler.postDelayed(searchRunnable!!, 500L) // 500ms delay
                } else {
                    viewModel.clearSearchAndHideCard()                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                weatherDataContainer.visibility = View.GONE
            } else {
                weatherDataContainer.visibility = View.VISIBLE
            }
        }

        viewModel.mainScreenState.observe(viewLifecycleOwner) { state ->
            progressBar.visibility = View.GONE
            weatherDataContainer.visibility = View.VISIBLE
            cityName.text = state.cityName
            setupForecastTabs(state.fullForecast)
        }

        viewModel.selectedDayWeather.observe(viewLifecycleOwner) { details ->
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

        viewModel.cityList.observe(viewLifecycleOwner) { cities ->
            if (cities.isNotEmpty()) {
                citySearchAdapter.submitList(cities)
            }
        }

        viewModel.showCitySearchCard.observe(viewLifecycleOwner) { show ->
            citySearchCardContainer.visibility = if (show) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                weatherDataContainer.visibility = View.GONE
            }
        }
    }

    private fun setupForecastTabs(forecast: List<DailyForecast>) {
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
                    viewModel.fetchWeatherAndWaterData(location.latitude, location.longitude, "Current Location")
                } else {
                    Toast.makeText(requireContext(), "Could not retrieve location. Is GPS on?", Toast.LENGTH_SHORT).show()
                }
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