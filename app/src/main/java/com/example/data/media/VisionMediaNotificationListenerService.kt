package com.example.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RealMediaState(
    val isAccessGranted: Boolean = false,
    val hasActivePlayer: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playerPackage: String? = null
)

class VisionMediaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "VisionMediaService"
        var instance: VisionMediaNotificationListenerService? = null
            private set

        private val _mediaState = MutableStateFlow(RealMediaState())
        val mediaState: StateFlow<RealMediaState> = _mediaState.asStateFlow()

        private var activeController: MediaController? = null

        fun play() {
            try {
                activeController?.transportControls?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing: ${e.message}")
            }
        }

        fun pause() {
            try {
                activeController?.transportControls?.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing: ${e.message}")
            }
        }

        fun togglePlayPause() {
            try {
                val state = activeController?.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    activeController?.transportControls?.pause()
                } else {
                    activeController?.transportControls?.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling playback: ${e.message}")
            }
        }

        fun skipNext() {
            try {
                activeController?.transportControls?.skipToNext()
            } catch (e: Exception) {
                Log.e(TAG, "Error next: ${e.message}")
            }
        }

        fun skipPrevious() {
            try {
                activeController?.transportControls?.skipToPrevious()
            } catch (e: Exception) {
                Log.e(TAG, "Error prev: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSessionManager: MediaSessionManager? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _mediaState.value = _mediaState.value.copy(isAccessGranted = true)

        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, VisionMediaNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            updateActiveController(controllers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active media sessions: ${e.message}")
        }

        startProgressTicker()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {}
        _mediaState.value = RealMediaState(isAccessGranted = false)
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        val candidate = controllers?.firstOrNull { ctrl ->
            val state = ctrl.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        } ?: controllers?.firstOrNull()

        activeController = candidate
        readCurrentMedia()

        candidate?.registerCallback(object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                readCurrentMedia()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                readCurrentMedia()
            }
        })
    }

    private fun readCurrentMedia() {
        val ctrl = activeController
        if (ctrl == null) {
            _mediaState.value = RealMediaState(isAccessGranted = true, hasActivePlayer = false)
            return
        }

        val metadata = ctrl.metadata
        val pbState = ctrl.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = pbState?.position ?: 0L
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING

        val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

        _mediaState.value = RealMediaState(
            isAccessGranted = true,
            hasActivePlayer = title.isNotBlank() || artist.isNotBlank(),
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            progress = progress,
            durationMs = duration,
            positionMs = position,
            playerPackage = ctrl.packageName
        )
    }

    private fun startProgressTicker() {
        scope.launch {
            while (isActive) {
                delay(1000)
                if (_mediaState.value.isPlaying) {
                    val ctrl = activeController
                    val pbState = ctrl?.playbackState
                    val duration = _mediaState.value.durationMs
                    if (pbState != null && duration > 0) {
                        val currentPos = pbState.position
                        val progress = (currentPos.toFloat() / duration).coerceIn(0f, 1f)
                        _mediaState.value = _mediaState.value.copy(
                            positionMs = currentPos,
                            progress = progress
                        )
                    }
                }
            }
        }
    }
}
