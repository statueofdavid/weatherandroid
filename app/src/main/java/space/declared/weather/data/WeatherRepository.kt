package space.declared.weather.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import space.declared.weather.data.local.StationEntity
import space.declared.weather.data.local.StationLocalDataSource
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.TideData
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.WaterLevelData

/**
 * The single source of truth for all app data.
 * It now uses a local database to cache station data and fetches details on demand.
 */
class WeatherRepository(
    private val openMeteoDataSource: OpenMeteoRemoteDataSource,
    private val noaaDataSource: NoaaRemoteDataSource,
    private val usgsDataSource: UsgsRemoteDataSource,
    private val localDataSource: StationLocalDataSource
) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // On first launch, check if we need to fetch and cache the station lists.
        cacheStationsIfNeeded()
    }

    private fun cacheStationsIfNeeded() {
        repositoryScope.launch {
            if (!localDataSource.hasStations("NOAA")) {
                noaaDataSource.fetchAllStations { stations ->
                    repositoryScope.launch {
                        localDataSource.saveStations(stations, "NOAA")
                    }
                }
            }
            if (!localDataSource.hasStations("USGS")) {
                usgsDataSource.fetchAllStations { stations ->
                    repositoryScope.launch {
                        localDataSource.saveStations(stations, "USGS")
                    }
                }
            }
        }
    }

    fun getCityList(query: String, callback: OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>>) {
        openMeteoDataSource.fetchCityList(query, callback)
    }

    /**
     * Fetches only the weather data from the network.
     */
    fun fetchWeatherData(latitude: Double, longitude: Double, units: String, callback: OpenMeteoRemoteDataSource.ApiCallback<JSONObject>) {
        openMeteoDataSource.fetchWeatherData(latitude, longitude, units, callback)
    }

    /**
     * Gets the list of all nearby stations from the local database.
     */
    suspend fun getNearbyStations(latitude: Double, longitude: Double, radius: Double): List<StationEntity> {
        val minLat = latitude - radius
        val maxLat = latitude + radius
        val minLon = longitude - radius
        val maxLon = longitude + radius

        // Use async to fetch both lists in parallel from the database for efficiency
        val noaaStationsDeferred = repositoryScope.async { localDataSource.getNoaaStations(minLat, maxLat, minLon, maxLon) }
        val usgsStationsDeferred = repositoryScope.async { localDataSource.getUsgsStations(minLat, maxLat, minLon, maxLon) }

        // Wait for both queries to finish and combine their results
        return noaaStationsDeferred.await() + usgsStationsDeferred.await()
    }

    /**
     * Fetches the live, detailed data for a single selected station.
     */
    fun fetchStationDetails(station: StationEntity, callback: OpenMeteoRemoteDataSource.ApiCallback<WaterData?>) {
        when (station.type) {
            "NOAA" -> {
                // For a NOAA station, fetch its tide predictions
                noaaDataSource.fetchTidePredictionsForStation(station.id, station.name, station.latitude, station.longitude, object : OpenMeteoRemoteDataSource.ApiCallback<TideData?> {
                    override fun onSuccess(result: TideData?) {
                        callback.onSuccess(WaterData(tideData = if (result != null) listOf(result) else null))
                    }
                    override fun onError(error: String) {
                        callback.onError(error)
                    }
                })
            }
            "USGS" -> {
                // For a USGS station, fetch its water level
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