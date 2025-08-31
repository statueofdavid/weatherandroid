package space.declared.weather.ui

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import space.declared.weather.data.CityResult
import space.declared.weather.data.DailyForecast
import space.declared.weather.data.MoonData
import space.declared.weather.data.WaterData
import space.declared.weather.data.WeatherRepository
import space.declared.weather.data.local.AppDatabase
import space.declared.weather.data.local.SavedLocationEntity
import space.declared.weather.data.local.StationLocalDataSource
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.source.UsnoMoonDataSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.floor
import kotlin.math.roundToInt

// --- DATA CLASSES FOR UI STATE ---

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
    val windGusts: String?,
    val isCurrentDay: Boolean,
    val rawSunrise: String,
    val rawSunset: String
)

data class SavedLocation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val timezone: String?,
    val isCurrentLocation: Boolean
)

enum class SortType {
    NONE,
    BY_NAME,
    BY_DISTANCE
}

data class WeatherUiState(
    val isLoading: Boolean = false,
    val isFetchingDetails: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val cityName: String? = null,
    val fullForecast: List<DailyForecast> = emptyList(),
    val cityList: List<CityResult> = emptyList(),
    val stationListItems: List<StationListItem> = emptyList(),
    val selectedDayWeather: DetailedDayWeather? = null,
    val selectedStationDetails: WaterData? = null,
    val stationSortType: SortType = SortType.BY_DISTANCE,
    val savedLocations: List<SavedLocation> = emptyList(),
    val unitSystem: String = "imperial",
    val sunPositionBias: Float = -1f,
    val timezone: String? = null,
    val moonData: MoonData? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WeatherRepository
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var sunPositionUpdaterJob: Job? = null
    private var lastFullResponse: JSONObject? = null
    private var hasInitialDataBeenFetched = false

    private var lastUserLocation: Location? = null
    private var originalStationList: List<StationListItem> = emptyList()

    init {
        val database = AppDatabase.getDatabase(application)
        val localDataSource = StationLocalDataSource(database.stationDao())
        val openMeteoDataSource = OpenMeteoRemoteDataSource(application)

        val noaaDataSource = NoaaRemoteDataSource(application)
        val usgsDataSource = UsgsRemoteDataSource(application)
        val moonDataSource = UsnoMoonDataSource(application)

        repository = WeatherRepository(
            application,
            openMeteoDataSource,
            noaaDataSource,
            usgsDataSource,
            moonDataSource,
            localDataSource,
            database.savedLocationDao()
        )

        viewModelScope.launch {
            combine(
                repository.isSyncing,
                repository.savedLocations
            ) { isSyncing, savedLocationEntities ->
                val savedLocations = savedLocationEntities.map { entity ->
                    SavedLocation(
                        entity.id,
                        entity.name,
                        entity.latitude,
                        entity.longitude,
                        entity.timestamp,
                        entity.timezone,
                        entity.isCurrentLocation
                    )
                }
                isSyncing to savedLocations
            }.collect { (isSyncing, savedLocations) ->
                _uiState.update { it.copy(isSyncing = isSyncing, savedLocations = savedLocations) }
            }
        }

        startSunPositionUpdater()
    }

    private fun startSunPositionUpdater() {
        sunPositionUpdaterJob?.cancel()
        sunPositionUpdaterJob = viewModelScope.launch {
            while (true) {
                updateSunPosition()
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    private fun updateSunPosition() {
        val rawSunriseStr = _uiState.value.selectedDayWeather?.rawSunrise ?: return
        val rawSunsetStr = _uiState.value.selectedDayWeather?.rawSunset ?: return
        val timezoneId = _uiState.value.timezone ?: return

        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            isoFormat.timeZone = TimeZone.getTimeZone(timezoneId)

            val sunrise = isoFormat.parse(rawSunriseStr)
            val sunset = isoFormat.parse(rawSunsetStr)

            if (sunrise == null || sunset == null) {
                return
            }

            val locationTime = Calendar.getInstance(TimeZone.getTimeZone(timezoneId))
            val daylightDuration = sunset.time - sunrise.time
            if (daylightDuration <= 0) return

            val timeSinceSunrise = locationTime.timeInMillis - sunrise.time
            val progress = (timeSinceSunrise.toFloat() / daylightDuration.toFloat())

            _uiState.update { it.copy(sunPositionBias = progress) }

        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Error updating sun position", e)
        }
    }

    // --- PUBLIC FUNCTIONS (Called from the UI) ---
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun triggerStationSync() {
        repository.triggerStationSync()
    }

    fun onCitySearch(query: String) {
        _uiState.update { it.copy(isLoading = true) }
        repository.getCityList(query, object : OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>> {
            override fun onSuccess(result: List<CityResult>) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cityList = result,
                        error = if (result.isEmpty()) "City not found" else null
                    )
                }
            }
            override fun onError(error: String) {
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        })
    }

    fun fetchInitialData(latitude: Double, longitude: Double, name: String) {
        if (_uiState.value.isLoading) {
            return // Prevent fetching new data if a fetch is already in progress
        }

        val units = sharedPreferences.getString("units", "imperial") ?: "imperial"
        _uiState.update { it.copy(isLoading = true, error = null, unitSystem = units) }

        lastUserLocation = Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }

        viewModelScope.launch {
            try {
                Log.d("ViewModel_Fetch", "Starting initial data fetch for $name")
                val weatherDataDeferred = async { fetchWeatherSuspend(latitude, longitude) }
                val stationDataDeferred = async { fetchNearbyStationsSuspend(latitude, longitude) }
                val moonDataDeferred = async { repository.fetchMoonData(Date(), latitude, longitude) }

                val (forecastList, timezone) = weatherDataDeferred.await()
                Log.d("ViewModel_Fetch", "Weather data received. Forecast items: ${forecastList.size}, Timezone: $timezone")

                val stationItems = stationDataDeferred.await()
                val moonData = moonDataDeferred.await()

                saveLocation(name, latitude, longitude, timezone)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cityName = name,
                        fullForecast = forecastList,
                        selectedDayWeather = if (forecastList.isNotEmpty()) createDetailedWeatherForDay(forecastList[0], 0) else null,
                        stationListItems = processStationList(stationItems, it.stationSortType),
                        timezone = timezone,
                        moonData = moonData
                    )
                }

                updateSunPosition()
            } catch (e: Exception) {
                Log.e("ViewModel_Fetch", "Error during initial data fetch", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "An unknown error occurred") }
            }
        }
    }

    fun onStationSelected(stationItem: StationListItem) {
        _uiState.update { it.copy(isFetchingDetails = true, selectedStationDetails = null) }

        repository.fetchStationDetails(stationItem.entity, object: OpenMeteoRemoteDataSource.ApiCallback<WaterData?>{
            override fun onSuccess(result: WaterData?) {
                _uiState.update { it.copy(selectedStationDetails = result, isFetchingDetails = false) }
            }
            override fun onError(error: String) {
                _uiState.update { it.copy(error = error, isFetchingDetails = false) }
            }
        })
    }

    fun onDetailCardClosed() {
        _uiState.update { it.copy(selectedStationDetails = null, isFetchingDetails = false) }
    }

    fun refreshDataForCurrentLocation() {
        if (lastUserLocation != null && _uiState.value.cityName != null) {
            fetchInitialData(lastUserLocation!!.latitude, lastUserLocation!!.longitude, _uiState.value.cityName!!)
        }
    }

    fun onDaySelected(index: Int) {
        val fullForecast = _uiState.value.fullForecast
        if (index >= fullForecast.size) return
        val selectedDay = fullForecast[index]
        _uiState.update {
            it.copy(selectedDayWeather = createDetailedWeatherForDay(selectedDay, index))
        }
        if (index == 0) {
            updateSunPosition()
        } else {
            _uiState.update { it.copy(sunPositionBias = -1f) }
        }
    }

    fun onSortStationsByName() {
        _uiState.update { currentState ->
            val newSortType = if (currentState.stationSortType == SortType.BY_NAME) SortType.NONE else SortType.BY_NAME
            currentState.copy(
                stationSortType = newSortType,
                stationListItems = processStationList(originalStationList, newSortType)
            )
        }
    }

    fun onSortStationsByDistance() {
        _uiState.update { currentState ->
            val newSortType = if (currentState.stationSortType == SortType.BY_DISTANCE) SortType.NONE else SortType.BY_DISTANCE
            currentState.copy(
                stationSortType = newSortType,
                stationListItems = processStationList(originalStationList, newSortType)
            )
        }
    }

    fun onCitySelectionDialogShown() {
        _uiState.update { it.copy(cityList = emptyList()) }
    }

    fun saveLocation(name: String, latitude: Double, longitude: Double, timezone: String?) {
        viewModelScope.launch {
            repository.saveLocation(
                SavedLocationEntity(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = System.currentTimeMillis(),
                    timezone = timezone
                )
            )
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteLocation(
                SavedLocationEntity(
                    location.id,
                    location.name,
                    location.latitude,
                    location.longitude,
                    location.timestamp,
                    location.timezone,
                    location.isCurrentLocation)
            )
        }
    }

    // --- PRIVATE HELPER FUNCTIONS ---

    private suspend fun fetchWeatherSuspend(latitude: Double, longitude: Double): Pair<List<DailyForecast>, String?> {
        return suspendCancellableCoroutine { continuation ->
            val units = sharedPreferences.getString("units", "metric") ?: "metric"
            repository.fetchWeatherData(latitude, longitude, units, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
                override fun onSuccess(result: JSONObject) {
                    lastFullResponse = result
                    val forecast = parseFullForecast(result.getJSONObject("daily"))
                    val timezone = result.optString("timezone", null)
                    if (continuation.isActive) continuation.resume(Pair(forecast, timezone))
                }
                override fun onError(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(Exception(error))
                }
            })
        }
    }

    private suspend fun fetchNearbyStationsSuspend(latitude: Double, longitude: Double): List<StationListItem> {
        val radiusInMiles = sharedPreferences.getString("radius", "1.0")?.toDoubleOrNull() ?: 1.0
        val radiusInDegrees = radiusInMiles / 69.0

        val stations = repository.getNearbyStations(latitude, longitude, radiusInDegrees)
        val stationItems = stations.map { entity ->
            val stationLocation = Location("").apply {
                this.latitude = entity.latitude
                this.longitude = entity.longitude
            }
            val distanceInMeters = lastUserLocation?.distanceTo(stationLocation) ?: 0f
            StationListItem(entity, distanceInMeters)
        }
        originalStationList = stationItems
        return stationItems
    }

    private fun processStationList(list: List<StationListItem>, sortType: SortType): List<StationListItem> {
        return when (sortType) {
            SortType.BY_NAME -> list.sortedBy { it.entity.name }
            SortType.BY_DISTANCE -> list.sortedBy { it.distanceInMeters }
            SortType.NONE -> list
        }
    }

    private fun createDetailedWeatherForDay(day: DailyForecast, index: Int): DetailedDayWeather {
        val units = sharedPreferences.getString("units", "imperial") ?: "imperial"
        val windUnit = if (units == "imperial") "mph" else "kmh"
        val tempUnit = if (units == "imperial") "°F" else "°C"

        val daily = lastFullResponse?.getJSONObject("daily")
        val uv = daily?.getJSONArray("uv_index_max")?.optDouble(index, 0.0) ?: 0.0

        val sunriseTimestamp = daily?.getJSONArray("sunrise")?.optLong(index) ?: 0
        val sunsetTimestamp = daily?.getJSONArray("sunset")?.optLong(index) ?: 0
        val daylightSeconds = daily?.getJSONArray("daylight_duration")?.optDouble(index, 0.0) ?: 0.0

        val rawSunriseStr = formatTimestampToISO(sunriseTimestamp)
        val rawSunsetStr = formatTimestampToISO(sunsetTimestamp)

        var pressureVal: String? = null
        var humidityVal: String? = null
        var precipChanceVal: String? = null
        var cloudCoverVal: String? = null
        var windVal: String? = null
        var windGustsVal: String? = null
        val isCurrent = index == 0

        if (isCurrent && lastFullResponse != null) {
            val current = lastFullResponse!!.getJSONObject("current")
            val hourly = lastFullResponse!!.getJSONObject("hourly")
            val hourlyTimeArray = hourly.getJSONArray("time")
            val currentTime = System.currentTimeMillis() / 1000
            var currentHourIndex = 0
            for (i in 0 until hourlyTimeArray.length()) {
                if (hourlyTimeArray.getLong(i) >= currentTime) {
                    currentHourIndex = i
                    break
                }
            }

            pressureVal = "Pressure: ${current.getDouble("pressure_msl").roundToInt()} hPa"
            humidityVal = "Humidity: ${hourly.getJSONArray("relative_humidity_2m").getInt(currentHourIndex)}%"
            precipChanceVal = "Precipitation Chance: ${hourly.getJSONArray("precipitation_probability").getInt(currentHourIndex)}%"
            cloudCoverVal = "Cloud Cover: ${hourly.getJSONArray("cloud_cover").getInt(currentHourIndex)}%"
            windVal = "Wind: ${getWindDirection(current.getDouble("wind_direction_10m"))} at ${current.getDouble("wind_speed_10m").roundToInt()} $windUnit"
            windGustsVal = "Gusts: ${current.getDouble("wind_gusts_10m").roundToInt()} $windUnit"
        } else if (daily != null) {
            val precipChanceMax = daily.getJSONArray("precipitation_probability_max").optInt(index, 0)
            val windSpeedMax = daily.getJSONArray("wind_speed_10m_max").optDouble(index, 0.0)
            val windGustsMax = daily.getJSONArray("wind_gusts_10m_max").optDouble(index, 0.0)

            precipChanceVal = "Max Precip Chance: $precipChanceMax%"
            windVal = "Max Wind: ${windSpeedMax.roundToInt()} $windUnit"
            windGustsVal = "Max Gusts: ${windGustsMax.roundToInt()} $windUnit"
        }

        return DetailedDayWeather(
            date = day.date,
            tempMax = "${day.tempMax.roundToInt()}$tempUnit",
            tempMin = "${day.tempMin.roundToInt()}$tempUnit",
            weatherDescription = getWeatherDescriptionFromCode(day.weatherCode),
            uvIndex = "UV Index: ${uv.roundToInt()} (${getUvIndexRisk(uv)})",
            sunrise = "Sunrise: ${formatTimestampToTime(sunriseTimestamp)}",
            sunset = "Sunset: ${formatTimestampToTime(sunsetTimestamp)}",
            daylight = "Daylight: ${String.format("%.1f", daylightSeconds / 3600)} hours",
            pressure = pressureVal,
            humidity = humidityVal,
            precipitationChance = precipChanceVal,
            cloudCover = cloudCoverVal,
            wind = windVal,
            windGusts = windGustsVal,
            isCurrentDay = isCurrent,
            rawSunrise = rawSunriseStr,
            rawSunset = rawSunsetStr
        )
    }

    private fun parseFullForecast(daily: JSONObject): List<DailyForecast> {
        Log.d("ViewModel_Parse", "Starting to parse full forecast...")
        val forecastList = mutableListOf<DailyForecast>()
        val timeArray = daily.getJSONArray("time")
        val weatherCodeArray = daily.getJSONArray("weather_code")
        val tempMaxArray = daily.getJSONArray("temperature_2m_max")
        val tempMinArray = daily.getJSONArray("temperature_2m_min")

        for (i in 0 until timeArray.length()) {
            val dateString = formatTimestampToDateString(timeArray.getLong(i))
            val forecast = DailyForecast(
                date = dateString,
                weatherCode = weatherCodeArray.getInt(i),
                tempMax = tempMaxArray.getDouble(i),
                tempMin = tempMinArray.getDouble(i)
            )
            forecastList.add(forecast)
        }
        Log.d("ViewModel_Parse", "Finished parsing. Total days: ${forecastList.size}")
        return forecastList
    }

    private fun formatTimestampToDateString(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = Date(timestamp * 1000)
            sdf.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun formatTimestampToTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = Date(timestamp * 1000)
            sdf.format(date)
        } catch (e: Exception) { "N/A" }
    }

    private fun formatTimestampToISO(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val date = Date(timestamp * 1000)
            sdf.format(date)
        } catch (e: Exception) { "" }
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