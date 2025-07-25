package space.declared.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import space.declared.weather.data.WaterData
import space.declared.weather.data.WeatherRepository
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A new data class to hold the detailed weather info for a single selected day.
 * This is what will be displayed in the main weather cards.
 */
data class DetailedDayWeather(
    val date: String,
    val tempMax: Int,
    val tempMin: Int,
    val weatherDescription: String,
    val uvIndex: String,
    val sunrise: String,
    val sunset: String,
    val daylight: String,
    // The following fields are from the hourly data for the current day
    val pressure: String?,
    val humidity: String?,
    val precipitationChance: String?,
    val cloudCover: String?,
    val wind: String?,
    val windGusts: String?
)

/**
 * This data class represents the entire state of the weather screen.
 * It's emitted once per successful API call.
 */
data class MainScreenState(
    val cityName: String,
    val fullForecast: List<DailyForecast>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository

    // LiveData for the list of cities from a search
    private val _cityList = MutableLiveData<List<CityResult>>()
    val cityList: LiveData<List<CityResult>> = _cityList

    // LiveData for the overall screen state (city name and 10-day forecast)
    private val _mainScreenState = MutableLiveData<MainScreenState>()
    val mainScreenState: LiveData<MainScreenState> = _mainScreenState

    // LiveData for the detailed weather of the currently selected day
    private val _selectedDayWeather = MutableLiveData<DetailedDayWeather>()
    val selectedDayWeather: LiveData<DetailedDayWeather> = _selectedDayWeather

    // LiveData for the water data (tides or rivers)
    private val _waterData = MutableLiveData<WaterData?>()
    val waterData: LiveData<WaterData?> = _waterData

    // LiveData for loading and error states
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var lastFullResponse: JSONObject? = null

    init {
        // Initialize all data sources and the repository
        val openMeteoDataSource = OpenMeteoRemoteDataSource(application)
        val noaaDataSource = NoaaRemoteDataSource(application)
        val usgsDataSource = UsgsRemoteDataSource(application)
        repository = WeatherRepository(openMeteoDataSource, noaaDataSource, usgsDataSource)
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
                }
            }

            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    /**
     * Called by the UI to fetch weather and water data for a specific location.
     */
    fun fetchWeatherAndWaterData(latitude: Double, longitude: Double, name: String) {
        _isLoading.value = true
        repository.getWeatherDataAndWaterLevels(latitude, longitude, object : OpenMeteoRemoteDataSource.ApiCallback<Pair<JSONObject, WaterData?>> {
            override fun onSuccess(result: Pair<JSONObject, WaterData?>) {
                val weatherJson = result.first
                val waterDataResult = result.second

                lastFullResponse = weatherJson // Cache the full response
                val forecast = parseFullForecast(weatherJson.getJSONObject("daily"))
                _mainScreenState.value = MainScreenState(name, forecast)
                _waterData.value = waterDataResult // Update the water data LiveData

                // After fetching, automatically select the first day (today)
                onDaySelected(0)
                _isLoading.value = false
            }

            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    /**
     * Called by the UI when the user taps on a forecast tab.
     */
    fun onDaySelected(index: Int) {
        val fullForecast = _mainScreenState.value?.fullForecast ?: return
        if (index >= fullForecast.size) return

        val selectedDay = fullForecast[index]
        val detailedWeather = createDetailedWeatherForDay(selectedDay, index)
        _selectedDayWeather.value = detailedWeather
    }

    /**
     * Called by the UI after the city selection dialog has been shown, to prevent it from re-appearing.
     */
    fun onCitySelectionDialogShown() {
        _cityList.value = emptyList()
    }

    /**
     * Creates the detailed weather object for the selected day.
     * For today (index 0), it uses current hourly data. For future days, some fields will be null.
     */
    private fun createDetailedWeatherForDay(day: DailyForecast, index: Int): DetailedDayWeather {
        val daily = lastFullResponse?.getJSONObject("daily")
        val uv = daily?.getJSONArray("uv_index_max")?.getDouble(index) ?: 0.0
        val sunriseStr = daily?.getJSONArray("sunrise")?.getString(index) ?: ""
        val sunsetStr = daily?.getJSONArray("sunset")?.getString(index) ?: ""
        val daylightSeconds = daily?.getJSONArray("daylight_duration")?.getDouble(index) ?: 0.0

        var pressureVal: String? = null
        var humidityVal: String? = null
        var precipChanceVal: String? = null
        var cloudCoverVal: String? = null
        var windVal: String? = null
        var windGustsVal: String? = null

        // Only today's forecast (index 0) will have current/hourly details
        if (index == 0 && lastFullResponse != null) {
            val current = lastFullResponse!!.getJSONObject("current")
            val hourly = lastFullResponse!!.getJSONObject("hourly")
            val currentHourIndex = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            val press = current.getDouble("pressure_msl")
            val hum = hourly.getJSONArray("relative_humidity_2m").getInt(currentHourIndex)
            val precipChance = hourly.getJSONArray("precipitation_probability").getInt(currentHourIndex)
            val cloud = hourly.getJSONArray("cloud_cover").getInt(currentHourIndex)
            val windSpeed = current.getDouble("wind_speed_10m")
            val windDir = current.getDouble("wind_direction_10m")
            val windGust = current.getDouble("wind_gusts_10m")

            pressureVal = "Pressure: ${press.roundToInt()} hPa"
            humidityVal = "Humidity: $hum%"
            precipChanceVal = "Precipitation Chance: $precipChance%"
            cloudCoverVal = "Cloud Cover: $cloud%"
            windVal = "Wind: ${getWindDirection(windDir)} at ${windSpeed.roundToInt()} km/h"
            windGustsVal = "Gusts: ${windGust.roundToInt()} km/h"
        }

        return DetailedDayWeather(
            date = day.date,
            tempMax = day.tempMax.roundToInt(),
            tempMin = day.tempMin.roundToInt(),
            weatherDescription = getWeatherDescriptionFromCode(day.weatherCode),
            uvIndex = "UV Index: ${uv.roundToInt()} (${getUvIndexRisk(uv)})",
            sunrise = "Sunrise: ${formatTime(sunriseStr)}",
            sunset = "Sunset: ${formatTime(sunsetStr)}",
            daylight = "Daylight: ${String.format("%.1f", daylightSeconds / 3600)} hours",
            pressure = pressureVal,
            humidity = humidityVal,
            precipitationChance = precipChanceVal,
            cloudCover = cloudCoverVal,
            wind = windVal,
            windGusts = windGustsVal
        )
    }

    /**
     * Parses just the 10-day forecast list from the "daily" JSON object.
     */
    private fun parseFullForecast(daily: JSONObject): List<DailyForecast> {
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
        return forecastList
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