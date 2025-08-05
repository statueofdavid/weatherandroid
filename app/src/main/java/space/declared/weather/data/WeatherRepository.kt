package space.declared.weather.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import space.declared.weather.data.local.StationLocalDataSource
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource

/**
 * The single source of truth for all app data.
 * It now uses a local database to cache station data.
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

    fun getWeatherDataAndWaterLevels(
        latitude: Double,
        longitude: Double,
        units: String,
        radius: Double,
        callback: OpenMeteoRemoteDataSource.ApiCallback<Pair<JSONObject, WaterData?>>
    ) {
        openMeteoDataSource.fetchWeatherData(latitude, longitude, units, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
            override fun onSuccess(weatherResult: JSONObject) {
                // Now, get the nearby stations from our local database
                repositoryScope.launch {
                    val minLat = latitude - radius
                    val maxLat = latitude + radius
                    val minLon = longitude - radius
                    val maxLon = longitude + radius

                    val nearbyNoaaStations = localDataSource.getNoaaStations(minLat, maxLat, minLon, maxLon)
                    val nearbyUsgsStations = localDataSource.getUsgsStations(minLat, maxLat, minLon, maxLon)

                    // TODO: We will update the ViewModel and Fragment to use this new list of stations.
                    // For now, this completes the data layer refactor.
                }
            }

            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }
}