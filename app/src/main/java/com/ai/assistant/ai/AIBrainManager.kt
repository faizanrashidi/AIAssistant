package com.ai.assistant.ai

import com.ai.assistant.data.repository.MemoryRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIBrainManager(
    private val memoryRepository: MemoryRepository,
    apiKey: String
) {

    // 1. Gemini Model Configuration
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    // 2. Main Intent & Response Processor
    suspend fun processUserQuery(userQuery: String): String = withContext(Dispatchers.IO) {
        try {
            // Step A: Local Encrypted Room DB se Memories fetch karein
            val storedMemoryContext = memoryRepository.buildMemoryContextForAI()

            // Step B: Master System Prompt Construct karein
            val systemInstruction = """
                You are an advanced Android OS AI System Agent. 
                You have access to the user's encrypted local memory.
                
                $storedMemoryContext
                
                Instructions:
                1. Answer the user's prompt directly using the Memory Context if relevant.
                2. If the user shares a NEW personal fact, preference, or detail (e.g., "My car number is 1234", "My brother is Alex"), extract it in a JSON format at the very end of your response using: [SAVE_MEMORY: {"category":"FACT", "key":"car_number", "value":"1234"}].
            """.trimIndent()

            // Step C: Gemini API Request Call
            val response = generativeModel.generateContent(
                content {
                    text("$systemInstruction\n\nUser Query: $userQuery")
                }
            )

            val responseText = response.text ?: "Sorry, I couldn't process that request."

            // Step D: Response se Facts Parse karke Database me Auto-Save karein
            parseAndSaveNewFacts(responseText)

            // Final cleaned response user ko return karein (Metadata strip karke)
            cleanResponseForUser(responseText)

        } catch (e: Exception) {
            "Error processing request: ${e.localizedMessage}"
        }
    }

    // 3. New Fact Auto-Extraction Engine
    private suspend fun parseAndSaveNewFacts(responseText: String) {
        val regex = "\\[SAVE_MEMORY:\\s*(\\{.*?\\})\\]".toRegex()
        val matches = regex.findAll(responseText)

        for (match in matches) {
            val jsonString = match.groupValues[1]
            try {
                // Quick JSON Parser for Memory extraction
                val category = extractJsonValue(jsonString, "category") ?: "GENERAL"
                val key = extractJsonValue(jsonString, "key") ?: "unknown"
                val value = extractJsonValue(jsonString, "value") ?: ""

                if (key.isNotEmpty() && value.isNotEmpty()) {
                    memoryRepository.rememberFact(category, key, value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Clean response for User Interface
    private fun cleanResponseForUser(text: String): String {
        return text.replace("\\[SAVE_MEMORY:.*?\\]".toRegex(), "").trim()
    }

    // Helper for extracting JSON values without heavy dependencies
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
