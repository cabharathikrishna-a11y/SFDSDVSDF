package com.example.util

import android.content.Context

object LocalGemmaInferenceManager {
    fun findAvailableModelPath(context: Context): String? = null
    fun initialize(context: Context): Boolean = false
    suspend fun generateLocalResponse(context: Context, prompt: String): String? = null
    fun isNativeEngineActive(): Boolean = false
    fun getActiveModelPath(): String? = null
    fun close() {}
}
