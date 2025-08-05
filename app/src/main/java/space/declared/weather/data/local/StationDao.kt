package space.declared.weather.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for the stations table.
 * Defines the database interactions.
 */
@Dao
interface StationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("DELETE FROM stations WHERE type = :stationType")
    suspend fun clearStationsByType(stationType: String)

    @Query("SELECT COUNT(id) FROM stations WHERE type = :stationType")
    suspend fun getStationCountByType(stationType: String): Int

    @Query("SELECT * FROM stations WHERE type = 'NOAA' AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getNoaaStationsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StationEntity>

    @Query("SELECT * FROM stations WHERE type = 'USGS' AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getUsgsStationsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StationEntity>
}

