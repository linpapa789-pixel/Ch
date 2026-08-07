package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "valid_tokens")
data class ValidToken(
    @PrimaryKey
    val token: String,
    val sessionId: String,
    val captchaText: String,
    val plan: String? = null,
    val time: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ValidTokenDao {
    @Query("SELECT * FROM valid_tokens ORDER BY timestamp DESC")
    fun getAllTokens(): Flow<List<ValidToken>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: ValidToken)

    @Query("DELETE FROM valid_tokens WHERE token = :token")
    suspend fun deleteToken(token: String)

    @Query("DELETE FROM valid_tokens")
    suspend fun clearAllTokens()
}

@Database(entities = [ValidToken::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun validTokenDao(): ValidTokenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "token_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class TokenRepository(private val validTokenDao: ValidTokenDao) {
    val allTokens: Flow<List<ValidToken>> = validTokenDao.getAllTokens()

    suspend fun insertToken(token: ValidToken) {
        validTokenDao.insertToken(token)
    }

    suspend fun deleteToken(token: String) {
        validTokenDao.deleteToken(token)
    }

    suspend fun clearAllTokens() {
        validTokenDao.clearAllTokens()
    }
}
