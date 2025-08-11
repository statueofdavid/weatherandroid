package space.declared.weather.data.local

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.InputStream

// Make sure to include all your entities here
@Database(entities = [StationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "weather_app_database"
                )
                    // Add the callback here during the build process
                    .addCallback(AppDatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * A database callback to handle the initial population of the database.
     * The onCreate method is called only once, when the database is first created.
     */
    @Keep
    private class AppDatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                // Use a coroutine to perform the database population off the main thread
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(context, database.stationDao())
                }
            }
        }

        /**
         * Reads station data from a JSON file in the assets folder and inserts it
         * into the database. This is where you should put your data loading logic.
         */
        suspend fun populateDatabase(context: Context, stationDao: StationDao) {
            try {
                // Assuming your station data is in a file named "stations.json"
                // in your app's src/main/assets folder.
                val inputStream: InputStream = context.assets.open("stations.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val stationsArray = JSONArray(jsonString)

                val stationList = mutableListOf<StationEntity>()
                for (i in 0 until stationsArray.length()) {
                    val stationJson = stationsArray.getJSONObject(i)
                    val station = StationEntity(
                        id = stationJson.getInt("id").toString(),
                        name = stationJson.getString("name"),
                        latitude = stationJson.getDouble("latitude"),
                        longitude = stationJson.getDouble("longitude"),
                        type = stationJson.getString("type")
                    )
                    stationList.add(station)
                }

                // Insert all stations into the database in a single transaction
                stationDao.insertAll(stationList)

            } catch (e: Exception) {
                // Log any errors that occur during population
                android.util.Log.e("DatabasePopulation", "Error populating database from asset", e)

            }
        }
    }
}