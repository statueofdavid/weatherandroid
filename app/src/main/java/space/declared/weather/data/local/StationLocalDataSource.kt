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
     * NEW FUNCTION: Clears all existing station data and inserts a fresh list.
     * This is used for the periodic background sync.
     */
    suspend fun clearAndSaveStations(stations: List<StationEntity>) {
        withContext(Dispatchers.IO) {
            stationDao.clearAllStations()
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
