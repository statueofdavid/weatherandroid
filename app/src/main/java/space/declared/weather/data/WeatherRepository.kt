package space.declared.weather.data

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import space.declared.weather.data.local.StationEntity
import space.declared.weather.data.local.StationLocalDataSource
import space.declared.weather.data.local.SavedLocationDao
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.local.SavedLocationEntity
import space.declared.weather.data.source.NominatimRemoteDataSource
import space.declared.weather.data.source.UsnoMoonDataSource
import java.util.Date
import java.util.concurrent.TimeUnit

class WeatherRepository(
    context: Context,
    private val openMeteoDataSource: OpenMeteoRemoteDataSource,
    private val noaaDataSource: NoaaRemoteDataSource,
    private val usgsDataSource: UsgsRemoteDataSource,
    private val moonDataSource: UsnoMoonDataSource,
    private val nominatimDataSource: NominatimRemoteDataSource,
    private val localDataSource: StationLocalDataSource,
    private val savedLocationDao: SavedLocationDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val _isSyncing = MutableStateFlow(false)

    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    val savedLocations: Flow<List<SavedLocationEntity>> = savedLocationDao.getAllLocations()

    init {
        syncStationsIfNeeded()
    }

    suspend fun getEnabledAlertsCount(): Int {
        return savedLocationDao.getEnabledAlertsCount()
    }

    suspend fun fetchLocationContext(latitude: Double, longitude: Double): String? {
        return nominatimDataSource.getQuirkyLocationContext(latitude, longitude)
    }

    suspend fun fetchMoonData(date: Date, latitude: Double, longitude: Double): MoonData? {
        return moonDataSource.fetchMoonPhase(date, latitude, longitude)
    }

    suspend fun updateLocation(location: SavedLocationEntity) {
        savedLocationDao.update(location)
    }

    suspend fun saveLocation(location: SavedLocationEntity) {
        // Check if the location already exists
        val existingLocation = savedLocationDao.getLocationByName(location.name)

        if (existingLocation == null) {
            // If the location doesn't exist, save it to the database
            Log.d("WeatherRepository", "Saving new location: ${location.name}")
            savedLocationDao.insert(location)
        } else {
            // If the location already exists, do not save it again
            Log.d("WeatherRepository", "Location '${location.name}' already exists. Skipping save.")
        }
    }

    suspend fun deleteLocation(location: SavedLocationEntity) {
        savedLocationDao.delete(location)
    }

    /**
     * NEW: Public function to allow the UI to manually trigger a sync.
     */
    fun triggerStationSync() {
        // To prevent multiple rapid syncs, we only start a new one if not already syncing.
        if (!_isSyncing.value) {
            syncStationsIfNeeded(forceSync = true)
        }
    }

    private fun syncStationsIfNeeded(forceSync: Boolean = false) {
        val lastSyncTime = sharedPreferences.getLong("last_station_sync", 0)
        val oneDayInMillis = TimeUnit.DAYS.toMillis(1)

        if (forceSync || System.currentTimeMillis() - lastSyncTime > oneDayInMillis) {
            repositoryScope.launch {
                _isSyncing.value = true
                try {
                    Log.d("WeatherRepository", "Syncing station data from network...")
                    val noaaStations = async { noaaDataSource.fetchAllStations() }
                    val usgsStations = async { usgsDataSource.fetchAllStations() }

                    val allStations = noaaStations.await() + usgsStations.await()
                    if (allStations.isNotEmpty()) {
                        localDataSource.clearAndSaveStations(allStations)
                        sharedPreferences.edit().putLong("last_station_sync", System.currentTimeMillis()).apply()
                        Log.d("WeatherRepository", "Sync complete. Saved ${allStations.size} stations.")
                    }
                } finally {
                    _isSyncing.value = false
                }
            }
        } else {
            Log.d("WeatherRepository", "Skipping station sync, data is fresh.")
        }
    }

    fun getCityList(query: String, callback: OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>>) {
        openMeteoDataSource.fetchCityList(query, callback)
    }

    fun fetchWeatherData(latitude: Double, longitude: Double, units: String, callback: OpenMeteoRemoteDataSource.ApiCallback<JSONObject>) {
        openMeteoDataSource.fetchWeatherData(latitude, longitude, units, callback)
    }

    suspend fun getNearbyStations(latitude: Double, longitude: Double, radius: Double): List<StationEntity> {
        val minLat = latitude - radius
        val maxLat = latitude + radius
        val minLon = longitude - radius
        val maxLon = longitude + radius

        val noaaStationsDeferred = repositoryScope.async { localDataSource.getNoaaStations(minLat, maxLat, minLon, maxLon) }
        val usgsStationsDeferred = repositoryScope.async { localDataSource.getUsgsStations(minLat, maxLat, minLon, maxLon) }

        return noaaStationsDeferred.await() + usgsStationsDeferred.await()
    }

    fun fetchStationDetails(station: StationEntity, callback: OpenMeteoRemoteDataSource.ApiCallback<WaterData?>) {
        when (station.type) {
            "NOAA" -> {
                // FIX: Pass the station's name, latitude, and longitude to the data source function.
                noaaDataSource.fetchTideDataForStation(
                    station.id,
                    station.name,
                    station.latitude,
                    station.longitude,
                    object : OpenMeteoRemoteDataSource.ApiCallback<TideData?> {
                        override fun onSuccess(result: TideData?) {
                            val waterData = WaterData(tideData = result?.let { listOf(it) })
                            callback.onSuccess(waterData)
                        }
                        override fun onError(error: String) {
                            callback.onError(error)
                        }
                    }
                )
            }
            "USGS" -> {
                usgsDataSource.fetchWaterLevelForStation(station.id, object : OpenMeteoRemoteDataSource.ApiCallback<WaterLevelData?> {
                    override fun onSuccess(result: WaterLevelData?) {
                        callback.onSuccess(WaterData(waterLevel = if (result != null) listOf(result) else null))
                    }
                    override fun onError(error: String) {
                        callback.onError(error)
                    }
                })
            }
            else -> callback.onSuccess(null)
        }
    }
}