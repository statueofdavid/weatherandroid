package space.declared.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import space.declared.weather.data.WeatherRepository
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// New data class to hold all parsed weather information together
data class WeatherData(
    val cityName: String,
    val currentTemp: Int,
    val weatherDescription: String,
    val uvIndex: String,
    val sunrise: String,
    val sunset: String,
    val daylight: String,
    val pressure: String,
    val humidity: String,
    val precipitationChance: String,
    val cloudCover: String,
    val wind: String,
    val windGusts: String,
    val forecast: List<DailyForecast>
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    // The repository is our single source of truth for data
    private val repository: WeatherRepository

    // LiveData to hold the list of cities for the selection dialog
    private val _cityList = MutableLiveData<List<CityResult>>()
    val cityList: LiveData<List<CityResult>> = _cityList

    // LiveData to hold all the weather data for the UI to display
    private val _weatherData = MutableLiveData<WeatherData>()
    val weatherData: LiveData<WeatherData> = _weatherData

    // LiveData to manage the visibility of the progress bar
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData to show error messages to the user
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    init {
        // Initialize the repository. This is a simple form of dependency injection.
        val remoteDataSource = OpenMeteoRemoteDataSource(application)
        repository = WeatherRepository(remoteDataSource)
    }

    /**
     * Called by the UI when the user searches for a city.
     */
    fun onCitySearch(query: String) {
        _isLoading.value = true
        repository.getCityList(query, object : OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>> {
            override fun onSuccess(result: List<CityResult>) {
                if (result.isEmpty()) {
                    _error.value = "City not found"
                    _isLoading.value = false
                } else {
                    _cityList.value = result
                    // Don't hide loading indicator yet, wait for user to select a city
                }
            }

            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    /**
     * Called by the UI when a city is selected or location is fetched directly.
     */
    fun fetchWeatherForLocation(latitude: Double, longitude: Double, name: String) {
        _isLoading.value = true
        repository.getWeatherData(latitude, longitude, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                // Parse the complex JSON object into our clean WeatherData class
                val parsedData = parseWeatherData(result, name)
                _weatherData.value = parsedData
                _isLoading.value = false
            }

            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    /**
     * A private helper function to parse the large JSON response from the API.
     * This keeps the parsing logic separate and clean.
     */
    private fun parseWeatherData(response: JSONObject, name: String): WeatherData {
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

        // Daily Weather
        val daily = response.getJSONObject("daily")
        val uv = daily.getJSONArray("uv_index_max").getDouble(0)
        val sunriseStr = daily.getJSONArray("sunrise").getString(0)
        val sunsetStr = daily.getJSONArray("sunset").getString(0)
        val daylightSeconds = daily.getJSONArray("daylight_duration").getDouble(0)

        // 10-Day Forecast
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

        // Return a single, clean data object
        return WeatherData(
            cityName = name,
            currentTemp = temp.roundToInt(),
            weatherDescription = getWeatherDescriptionFromCode(weatherCode),
            uvIndex = "UV Index: ${uv.roundToInt()} (${getUvIndexRisk(uv)})",
            sunrise = "Sunrise: ${formatTime(sunriseStr)}",
            sunset = "Sunset: ${formatTime(sunsetStr)}",
            daylight = "Daylight: ${String.format("%.1f", daylightSeconds / 3600)} hours",
            pressure = "Pressure: ${press.roundToInt()} hPa",
            humidity = "Humidity: $hum%",
            precipitationChance = "Precipitation Chance: $precipChance%",
            cloudCover = "Cloud Cover: $cloud%",
            wind = "Wind: ${getWindDirection(windDir)} at ${windSpeed.roundToInt()} km/h",
            windGusts = "Gusts: ${windGust.roundToInt()} km/h",
            forecast = forecastList
        )
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
