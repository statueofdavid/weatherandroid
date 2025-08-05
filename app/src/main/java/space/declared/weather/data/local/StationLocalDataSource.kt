package space.declared.weather.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles all interactions with the local Room database for station data.
 */
class StationLocalDataSource(private val stationDao: StationDao) {

    /**
     * Checks if the database has stations of a specific type.
     */
    suspend fun hasStations(stationType: String): Boolean {
        return withContext(Dispatchers.IO) {
            stationDao.getStationCountByType(stationType) > 0
        }
    }

    /**
     * Inserts a list of stations into the database, replacing any old ones.
     */
    suspend fun saveStations(stations: List<StationEntity>, stationType: String) {
        withContext(Dispatchers.IO) {
            stationDao.clearStationsByType(stationType)
            stationDao.insertAll(stations)
        }
    }

    /**
     * Gets all NOAA stations within a given geographic area from the database.
     */
    suspend fun getNoaaStations(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StationEntity> {
        return withContext(Dispatchers.IO) {
            stationDao.getNoaaStationsInBounds(minLat, maxLat, minLon, maxLon)
        }
    }

    /**
     * Gets all USGS stations within a given geographic area from the database.
     */
    suspend fun getUsgsStations(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StationEntity> {
        return withContext(Dispatchers.IO) {
            stationDao.getUsgsStationsInBounds(minLat, maxLat, minLon, maxLon)
        }
    }
}