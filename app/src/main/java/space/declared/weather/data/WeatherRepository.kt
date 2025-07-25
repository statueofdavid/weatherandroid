package space.declared.weather.data

import org.json.JSONObject
import space.declared.weather.data.source.NoaaRemoteDataSource
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.TidePrediction
import space.declared.weather.data.source.UsgsRemoteDataSource
import space.declared.weather.data.source.WaterLevelData

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
     * Gets the full weather data and the relevant water data (tides or river levels).
     */
    fun getWeatherDataAndWaterLevels(
        latitude: Double,
        longitude: Double,
        callback: OpenMeteoRemoteDataSource.ApiCallback<Pair<JSONObject, WaterData?>>
    ) {
        // First, fetch the main weather data
        openMeteoDataSource.fetchWeatherData(latitude, longitude, object : OpenMeteoRemoteDataSource.ApiCallback<JSONObject> {
            override fun onSuccess(weatherResult: JSONObject) {
                // After getting weather, try to get tide data
                noaaDataSource.fetchTidePredictions(latitude, longitude, object : OpenMeteoRemoteDataSource.ApiCallback<List<TidePrediction>> {
                    override fun onSuccess(tideResult: List<TidePrediction>) {
                        if (tideResult.isNotEmpty()) {
                            // We found tide data, so we're done.
                            val waterData = WaterData(isTideData = true, tidePredictions = tideResult)
                            callback.onSuccess(Pair(weatherResult, waterData))
                        } else {
                            // No tide data, so now we try to get river data
                            usgsDataSource.fetchWaterLevel(latitude, longitude, object : OpenMeteoRemoteDataSource.ApiCallback<WaterLevelData?> {
                                override fun onSuccess(waterLevelResult: WaterLevelData?) {
                                    val waterData = waterLevelResult?.let {
                                        WaterData(isTideData = false, waterLevel = it)
                                    }
                                    callback.onSuccess(Pair(weatherResult, waterData))
                                }

                                override fun onError(error: String) {
                                    // Failed to get river data, just return weather
                                    callback.onSuccess(Pair(weatherResult, null))
                                }
                            })
                        }
                    }

                    override fun onError(error: String) {
                        // Failed to get tide data, just return weather
                        callback.onSuccess(Pair(weatherResult, null))
                    }
                })
            }

            override fun onError(error: String) {
                // If we can't even get the weather, fail completely
                callback.onError(error)
            }
        })
    }
}