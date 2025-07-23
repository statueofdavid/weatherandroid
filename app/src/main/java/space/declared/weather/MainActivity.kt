package space.declared.weather

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// Data class for a single day in the forecast
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double
)

// Data class for a city search result
data class CityResult(
    val name: String,
    val state: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun fromJson(json: JSONObject): CityResult {
            return CityResult(
                name = json.getString("name"),
                state = json.optString("admin1", null),
                country = json.getString("country"),
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude")
            )
        }
    }
}

class MainActivity : AppCompatActivity() {

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

    private lateinit var requestQueue: RequestQueue
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
        requestQueue = Volley.newRequestQueue(this)

        searchButton.setOnClickListener {
            val city = citySearch.text.toString().trim()
            if (city.isNotEmpty()) fetchCityList(city)
            else Toast.makeText(this, "Please enter a city name", Toast.LENGTH_SHORT).show()
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

    private fun getCurrentLocationWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            progressBar.visibility = View.VISIBLE
            weatherDataContainer.visibility = View.GONE
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fetchWeatherData(location.latitude, location.longitude, "Current Location")
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Could not retrieve location. Is GPS on?", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun fetchCityList(city: String) {
        progressBar.visibility = View.VISIBLE
        weatherDataContainer.visibility = View.GONE

        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$city&count=5&language=en&format=json"
        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val resultsArray = response.optJSONArray("results")
                    if (resultsArray == null || resultsArray.length() == 0) {
                        Toast.makeText(this, "City not found", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        return@JsonObjectRequest
                    }

                    val cityResults = List(resultsArray.length()) { i -> CityResult.fromJson(resultsArray.getJSONObject(i)) }

                    when {
                        cityResults.size > 1 -> {
                            progressBar.visibility = View.GONE
                            showCitySelectionDialog(cityResults)
                        }
                        cityResults.size == 1 -> {
                            val selectedCity = cityResults.first()
                            fetchWeatherData(selectedCity.latitude, selectedCity.longitude, selectedCity.name)
                        }
                    }
                } catch (e: JSONException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error parsing city data", Toast.LENGTH_SHORT).show()
                }
            },
            {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching city data", Toast.LENGTH_SHORT).show()
            }
        )
        requestQueue.add(jsonObjectRequest)
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
                progressBar.visibility = View.VISIBLE
                weatherDataContainer.visibility = View.GONE
                fetchWeatherData(selectedCity.latitude, selectedCity.longitude, displayName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double, name: String) {
        val currentParams = "temperature_2m,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
        val hourlyParams = "relative_humidity_2m,precipitation_probability,cloud_cover"
        // Updated daily params to get 10-day forecast data
        val dailyParams = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,uv_index_max"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=$currentParams&hourly=$hourlyParams&daily=$dailyParams&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm&timezone=auto&forecast_days=10"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                progressBar.visibility = View.GONE
                weatherDataContainer.visibility = View.VISIBLE
                try {
                    // Current Weather
                    val current = response.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m")
                    val weatherCode = current.getInt("weather_code")
                    val press = current.getDouble("pressure_msl")
                    val windSpeed = current.getDouble("wind_speed_10m")
                    val windDir = current.getDouble("wind_direction_10m")
                    val windGust = current.getDouble("wind_gusts_10m")

                    // Hourly Weather
                    val hourly = response.getJSONObject("hourly")
                    val currentHourIndex = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val hum = hourly.getJSONArray("relative_humidity_2m").getInt(currentHourIndex)
                    val precipChance = hourly.getJSONArray("precipitation_probability").getInt(currentHourIndex)
                    val cloud = hourly.getJSONArray("cloud_cover").getInt(currentHourIndex)

                    // Daily Weather (Today)
                    val daily = response.getJSONObject("daily")
                    val uv = daily.getJSONArray("uv_index_max").getDouble(0)
                    val sunriseStr = daily.getJSONArray("sunrise").getString(0)
                    val sunsetStr = daily.getJSONArray("sunset").getString(0)
                    val daylightSeconds = daily.getJSONArray("daylight_duration").getDouble(0)

                    // Update UI for current weather
                    cityName.text = name
                    temperature.text = "${temp.roundToInt()}°C"
                    weatherDescription.text = getWeatherDescriptionFromCode(weatherCode)
                    uvIndex.text = "UV Index: ${uv.roundToInt()} (${getUvIndexRisk(uv)})"
                    sunrise.text = "Sunrise: ${formatTime(sunriseStr)}"
                    sunset.text = "Sunset: ${formatTime(sunsetStr)}"
                    daylight.text = "Daylight: ${String.format("%.1f", daylightSeconds / 3600)} hours"
                    pressure.text = "Pressure: ${press.roundToInt()} hPa"
                    humidity.text = "Humidity: $hum%"
                    precipitation.text = "Precipitation Chance: $precipChance%"
                    cloudCover.text = "Cloud Cover: $cloud%"
                    wind.text = "Wind: ${getWindDirection(windDir)} at ${windSpeed.roundToInt()} km/h"
                    windGusts.text = "Gusts: ${windGust.roundToInt()} km/h"

                    // --- Parse and display 10-day forecast ---
                    val forecastList = mutableListOf<DailyForecast>()
                    val timeArray = daily.getJSONArray("time")
                    val weatherCodeArray = daily.getJSONArray("weather_code")
                    val tempMaxArray = daily.getJSONArray("temperature_2m_max")
                    val tempMinArray = daily.getJSONArray("temperature_2m_min")

                    for (i in 0 until timeArray.length()) {
                        forecastList.add(
                            DailyForecast(
                                date = timeArray.getString(i),
                                weatherCode = weatherCodeArray.getInt(i),
                                tempMax = tempMaxArray.getDouble(i),
                                tempMin = tempMinArray.getDouble(i)
                            )
                        )
                    }
                    forecastAdapter.updateData(forecastList)

                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error parsing weather data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                progressBar.visibility = View.GONE
                weatherDataContainer.visibility = View.GONE
                error.printStackTrace()
                Toast.makeText(this, "Error fetching weather data", Toast.LENGTH_SHORT).show()
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    // --- HELPER FUNCTIONS ---

    private fun formatTime(dateTimeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTimeString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }

    private fun getUvIndexRisk(uv: Double): String {
        return when {
            uv < 3 -> "Low"
            uv < 6 -> "Moderate"
            uv < 8 -> "High"
            uv < 11 -> "Very High"
            else -> "Extreme"
        }
    }

    private fun getWindDirection(degrees: Double): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return directions[floor((degrees + 11.25) / 22.5).toInt() % 16]
    }

    private fun getWeatherDescriptionFromCode(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            80, 81, 82 -> "Rain showers"
            95 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}

// --- RecyclerView Adapter for the Forecast ---
class ForecastAdapter(private var forecastList: List<DailyForecast>) :
    RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    // Describes an item view and metadata about its place within the RecyclerView.
    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayTextView: TextView = view.findViewById(R.id.dayTextView)
        val weatherIconImageView: ImageView = view.findViewById(R.id.weatherIconImageView)
        val tempTextView: TextView = view.findViewById(R.id.tempTextView)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.forecast_list_item, parent, false)
        return ForecastViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val forecast = forecastList[position]

        // Set the day of the week
        holder.dayTextView.text = formatDay(forecast.date)

        // Set the temperature range
        holder.tempTextView.text = "${forecast.tempMax.roundToInt()}° / ${forecast.tempMin.roundToInt()}°"

        // Set the weather icon
        holder.weatherIconImageView.setImageResource(getIconForWeatherCode(forecast.weatherCode))
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = forecastList.size

    // Helper function to update the data in the adapter
    fun updateData(newForecastList: List<DailyForecast>) {
        forecastList = newForecastList
        notifyDataSetChanged()
    }

    // Helper function to format the date string to a 3-letter day
    private fun formatDay(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }

    // Helper function to get a drawable resource for a weather code
    private fun getIconForWeatherCode(code: Int): Int {
        return when (code) {
            0 -> R.drawable.ic_clear_day // You need to create these drawables
            1, 2, 3 -> R.drawable.ic_cloudy
            45, 48 -> R.drawable.ic_fog
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_rain
            95 -> R.drawable.ic_thunderstorm
            else -> R.drawable.ic_cloudy // Default icon
        }
    }
}
