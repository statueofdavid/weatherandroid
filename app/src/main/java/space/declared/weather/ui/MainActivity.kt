package space.declared.weather.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import space.declared.weather.R
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val viewModel: WeatherViewModel by viewModels()

    // UI Elements
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

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> getCurrentLocationWeather()
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> getCurrentLocationWeather()
            else -> Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        initializeViews()
        setupObservers()

        searchButton.setOnClickListener {
            val city = citySearch.text.toString().trim()
            if (city.isNotEmpty()) {
                viewModel.onCitySearch(city)
            } else {
                Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
            }
        }

        locationButton.setOnClickListener { getCurrentLocationWeather() }
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        weatherDataContainer = findViewById(R.id.weatherDataContainer)
        citySearch = findViewById(R.id.citySearch)
        searchButton = findViewById(R.id.searchButton)
        locationButton = findViewById(R.id.locationButton)
        forecastTabs = findViewById(R.id.forecastTabs)
        cityName = findViewById(R.id.cityName)
        temperature = findViewById(R.id.temperature)
        weatherDescription = findViewById(R.id.weatherDescription)
        uvIndex = findViewById(R.id.uvIndex)
        sunrise = findViewById(R.id.sunrise)
        sunset = findViewById(R.id.sunset)
        daylight = findViewById(R.id.daylight)
        pressure = findViewById(R.id.pressure)
        humidity = findViewById(R.id.humidity)
        precipitation = findViewById(R.id.precipitation)
        cloudCover = findViewById(R.id.cloudCover)
        wind = findViewById(R.id.wind)
        windGusts = findViewById(R.id.windGusts)
    }

    private fun setupObservers() {
        // Observer for the overall weather state (city name and forecast for tabs)
        viewModel.weatherScreenState.observe(this) { state ->
            weatherDataContainer.visibility = View.VISIBLE
            cityName.text = state.cityName
            setupForecastTabs(state.fullForecast)
        }

        // Observer for the detailed weather of the selected day
        viewModel.selectedDayWeather.observe(this) { details ->
            temperature.text = "${details.tempMax}° / ${details.tempMin}°C"
            weatherDescription.text = details.weatherDescription
            uvIndex.text = details.uvIndex
            sunrise.text = details.sunrise
            sunset.text = details.sunset
            daylight.text = details.daylight

            // These details are only available for the current day
            pressure.text = details.pressure ?: "Pressure: N/A"
            humidity.text = details.humidity ?: "Humidity: N/A"
            precipitation.text = details.precipitationChance ?: "Precipitation Chance: N/A"
            cloudCover.text = details.cloudCover ?: "Cloud Cover: N/A"
            wind.text = details.wind ?: "Wind: N/A"
            windGusts.text = details.windGusts ?: "Gusts: N/A"
        }

        viewModel.cityList.observe(this) { cities ->
            when {
                cities.size > 1 -> showCitySelectionDialog(cities)
                cities.size == 1 -> {
                    val city = cities.first()
                    viewModel.fetchWeatherForLocation(city.latitude, city.longitude, city.name)
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                weatherDataContainer.visibility = View.GONE
            }
        }

        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupForecastTabs(forecast: List<DailyForecast>) {
        forecastTabs.removeAllTabs()
        forecast.forEachIndexed { index, day ->
            val tab = forecastTabs.newTab().setText(formatDayForTab(day.date, index))
            forecastTabs.addTab(tab)
        }
        forecastTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { viewModel.onDaySelected(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun getCurrentLocationWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.fetchWeatherForLocation(location.latitude, location.longitude, "Current Location")
                } else {
                    Toast.makeText(this, "Could not retrieve location. Is GPS on?", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun showCitySelectionDialog(cities: List<CityResult>) {
        val cityNames = cities.map {
            if (it.state != null && it.state.isNotEmpty()) "${it.name}, ${it.state}, ${it.country}"
            else "${it.name}, ${it.country}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select a City")
            .setItems(cityNames) { dialog, which ->
                val selectedCity = cities[which]
                val displayName = if (selectedCity.state != null && selectedCity.state.isNotEmpty()) "${selectedCity.name}, ${selectedCity.state}"
                else selectedCity.name
                viewModel.fetchWeatherForLocation(selectedCity.latitude, selectedCity.longitude, displayName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
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