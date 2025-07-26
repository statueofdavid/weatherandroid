package space.declared.weather.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private lateinit var searchButton: Button
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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_weather, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        setupObservers() // All logic is now driven by observers
    }

    // --- View Initialization ---
    private fun initializeViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        weatherDataContainer = view.findViewById(R.id.weatherDataContainer)
        citySearch = view.findViewById(R.id.citySearch)
        searchButton = view.findViewById(R.id.searchButton)
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

    // --- Click Listeners ---
    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val city = citySearch.text.toString().trim()
            if (city.isNotEmpty()) {
                viewModel.onCitySearch(city)
            } else {
                Toast.makeText(requireContext(), "Please enter a city name", Toast.LENGTH_SHORT).show()
            }
        }
        locationButton.setOnClickListener { getCurrentLocationWeather() }
    }

    // --- State Observers (The Core Fix) ---
    private fun setupObservers() {
        // Observer for loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // The loading flag ONLY controls the progress bar visibility.
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // It ALSO hides the content while loading.
            if (isLoading) {
                weatherDataContainer.visibility = View.GONE
            }
        }

        // Observer for the main screen data (city name and forecast tabs)
        viewModel.mainScreenState.observe(viewLifecycleOwner) { state ->
            // This is the point of success. Hide the progress bar and show the content.
            progressBar.visibility = View.GONE
            weatherDataContainer.visibility = View.VISIBLE

            // Populate the city name and setup the forecast tabs
            cityName.text = state.cityName
            setupForecastTabs(state.fullForecast)
        }

        // Observer for the detailed weather of the selected day
        viewModel.selectedDayWeather.observe(viewLifecycleOwner) { details ->
            // This just populates the text views. It does not change visibility.
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

        // Observer for handling the city selection dialog
        viewModel.cityList.observe(viewLifecycleOwner) { cities ->
            if (cities.isNotEmpty()) {
                if (cities.size > 1) {
                    showCitySelectionDialog(cities)
                } else {
                    val city = cities.first()
                    viewModel.fetchWeatherAndWaterData(city.latitude, city.longitude, city.name)
                }
                viewModel.onCitySelectionDialogShown() // Clear the event
            }
        }

        // Observer for errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                // On error, hide the progress bar and the content.
                progressBar.visibility = View.GONE
                weatherDataContainer.visibility = View.GONE
            }
        }
    }

    // --- Helper Functions ---
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

    private fun showCitySelectionDialog(cities: List<CityResult>) {
        val cityNames = cities.map {
            if (it.state != null && it.state.isNotEmpty()) "${it.name}, ${it.state}, ${it.country}"
            else "${it.name}, ${it.country}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select a City")
            .setItems(cityNames) { dialog, which ->
                val selectedCity = cities[which]
                val displayName = if (selectedCity.state != null && selectedCity.state.isNotEmpty()) "${selectedCity.name}, ${selectedCity.state}"
                else selectedCity.name
                viewModel.fetchWeatherAndWaterData(selectedCity.latitude, selectedCity.longitude, displayName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
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