package com.codexkd.vivoassistant.memory

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codexkd.vivoassistant.models.Message
import com.codexkd.vivoassistant.models.Routine
import com.codexkd.vivoassistant.utils.Constants
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════
// MESSAGE DAO
// ═══════════════════════════════════════════════════════
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSessionMessages(sessionId: String): List<Message>

    @Query("""
        SELECT * FROM messages 
        WHERE role IN ('user', 'assistant') 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getHistoryForAI(limit: Int = 12): List<Message>

    @Query("SELECT * FROM messages WHERE role = 'user' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentUserMessages(limit: Int = 20): List<Message>

    @Query("DELETE FROM messages WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getCount(): Int

    @Query("""
        SELECT * FROM messages 
        WHERE content LIKE '%' || :query || '%' 
        ORDER BY timestamp DESC 
        LIMIT 20
    """)
    suspend fun search(query: String): List<Message>
}

// ═══════════════════════════════════════════════════════
// ROUTINE DAO
// ═══════════════════════════════════════════════════════
@Dao
interface RoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: Routine)

    @Update
    suspend fun update(routine: Routine)

    @Delete
    suspend fun delete(routine: Routine)

    @Query("SELECT * FROM routines ORDER BY name ASC")
    fun getAllRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE isEnabled = 1 ORDER BY name ASC")
    suspend fun getEnabledRoutines(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): Routine?

    @Query("UPDATE routines SET executeCount = executeCount + 1, lastExecuted = :time WHERE id = :id")
    suspend fun incrementExecuteCount(id: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE routines SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM routines WHERE isBuiltIn = 0")
    suspend fun deleteCustomRoutines()
}

// ═══════════════════════════════════════════════════════
// CONTEXT ENTITY (lightweight memory of user habits)
// ═══════════════════════════════════════════════════════
@Entity(tableName = "user_context")
data class ContextEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface ContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(item: ContextEntity)

    @Query("SELECT * FROM user_context WHERE `key` = :key")
    suspend fun get(key: String): ContextEntity?

    @Query("SELECT * FROM user_context ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ContextEntity>

    @Query("DELETE FROM user_context WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM user_context")
    suspend fun clear()
}

// ═══════════════════════════════════════════════════════
// ROOM DATABASE
// ═══════════════════════════════════════════════════════
@Database(
    entities = [
        Message::class,
        Routine::class,
        ContextEntity::class
    ],
    version = Constants.MEMORY_DB_VERSION,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun routineDao(): RoutineDao
    abstract fun contextDao(): ContextDao

    companion object {
        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    Constants.MEMORY_DB_NAME
                )
                    .fallbackToDestructiveMigration() // Dev mode — use migrations in production
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun destroy() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
