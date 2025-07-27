package space.declared.weather.data

import android.util.Log
import org.json.JSONObject
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.TideData
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.WaterLevelData

/**
 * The single source of truth for all app data.
 * It orchestrates fetching data from multiple sources.
 */
class WeatherRepository(
    private val openMeteoDataSource: OpenMeteoRemoteDataSource,
    private val noaaDataSource: NoaaRemoteDataSource,
    private val usgsDataSource: UsgsRemoteDataSource
) {

    fun getCityList(query: String, callback: OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>>) {
        openMeteoDataSource.fetchCityList(query, callback)
    }

    /**
     * Gets all data, now accepting the unit system and search radius.
     */
    fun getWeatherDataAndWaterLevels(
        latitude: Double,
        longitude: Double,
        units: String, // Added units parameter
        radius: Double,
        callback: OpenMeteoRemoteDataSource.ApiCallback<Pair<JSONObject, WaterData?>>
    ) {
        // At the beginning of the function
        Log.d("WeatherRepository", "Searching for stations with lat=${latitude}, lon=${longitude}, radius=${radius} miles")

        // Pass the units parameter down to the data source
        openMeteoDataSource.fetchWeatherData(latitude, longitude, units, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
            override fun onSuccess(weatherResult: JSONObject) {
                var tideResult: List<TideData>? = null
                var waterLevelResult: List<WaterLevelData>? = null
                var completedCalls = 0

                fun checkCompletion() {
                    completedCalls++
                    if (completedCalls == 2) {
                        val waterData = if (!tideResult.isNullOrEmpty() || !waterLevelResult.isNullOrEmpty()) {
                            WaterData(tideData = tideResult, waterLevel = waterLevelResult)
                        } else {
                            null
                        }
                        callback.onSuccess(Pair(weatherResult, waterData))
                    }
                }

                noaaDataSource.fetchTidePredictions(latitude, longitude, radius, object : OpenMeteoRemoteDataSource.ApiCallback<List<TideData>> {
                    override fun onSuccess(result: List<TideData>) {
                        tideResult = result
                        checkCompletion()
                    }
                    override fun onError(error: String) { checkCompletion() }
                })

                usgsDataSource.fetchWaterLevels(latitude, longitude, radius, object : OpenMeteoRemoteDataSource.ApiCallback<List<WaterLevelData>> {
                    override fun onSuccess(result: List<WaterLevelData>) {
                        waterLevelResult = result
                        checkCompletion()
                    }
                    override fun onError(error: String) { checkCompletion() }
                })
            }

            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }
}