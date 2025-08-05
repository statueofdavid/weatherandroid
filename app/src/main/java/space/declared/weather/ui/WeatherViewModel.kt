package space.declared.weather.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import space.declared.weather.data.WaterData
import space.declared.weather.data.WeatherRepository
import space.declared.weather.data.local.AppDatabase
import space.declared.weather.data.local.StationEntity
import space.declared.weather.data.local.StationLocalDataSource
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
    val tempMax: String,
    val tempMin: String,
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

    // --- LiveData for UI State ---
    private val _cityList = MutableLiveData<List<CityResult>>()
    val cityList: LiveData<List<CityResult>> = _cityList

    private val _mainScreenState = MutableLiveData<MainScreenState>()
    val mainScreenState: LiveData<MainScreenState> = _mainScreenState

    private val _selectedDayWeather = MutableLiveData<DetailedDayWeather>()
    val selectedDayWeather: LiveData<DetailedDayWeather> = _selectedDayWeather

    private val _stationListItems = MutableLiveData<List<StationListItem>>()
    val stationListItems: LiveData<List<StationListItem>> = _stationListItems

    private val _selectedStationDetails = MutableLiveData<WaterData?>()
    val selectedStationDetails: LiveData<WaterData?> = _selectedStationDetails

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // --- Private State ---
    private var lastFullResponse: JSONObject? = null
    private var lastUserLocation: Location? = null
    private var lastCityName: String? = null

    init {
        // Correctly initialize the database and all data sources
        val database = AppDatabase.getDatabase(application)
        val localDataSource = StationLocalDataSource(database.stationDao())
        val openMeteoDataSource = OpenMeteoRemoteDataSource(application)
        val noaaDataSource = NoaaRemoteDataSource(application)
        val usgsDataSource = UsgsRemoteDataSource(application)
        repository = WeatherRepository(openMeteoDataSource, noaaDataSource, usgsDataSource, localDataSource)
    }

    // --- Public Functions (called by the UI) ---

    fun onCitySearch(query: String) {
        _isLoading.value = true
        repository.getCityList(query, object : OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>> {
            override fun onSuccess(result: List<CityResult>) {
                _isLoading.value = false
                if (result.isEmpty()) {
                    _error.value = "City not found"
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

    fun fetchInitialData(latitude: Double, longitude: Double, name: String) {
        _isLoading.value = true
        lastUserLocation = Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        lastCityName = name

        // Now fetches weather and the list of nearby stations separately
        fetchWeather(latitude, longitude, name)
        fetchNearbyStations(latitude, longitude)
    }

    fun onStationSelected(stationItem: StationListItem) {
        _isLoading.value = true
        repository.fetchStationDetails(stationItem.entity, object: OpenMeteoRemoteDataSource.ApiCallback<WaterData?>{
            override fun onSuccess(result: WaterData?) {
                _selectedStationDetails.value = result
                _isLoading.value = false
            }
            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    fun refreshDataForCurrentLocation() {
        if (lastUserLocation != null && lastCityName != null) {
            fetchInitialData(lastUserLocation!!.latitude, lastUserLocation!!.longitude, lastCityName!!)
        }
    }

    fun onDaySelected(index: Int) {
        val fullForecast = _mainScreenState.value?.fullForecast ?: return
        if (index >= fullForecast.size) return
        val selectedDay = fullForecast[index]
        _selectedDayWeather.value = createDetailedWeatherForDay(selectedDay, index)
    }

    fun onCitySelectionDialogShown() {
        _cityList.value = emptyList()
    }

    // --- Private Helper Functions ---

    private fun fetchWeather(latitude: Double, longitude: Double, name: String) {
        val units = sharedPreferences.getString("units", "metric") ?: "metric"
        repository.fetchWeatherData(latitude, longitude, units, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                lastFullResponse = result
                val forecast = parseFullForecast(result.getJSONObject("daily"))
                _mainScreenState.value = MainScreenState(name, forecast)
                if (forecast.isNotEmpty()) {
                    _selectedDayWeather.value = createDetailedWeatherForDay(forecast[0], 0)
                }
                checkIfAllLoadingFinished()
            }
            override fun onError(error: String) {
                _error.value = error
                _isLoading.value = false
            }
        })
    }

    private fun fetchNearbyStations(latitude: Double, longitude: Double) {
        val radius = sharedPreferences.getString("radius", "1.0")?.toDoubleOrNull() ?: 1.0
        viewModelScope.launch {
            val stations = repository.getNearbyStations(latitude, longitude, radius)
            val stationItems = stations.map { entity ->
                val stationLocation = Location("").apply {
                    this.latitude = entity.latitude
                    this.longitude = entity.longitude
                }
                val distance = lastUserLocation?.distanceTo(stationLocation)?.times(0.000621371f) ?: 0f // meters to miles
                StationListItem(entity, distance)
            }.sortedBy { it.distance }
            _stationListItems.postValue(stationItems)
            checkIfAllLoadingFinished()
        }
    }

    private var loadingParts = 2
    private fun checkIfAllLoadingFinished() {
        loadingParts--
        if (loadingParts <= 0) {
            _isLoading.value = false
            loadingParts = 2 // Reset for next time
        }
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