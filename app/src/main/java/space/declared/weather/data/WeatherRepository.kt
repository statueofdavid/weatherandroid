package space.declared.weather.data

import org.json.JSONObject
import space.declared.weather.data.source.OpenMeteoRemoteDataSource

/**
 * The single source of truth for weather data.
 * It decides where to get the data from (network, local database, etc.).
 * For now, it only fetches from the network.
 */
class WeatherRepository(private val remoteDataSource: OpenMeteoRemoteDataSource) {

    /**
     * Gets the list of cities from the remote data source.
     */
    fun getCityList(query: String, callback: OpenMeteoRemoteDataSource.ApiCallback<List<CityResult>>) {
        remoteDataSource.fetchCityList(query, callback)
    }

    /**
     * Gets the full weather data from the remote data source.
     */
    fun getWeatherData(latitude: Double, longitude: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<JSONObject>) {
        remoteDataSource.fetchWeatherData(latitude, longitude, callback)
    }
}