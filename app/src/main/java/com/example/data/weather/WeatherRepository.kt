package com.example.data.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveWeatherData(
    val temperatureC: Int? = null,
    val condition: String? = null,
    val isLoaded: Boolean = false
)

class WeatherRepository {

    companion object {
        private const val TAG = "WeatherRepo"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var lastFetchTime = 0L
    private var cachedWeather: LiveWeatherData? = null

    suspend fun fetchWeatherForLocation(latitude: Double, longitude: Double): LiveWeatherData? {
        val now = System.currentTimeMillis()
        // Cache weather for 15 minutes to preserve network and battery
        if (cachedWeather != null && (now - lastFetchTime) < 15 * 60 * 1000L) {
            return cachedWeather
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val current = json.optJSONObject("current") ?: return@withContext null
                    val temp = current.optDouble("temperature_2m", Double.NaN)
                    val code = current.optInt("weather_code", -1)

                    if (temp.isNaN()) return@withContext null

                    val condition = decodeWmoCode(code)
                    val result = LiveWeatherData(
                        temperatureC = Math.round(temp).toInt(),
                        condition = condition,
                        isLoaded = true
                    )
                    lastFetchTime = now
                    cachedWeather = result
                    result
                }
            } catch (e: Exception) {
                Log.d(TAG, "Weather fetch skipped or offline: ${e.message}")
                null
            }
        }
    }

    private fun decodeWmoCode(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1, 2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Rain Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Fair"
        }
    }
}
