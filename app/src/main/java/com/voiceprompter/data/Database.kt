package com.voiceprompter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Sentence index the user last reached, so playback can resume where it stopped. */
    val lastPosition: Int = 0
)

@Dao
interface ScriptDao {

    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Script>>

    @Query(
        """
        SELECT * FROM scripts
        WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun observeById(id: Long): Flow<Script?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: Long): Script?

    @Insert
    suspend fun insert(script: Script): Long

    @Update
    suspend fun update(script: Script)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scripts SET lastPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}

@Database(entities = [Script::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scriptDao(): ScriptDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "voice_prompter.db"
            ).build()
    }
}
