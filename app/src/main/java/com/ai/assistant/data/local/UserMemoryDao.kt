package com.ai.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {

    // 1. New Memory Add/Update karna
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemoryEntity)

    // 2. Keyword se past memory search karna (e.g., "Birthday" or "Doctor")
    @Query("SELECT * FROM user_memory WHERE memoryKey LIKE '%' || :query || '%' OR memoryValue LIKE '%' || :query || '%'")
    suspend fun searchMemory(query: String): List<UserMemoryEntity>

    // 3. Category wise memories fetch karna
    @Query("SELECT * FROM user_memory WHERE category = :category")
    fun getMemoriesByCategory(category: String): Flow<List<UserMemoryEntity>>

    // 4. Sabhi stored memories fetch karna (AI Context Injection ke liye)
    @Query("SELECT * FROM user_memory ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<UserMemoryEntity>

    // 5. Specific Memory Delete karna
    @Query("DELETE FROM user_memory WHERE memoryKey = :key")
    suspend fun deleteMemoryByKey(key: String)

    // 6. Complete Memory Wipe ("Forget Everything")
    @Query("DELETE FROM user_memory")
    suspend fun clearAllMemories()
}
