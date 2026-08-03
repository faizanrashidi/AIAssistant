package com.ai.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memory")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val category: String, // e.g., "PERSONAL_FACT", "PREFERENCE", "MEDICAL", "CONTACT_INFO"
    val memoryKey: String, // e.g., "BIRTHDAY", "FAVORITE_FOOD", "AADHAAR_NUMBER"
    val memoryValue: String, // e.g., "15 March", "Pizza", "1234-5678-9012"
    
    val timestamp: Long = System.currentTimeMillis()
)

