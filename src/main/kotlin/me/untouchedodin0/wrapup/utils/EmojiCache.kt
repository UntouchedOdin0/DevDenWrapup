package me.untouchedodin0.wrapup.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.system.measureTimeMillis

object EmojiCache {
    private val unicodeToName = HashMap<String, String>()
    private val unicodeEmojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")

    fun initialize() {
        val timeTaken = measureTimeMillis {
            try {
                val inputStream = object {}.javaClass.getResourceAsStream("/emojis.json")
                    ?: throw IllegalStateException("Could not find emojis.json in resources!")

                val jsonContent = inputStream.bufferedReader().use { it.readText() }

                val gson = Gson()
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val rawData: Map<String, List<String>> = gson.fromJson(jsonContent, type)

                rawData.forEach { (unicode, names) ->
                    names.firstOrNull()?.let { unicodeToName[unicode] = it }
                }
            } catch (e: Exception) {
                println("❌ Failed to load Emoji JSON!")
                e.printStackTrace()
                return // Exit early if it fails
            }
        }

        // Convert ms to seconds (e.g., 1.25s)
        val seconds = timeTaken / 1000.0
        println("✅ EmojiCache: Loaded ${unicodeToName.size} emojis in ${seconds}s")
    }

    fun extractEmojis(content: String): List<String> {
        return unicodeEmojiRegex.findAll(content).mapNotNull {
            unicodeToName[it.value]
        }.toList()
    }
}