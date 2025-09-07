package space.declared.weather.ui

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
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
import space.declared.weather.data.source.NominatimRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.source.UsnoMoonDataSource
import space.declared.weather.service.HazardNotifier
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
    val solsticeCountdown: String,
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
    val isCurrentLocation: Boolean,
    val notificationsEnabled: Boolean,
    val context: String? = null,
    val lastHazardCheck: Long?,
    val capeLevel: String? = null,
    val lightningRisk: String? = null
)

enum class SortType {
    NONE,
    BY_NAME,
    BY_DISTANCE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
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
    val selectedDayIndex: Int = 0,
    val selectedStationDetails: WaterData? = null,
    val stationSortType: SortType = SortType.BY_DISTANCE,
    val stationSortOrder: SortOrder = SortOrder.ASCENDING,
    val savedLocations: List<SavedLocation> = emptyList(),
    val unitSystem: String = "imperial",
    val sunPositionBias: Float = -1f,
    val latitude: Double = 0.0,
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

    private var lastUserLocation: Location? = null
    private var originalStationList: List<StationListItem> = emptyList()

    init {
        val database = AppDatabase.getDatabase(application)
        val localDataSource = StationLocalDataSource(database.stationDao())
        val openMeteoDataSource = OpenMeteoRemoteDataSource(application)

        val noaaDataSource = NoaaRemoteDataSource(application)
        val usgsDataSource = UsgsRemoteDataSource(application)
        val moonDataSource = UsnoMoonDataSource(application)
        val nominatimDataSource = NominatimRemoteDataSource(application)

        repository = WeatherRepository(
            application,
            openMeteoDataSource,
            noaaDataSource,
            usgsDataSource,
            moonDataSource,
            nominatimDataSource,
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
                        entity.isCurrentLocation,
                        entity.notificationsEnabled,
                        entity.context,
                        entity.lastHazardCheck,
                        entity.capeLevel,
                        entity.lightningRisk
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
                val contextDeferred = async { repository.fetchLocationContext(latitude, longitude) }

                val (forecastList, timezone) = weatherDataDeferred.await()
                Log.d("ViewModel_Fetch", "Weather data received. Forecast items: ${forecastList.size}, Timezone: $timezone")

                val stationItems = stationDataDeferred.await()
                val moonData = moonDataDeferred.await()
                val context = contextDeferred.await()

                saveLocation(name, latitude, longitude, timezone, context)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cityName = name,
                        latitude = latitude,
                        fullForecast = forecastList,
                        selectedDayWeather = if (forecastList.isNotEmpty()) createDetailedWeatherForDay(forecastList[0], 0, latitude) else null,
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
            it.copy(
                selectedDayWeather = createDetailedWeatherForDay(selectedDay, index, _uiState.value.latitude),
                selectedDayIndex = index
            )
        }
        if (index == 0) {
            updateSunPosition()
        } else {
            _uiState.update { it.copy(sunPositionBias = -1f) }
        }
    }

    fun onSortStationsByName() {
        _uiState.update { currentState ->
            val newSortOrder = if (currentState.stationSortType == SortType.BY_NAME &&
                currentState.stationSortOrder == SortOrder.ASCENDING) {
                SortOrder.DESCENDING
            } else {
                SortOrder.ASCENDING
            }

            currentState.copy(
                stationSortType = SortType.BY_NAME,
                stationSortOrder = newSortOrder,
                stationListItems = sortStations(originalStationList, SortType.BY_NAME, newSortOrder)
            )
        }
    }

    fun onSortStationsByDistance() {
        _uiState.update { currentState ->
            val newSortOrder = if (currentState.stationSortType == SortType.BY_DISTANCE &&
                currentState.stationSortOrder == SortOrder.ASCENDING) {
                SortOrder.DESCENDING
            } else {
                SortOrder.ASCENDING
            }

            currentState.copy(
                stationSortType = SortType.BY_DISTANCE,
                stationSortOrder = newSortOrder,
                stationListItems = sortStations(originalStationList, SortType.BY_DISTANCE, newSortOrder)
            )
        }
    }

    fun onCitySelectionDialogShown() {
        _uiState.update { it.copy(cityList = emptyList()) }
    }

    fun saveLocation(
        name: String,
        latitude: Double,
        longitude: Double,
        timezone: String?,
        context: String?
    ) {
        viewModelScope.launch {
            repository.saveLocation(
                SavedLocationEntity(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = System.currentTimeMillis(),
                    timezone = timezone,
                    isCurrentLocation = (name == "Current Location"), // Set this based on the name
                    notificationsEnabled = false,  // <-- Default to OFF
                    context = context,             // <-- Pass the context from Nominatim
                    lastHazardCheck = null,        // <-- Default to null
                    capeLevel = null,             // <-- Default to null
                    lightningRisk = null
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
                    location.isCurrentLocation,
                    location.notificationsEnabled,
                    location.context,
                    location.lastHazardCheck,
                    location.capeLevel,
                    location.lightningRisk)
            )
        }
    }

    fun toggleHazardAlertsForLocation(location: SavedLocation, isEnabled: Boolean) {
        viewModelScope.launch {
            val updatedEntity = location.toEntity().copy(notificationsEnabled = isEnabled)
            repository.updateLocation(updatedEntity)

            if (isEnabled) {
                // 1. Always ensure the 30-minute periodic worker is scheduled
                startHazardAlerts()

                // --- YOUR NEW LOGIC GOES HERE ---
                val tenMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)

                // Only run the immediate check if we DON'T have a recent successful check
                if (location.lastHazardCheck == null || location.lastHazardCheck < tenMinutesAgo) {

                    // We don't have recent data, so run the immediate check
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val immediateCheckRequest = OneTimeWorkRequest.Builder(HazardNotifier::class.java)
                        .addTag("immediate_hazard_check")
                        .setConstraints(constraints)
                        .build()
                    WorkManager.getInstance(getApplication()).enqueue(immediateCheckRequest)
                }
                // If we *do* have recent data, we do nothing. The UI is already up to date.

            } else {
                checkIfAllAlertsAreDisabled()
            }
        }
    }

    fun startHazardAlerts() {
        val hazardAlertRequest = PeriodicWorkRequest.Builder(
            HazardNotifier::class.java,
            30, TimeUnit.MINUTES // Default interval
        )
            .addTag("hazard_notifier_work")
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "HazardNotifierWork",
            ExistingPeriodicWorkPolicy.KEEP,
            hazardAlertRequest
        )
    }

    private fun checkIfAllAlertsAreDisabled() {
        viewModelScope.launch {
            // Ask the repository if any locations still have alerts enabled
            val enabledCount = repository.getEnabledAlertsCount()
            if (enabledCount == 0) {
                // No locations need alerts, so we can stop the 30-minute periodic worker.
                stopHazardAlerts()
            }
        }
    }

    fun stopHazardAlerts() {
        WorkManager.getInstance(getApplication()).cancelAllWorkByTag("hazard_notifier_work")
    }

    // --- PRIVATE HELPER FUNCTIONS ---

    private fun SavedLocation.toEntity(): SavedLocationEntity {
        return SavedLocationEntity(
            id = this.id,
            name = this.name,
            latitude = this.latitude,
            longitude = this.longitude,
            timestamp = this.timestamp,
            timezone = this.timezone,
            isCurrentLocation = this.isCurrentLocation,
            notificationsEnabled = this.notificationsEnabled,
            context = this.context,
            lastHazardCheck = this.lastHazardCheck,
            capeLevel = this.capeLevel,
            lightningRisk = this.lightningRisk
        )
    }

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

    private fun createDetailedWeatherForDay(day: DailyForecast, index: Int, latitude: Double): DetailedDayWeather {
        val units = sharedPreferences.getString("units", "imperial") ?: "imperial"
        val windUnit = if (units == "imperial") "mph" else "kmh"
        val tempUnit = if (units == "imperial") "°F" else "°C"

        val daily = lastFullResponse?.getJSONObject("daily")
        val uv = daily?.getJSONArray("uv_index_max")?.optDouble(index, 0.0) ?: 0.0
        val currentLatitude: Double = 0.0

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

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val selectedDate = Calendar.getInstance()
        try {
            sdf.parse(day.date)?.let { selectedDate.time = it }
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Could not parse forecast date: ${day.date}", e)
        }


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
            solsticeCountdown = getSolsticeCountdown(selectedDate,  latitude),
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

    private fun sortStations(list: List<StationListItem>, sortType: SortType, sortOrder: SortOrder): List<StationListItem> {
        return when (sortOrder) {
            SortOrder.ASCENDING -> when (sortType) {
                SortType.BY_NAME -> list.sortedBy { it.entity.name }
                SortType.BY_DISTANCE -> list.sortedBy { it.distanceInMeters }
                SortType.NONE -> list
            }
            SortOrder.DESCENDING -> when (sortType) {
                SortType.BY_NAME -> list.sortedByDescending { it.entity.name }
                SortType.BY_DISTANCE -> list.sortedByDescending { it.distanceInMeters }
                SortType.NONE -> list
            }
        }
    }

    private fun getSolsticeCountdown(forDate: Calendar, latitude: Double): String {
        val now = forDate
        val year = now.get(Calendar.YEAR)

        // Define the approximate solstice dates
        val juneSolstice = Calendar.getInstance().apply { set(year, Calendar.JUNE, 21) }
        val decSolstice = Calendar.getInstance().apply { set(year, Calendar.DECEMBER, 21) }

        val isNorthernHemisphere = latitude >= 0

        val targetSolstice: Calendar
        val solsticeName: String

        when {
            now.before(juneSolstice) -> {
                targetSolstice = juneSolstice
                solsticeName = if (isNorthernHemisphere) "Summer Solstice" else "Winter Solstice"
            }
            now.before(decSolstice) -> {
                targetSolstice = decSolstice
                solsticeName = if (isNorthernHemisphere) "Winter Solstice" else "Summer Solstice"
            }
            else -> {
                // If past December solstice, the next one is in the following year
                targetSolstice = Calendar.getInstance().apply { set(year + 1, Calendar.JUNE, 21) }
                solsticeName = if (isNorthernHemisphere) "Summer Solstice" else "Winter Solstice"
            }
        }

        // Check if today is the solstice
        if (now.get(Calendar.DAY_OF_YEAR) == targetSolstice.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == targetSolstice.get(Calendar.YEAR)) {
            return "$solsticeName is Today!"
        }

        val diffMillis = targetSolstice.timeInMillis - now.timeInMillis
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)

        return "$days days until $solsticeName"
    }
}