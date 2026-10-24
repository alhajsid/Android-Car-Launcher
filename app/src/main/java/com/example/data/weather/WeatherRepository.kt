package com.example.data.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LiveWeatherData(
    val temperatureC: Int? = null,
    val condition: String? = null,
    val isLoaded: Boolean = false,
    val fetchedAtMs: Long = 0L,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN
)

class WeatherRepository(
    val cacheExpiryMs: Long = DEFAULT_CACHE_EXPIRY_MS,
    val minDistanceDisplacementMeters: Double = DEFAULT_MIN_DISTANCE_METERS,
    val failureBackoffMs: Long = DEFAULT_FAILURE_BACKOFF_MS
) {

    companion object {
        private const val TAG = "WeatherRepo"
        const val DEFAULT_CACHE_EXPIRY_MS = 15 * 60 * 1000L // 15 minutes
        const val DEFAULT_MIN_DISTANCE_METERS = 2_500.0     // 2.5 km meaningful movement
        const val DEFAULT_FAILURE_BACKOFF_MS = 60 * 1000L   // 1 minute backoff on transient failure

        /**
         * Pure-math Haversine distance in meters between two WGS84 coordinates.
         */
        fun calculateDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) {
                return Double.POSITIVE_INFINITY
            }
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadiusMeters * c
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val fetchMutex = Mutex()

    @Volatile
    private var lastFetchSuccessTimeMs = 0L
    @Volatile
    private var lastAttemptTimeMs = 0L
    @Volatile
    private var lastFetchedLat = Double.NaN
    @Volatile
    private var lastFetchedLon = Double.NaN
    @Volatile
    private var cachedWeather: LiveWeatherData? = null

    fun getCachedWeather(): LiveWeatherData? = cachedWeather

    /**
     * Determines whether a network weather refresh is warranted based on:
     * 1. Force refresh flag
     * 2. Cache expiry ([cacheExpiryMs])
     * 3. Meaningful vehicle movement (>= [minDistanceDisplacementMeters])
     * 4. Backoff after transient failure ([failureBackoffMs])
     */
    fun shouldFetchWeather(
        latitude: Double,
        longitude: Double,
        nowMs: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false
    ): Boolean {
        if (forceRefresh) return true
        val currentCache = cachedWeather
        if (currentCache == null || !currentCache.isLoaded) {
            return (nowMs - lastAttemptTimeMs) >= failureBackoffMs || lastAttemptTimeMs == 0L
        }

        val cacheExpired = (nowMs - lastFetchSuccessTimeMs) >= cacheExpiryMs
        val distanceMoved = calculateDistanceMeters(lastFetchedLat, lastFetchedLon, latitude, longitude)
        val movedMeaningfully = distanceMoved >= minDistanceDisplacementMeters

        if (!cacheExpired && !movedMeaningfully) {
            return false
        }

        // Respect failure backoff if a recent attempt failed
        if (lastAttemptTimeMs > lastFetchSuccessTimeMs && (nowMs - lastAttemptTimeMs) < failureBackoffMs) {
            return false
        }
        return true
    }

    /**
     * Fetches weather for [latitude], [longitude] with mutex deduplication, spatial/temporal caching,
     * and preservation of the last valid weather result during transient failures.
     */
    suspend fun fetchWeatherForLocation(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): LiveWeatherData? = fetchMutex.withLock {
        val now = System.currentTimeMillis()
        if (!shouldFetchWeather(latitude, longitude, now, forceRefresh)) {
            return@withLock cachedWeather
        }

        lastAttemptTimeMs = now

        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext cachedWeather
                    }
                    val body = response.body?.string() ?: return@withContext cachedWeather
                    val json = JSONObject(body)
                    val current = json.optJSONObject("current") ?: return@withContext cachedWeather
                    val temp = current.optDouble("temperature_2m", Double.NaN)
                    val code = current.optInt("weather_code", -1)

                    if (temp.isNaN()) return@withContext cachedWeather

                    val condition = decodeWmoCode(code)
                    val result = LiveWeatherData(
                        temperatureC = temp.roundToInt(),
                        condition = condition,
                        isLoaded = true,
                        fetchedAtMs = now,
                        latitude = latitude,
                        longitude = longitude
                    )
                    lastFetchSuccessTimeMs = now
                    lastFetchedLat = latitude
                    lastFetchedLon = longitude
                    cachedWeather = result
                    result
                }
            } catch (e: Exception) {
                Log.d(TAG, "Weather fetch skipped or offline (preserving cache): ${e.message}")
                cachedWeather
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
