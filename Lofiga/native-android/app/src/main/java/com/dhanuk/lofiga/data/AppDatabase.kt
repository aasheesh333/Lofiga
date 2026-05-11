package com.dhanuk.lofiga.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// --- Entities ---

@Entity(tableName = "saved_configs")
data class SavedConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    @ColumnInfo(name = "tempo") val tempo: Float = 1.0f,
    @ColumnInfo(name = "pitch") val pitch: Float = 0f,
    @ColumnInfo(name = "reverb") val reverb: Float = 0f,
    @ColumnInfo(name = "delay") val delay: Float = 0f,
    @ColumnInfo(name = "bass") val bass: Float = 0f,
    @ColumnInfo(name = "treble_cut") val trebleCut: Float = 0f,
    @ColumnInfo(name = "rain_volume") val rainVolume: Float = 0f,
    @ColumnInfo(name = "vinyl_volume") val vinylVolume: Float = 0f,
    @ColumnInfo(name = "wind_volume") val windVolume: Float = 0f,
    @ColumnInfo(name = "tape_volume") val tapeVolume: Float = 0f
)

@Entity(tableName = "custom_presets")
data class CustomPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "tempo") val tempo: Float = 1.0f,
    @ColumnInfo(name = "pitch") val pitch: Float = 0f,
    @ColumnInfo(name = "reverb") val reverb: Float = 0f,
    @ColumnInfo(name = "delay") val delay: Float = 0f,
    @ColumnInfo(name = "bass") val bass: Float = 0f,
    @ColumnInfo(name = "treble_cut") val trebleCut: Float = 0f,
    @ColumnInfo(name = "rain_volume") val rainVolume: Float = 0f,
    @ColumnInfo(name = "vinyl_volume") val vinylVolume: Float = 0f,
    @ColumnInfo(name = "wind_volume") val windVolume: Float = 0f,
    @ColumnInfo(name = "tape_volume") val tapeVolume: Float = 0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface SavedConfigDao {
    @Query("SELECT * FROM saved_configs ORDER BY saved_at DESC")
    suspend fun getAll(): List<SavedConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SavedConfigEntity)

    @Query("DELETE FROM saved_configs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_configs WHERE file_path = :filePath")
    suspend fun deleteByPath(filePath: String)
}

@Dao
interface CustomPresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY created_at DESC")
    suspend fun getAll(): List<CustomPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: CustomPresetEntity): Long

    @Query("DELETE FROM custom_presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_presets")
    suspend fun deleteAll()
}

// --- Database ---

@Database(
    entities = [SavedConfigEntity::class, CustomPresetEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedConfigDao(): SavedConfigDao
    abstract fun customPresetDao(): CustomPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Initial migration: schema unchanged, version bump for future-safe upgrades
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lofiga_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}