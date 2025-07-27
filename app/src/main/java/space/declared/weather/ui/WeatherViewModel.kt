package space.declared.weather.ui

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
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

data class DetailedDayWeather(
    val date: String,
    val tempMax: String, // Changed to String to include unit
    val tempMin: String, // Changed to String to include unit
    val weatherDescription: String,
    val uvIndex: String,
    val sunrise: String,
    val sunset: String,
    val daylight: String,
    val pressure: String?,
    val humidity: String?,
    val precipitationChance: String?,
    val cloudCover: String?,
    val wind: String?,
    val windGusts: String?
)

data class MainScreenState(
    val cityName: String,
    val fullForecast: List<DailyForecast>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _cityList = MutableLiveData<List<CityResult>>()
    val cityList: LiveData<List<CityResult>> = _cityList

    private val _mainScreenState = MutableLiveData<MainScreenState>()
    val mainScreenState: LiveData<MainScreenState> = _mainScreenState

    private val _selectedDayWeather = MutableLiveData<DetailedDayWeather>()
    val selectedDayWeather: LiveData<DetailedDayWeather> = _selectedDayWeather

    private val _waterData = MutableLiveData<WaterData?>()
    val waterData: LiveData<WaterData?> = _waterData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _showCitySearchCard = MutableLiveData<Boolean>()
    val showCitySearchCard: LiveData<Boolean> = _showCitySearchCard

    private var lastFullResponse: JSONObject? = null
    private var lastUserLocation: Location? = null
    private var lastCityName: String? = null

    init {
        val openMeteoDataSource = OpenMeteoRemoteDataSource(application)
        val noaaDataSource = NoaaRemoteDataSource(application)
        val usgsDataSource = UsgsRemoteDataSource(application)

        _showCitySearchCard.value = false
        repository = WeatherRepository(openMeteoDataSource, noaaDataSource, usgsDataSource)
    }

    fun onCitySearch(query: String) {
        if (query.length > 2) {
            _isLoading.value = true
            _showCitySearchCard.value = true // Show card when search starts
            repository.getCityList(query, object : OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>> {
                override fun onSuccess(result: List<CityResult>) {
                    _cityList.value = result // Always update cityList
                    _isLoading.value = false

                    // If results are empty, hide the card
                    if (result.isEmpty()) {
                        _error.value = "City not found"
                        _showCitySearchCard.value = false
                    }
                }
                override fun onError(error: String) {
                    _error.value = error
                    _isLoading.value = false
                    _showCitySearchCard.value = false // Hide on error
                    _cityList.value = emptyList() // Clear list on error
                }
            })
        } else {
            // Query is too short (or empty)
            clearSearchAndHideCard()
        }
    }

    fun fetchWeatherAndWaterData(latitude: Double, longitude: Double, name: String) {
        _isLoading.value = true
        lastUserLocation = Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        lastCityName = name

        val units = sharedPreferences.getString("units", "metric") ?: "metric"
        val radius = sharedPreferences.getString("radius", "1.0")?.toDoubleOrNull() ?: 1.0

        repository.getWeatherDataAndWaterLevels(latitude, longitude, units, radius, object : OpenMeteoRemoteDataSource.ApiCallback<Pair<JSONObject, WaterData?>> {
            override fun onSuccess(result: Pair<JSONObject, WaterData?>) {
                Log.i("MainViewModel", "SUCCESS: Weather data received for $name.")
                val weatherJson = result.first
                val waterDataResult = result.second

                // Log.d("MainViewModel", "RAW_JSON_RESPONSE: ${weatherJson.toString(2)}")
                Log.d("MainViewModel", "DATA_CHECK - Tide Stations: ${waterDataResult?.tideData?.size ?: 0}, River/Lake Stations: ${waterDataResult?.waterLevel?.size ?: 0}")

                lastFullResponse = weatherJson
                val forecast = parseFullForecast(weatherJson.getJSONObject("daily"))
                Log.d("MainViewModel", "the forecast is parsed: $forecast")
                _mainScreenState.value = MainScreenState(name, forecast)

                val sortedWaterData = sortWaterDataByDistance(waterDataResult)
                _waterData.value = sortedWaterData

                if (forecast.isNotEmpty()) {
                    val todayWeather = createDetailedWeatherForDay(forecast[0], 0)
                    _selectedDayWeather.value = todayWeather
                }

                Log.i("MainViewModel", "COMPLETED: Setting isLoading=false for $name.")
                _isLoading.value = false
            }
            override fun onError(error: String) {
                Log.e("MainViewModel", "ERROR: $error. Setting isLoading=false.")
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    /**
     * Re-fetches the data for the last known location. Called when settings change.
     */
    fun refreshDataForCurrentLocation() {
        if (lastUserLocation != null && lastCityName != null) {
            fetchWeatherAndWaterData(lastUserLocation!!.latitude, lastUserLocation!!.longitude, lastCityName!!)
        }
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
        // You could also add logging or other logic here if needed,
        // which is the benefit of letting the ViewModel control its state.
        Log.d("WeatherViewModel::setLoading", "setLoading: ViewModel's isLoading set to $loading")
    }

    fun onDaySelected(index: Int) {
        Log.i("MainViewModel", "CALL: onDaySelected(index: $index).")
        val fullForecast = _mainScreenState.value?.fullForecast ?: return
        if (index >= fullForecast.size) return

        val selectedDay = fullForecast[index]
        val detailedWeather = createDetailedWeatherForDay(selectedDay, index)
        _selectedDayWeather.value = detailedWeather
    }

    fun onCitySelectionDialogShown() {
        _cityList.value = emptyList()
        _showCitySearchCard.value = false
    }

    fun hideCitySearchCard() {
        _showCitySearchCard.value = false
    }

    fun clearSearchAndHideCard() {
        _cityList.value = emptyList()
        _showCitySearchCard.value = false
    }

    fun onCitySelectionDone() {
        _cityList.value = emptyList() // Clear the list
        _showCitySearchCard.value = false // Hide the card
    }

    fun onSortByDistanceClicked() {
        _waterData.value = sortWaterDataByDistance(_waterData.value)
    }

    fun onSortByNameClicked() {
        val currentData = _waterData.value
        val sortedTides = currentData?.tideData?.sortedBy { it.stationName }
        val sortedRivers = currentData?.waterLevel?.sortedBy { it.siteName }
        _waterData.value = WaterData(sortedTides, sortedRivers)
    }

    private fun sortWaterDataByDistance(waterData: WaterData?): WaterData? {
        val userLocation = lastUserLocation ?: return waterData

        val sortedTides = waterData?.tideData?.sortedBy {
            val stationLocation = Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            userLocation.distanceTo(stationLocation)
        }

        val sortedRivers = waterData?.waterLevel?.sortedBy {
            val stationLocation = Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            userLocation.distanceTo(stationLocation)
        }

        return WaterData(sortedTides, sortedRivers)
    }

    private fun createDetailedWeatherForDay(day: DailyForecast, index: Int): DetailedDayWeather {
        val units = sharedPreferences.getString("units", "metric") ?: "metric"
        val windUnit = if (units == "imperial") "mph" else "km/h"
        val tempUnit = if (units == "imperial") "°F" else "°C"

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
            windVal = "Wind: ${getWindDirection(windDir)} at ${windSpeed.roundToInt()} $windUnit"
            windGustsVal = "Gusts: ${windGust.roundToInt()} $windUnit"
        }

        return DetailedDayWeather(
            date = day.date,
            tempMax = "${day.tempMax.roundToInt()}$tempUnit",
            tempMin = "${day.tempMin.roundToInt()}$tempUnit",
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