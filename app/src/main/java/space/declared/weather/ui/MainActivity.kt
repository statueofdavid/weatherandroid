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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import space.declared.weather.R
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // Get a reference to the ViewModel using the AndroidX ktx library
    private val viewModel: WeatherViewModel by viewModels()

    // UI Elements
    private lateinit var progressBar: ProgressBar
    private lateinit var weatherDataContainer: LinearLayout
    private lateinit var citySearch: EditText
    private lateinit var searchButton: Button
    private lateinit var locationButton: Button
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
    private lateinit var forecastRecyclerView: RecyclerView
    private lateinit var forecastAdapter: ForecastAdapter

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
        setupRecyclerView()
        setupObservers() // Set up listeners for LiveData from the ViewModel

        searchButton.setOnClickListener {
            val city = citySearch.text.toString().trim()
            if (city.isNotEmpty()) {
                // Tell the ViewModel to start the search
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
        forecastRecyclerView = findViewById(R.id.forecastRecyclerView)
    }

    private fun setupRecyclerView() {
        forecastAdapter = ForecastAdapter(emptyList())
        forecastRecyclerView.layoutManager = LinearLayoutManager(this)
        forecastRecyclerView.adapter = forecastAdapter
    }

    /**
     * This is the core of the new architecture. We observe the LiveData objects
     * in the ViewModel and update the UI whenever the data changes.
     */
    private fun setupObservers() {
        // Observer for the main weather data
        viewModel.weatherData.observe(this) { data ->
            weatherDataContainer.visibility = View.VISIBLE
            cityName.text = data.cityName
            temperature.text = "${data.currentTemp}°C"
            weatherDescription.text = data.weatherDescription
            uvIndex.text = data.uvIndex
            sunrise.text = data.sunrise
            sunset.text = data.sunset
            daylight.text = data.daylight
            pressure.text = data.pressure
            humidity.text = data.humidity
            precipitation.text = data.precipitationChance
            cloudCover.text = data.cloudCover
            wind.text = data.wind
            windGusts.text = data.windGusts
            forecastAdapter.updateData(data.forecast)
        }

        // Observer for the city list to show the selection dialog
        viewModel.cityList.observe(this) { cities ->
            when {
                cities.size > 1 -> showCitySelectionDialog(cities)
                cities.size == 1 -> {
                    val city = cities.first()
                    viewModel.fetchWeatherForLocation(city.latitude, city.longitude, city.name)
                }
            }
        }

        // Observer for the loading state (progress bar)
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                weatherDataContainer.visibility = View.GONE
            }
        }

        // Observer for error messages
        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getCurrentLocationWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    // Tell the ViewModel to fetch weather for the current location
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
                // Tell the ViewModel to fetch weather for the selected city
                viewModel.fetchWeatherForLocation(selectedCity.latitude, selectedCity.longitude, displayName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

// --- RecyclerView Adapter for the Forecast ---
class ForecastAdapter(private var forecastList: List<DailyForecast>) :
    RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayTextView: TextView = view.findViewById(R.id.dayTextView)
        val weatherIconImageView: ImageView = view.findViewById(R.id.weatherIconImageView)
        val tempTextView: TextView = view.findViewById(R.id.tempTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.forecast_list_item, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val forecast = forecastList[position]
        holder.dayTextView.text = formatDay(forecast.date)
        holder.tempTextView.text = "${forecast.tempMax.roundToInt()}° / ${forecast.tempMin.roundToInt()}°"
        holder.weatherIconImageView.setImageResource(getIconForWeatherCode(forecast.weatherCode))
    }

    override fun getItemCount() = forecastList.size

    fun updateData(newForecastList: List<DailyForecast>) {
        forecastList = newForecastList
        notifyDataSetChanged()
    }

    private fun formatDay(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }

    private fun getIconForWeatherCode(code: Int): Int {
        return when (code) {
            0 -> R.drawable.ic_clear_day
            1, 2, 3 -> R.drawable.ic_cloudy
            45, 48 -> R.drawable.ic_fog
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_rain
            95 -> R.drawable.ic_thunderstorm
            else -> R.drawable.ic_cloudy
        }
    }
}
