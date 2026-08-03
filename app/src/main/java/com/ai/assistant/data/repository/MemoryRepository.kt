package com.ai.assistant.data.repository

import com.ai.assistant.data.local.UserMemoryDao
import com.ai.assistant.data.local.UserMemoryEntity

class MemoryRepository(private val memoryDao: UserMemoryDao) {

    // Fact Auto-Save Engine
    suspend fun rememberFact(category: String, key: String, value: String) {
        val memory = UserMemoryEntity(
            category = category.uppercase(),
            memoryKey = key.lowercase(),
            memoryValue = value
        )
        memoryDao.saveMemory(memory)
    }

    // Direct Memory Query
    suspend fun queryMemory(userQuery: String): String {
        val results = memoryDao.searchMemory(userQuery.lowercase())
        if (results.isEmpty()) return "No relevant past memory found."

        val memoryContext = StringBuilder("Stored Relevant Memories:\n")
        results.forEach {
            memoryContext.append("- ${it.memoryKey}: ${it.memoryValue}\n")
        }
        return memoryContext.toString()
    }

    // AI Prompt Injector: Sabhi facts ko system prompt context me inject karna
    suspend fun buildMemoryContextForAI(): String {
        val memories = memoryDao.getAllMemories()
        if (memories.isEmpty()) return ""

        val builder = StringBuilder("User Personal Memory Context:\n")
        memories.forEach {
            builder.append("[${it.category}] ${it.memoryKey} = ${it.memoryValue}\n")
        }
        return builder.toString()
    }

    // Complete Memory Reset
    suspend fun wipeMemory() {
        memoryDao.clearAllMemories()
    }
}
