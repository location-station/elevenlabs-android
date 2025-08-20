package net.locationstation.elevenlabs.ws.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.locationstation.elevenlabs.ws.logging.Logger

/**
 * Manages Android audio focus for the ElevenLabs SDK.
 * This is a key differentiator from official SDKs - proper Android audio focus handling.
 * 
 * Features:
 * - Automatic audio focus request/release
 * - Ducking support for notifications
 * - Transient loss handling (phone calls)
 * - Bluetooth routing support
 * - Audio mode switching for voice calls
 */
class AndroidAudioManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    
    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    
    private val _focusState = MutableStateFlow(AudioFocusState.NONE)
    val focusState: StateFlow<AudioFocusState> = _focusState.asStateFlow()
    
    /**
     * Audio state information.
     */
    data class AudioState(
        val hasFocus: Boolean = false,
        val isBluetoothConnected: Boolean = false,
        val isSpeakerphoneOn: Boolean = false,
        val isWiredHeadsetConnected: Boolean = false,
        val volume: Int = 0,
        val maxVolume: Int = 0,
        val audioMode: AudioMode = AudioMode.NORMAL
    )
    
    /**
     * Audio focus states.
     */
    enum class AudioFocusState {
        NONE,
        GAINED,
        LOST,
        LOST_TRANSIENT,
        LOST_TRANSIENT_CAN_DUCK
    }
    
    /**
     * Audio modes for different use cases.
     */
    enum class AudioMode {
        NORMAL,
        VOICE_COMMUNICATION,
        IN_CALL,
        RINGTONE
    }
    
    /**
     * Configuration for audio focus requests.
     */
    data class AudioFocusConfig(
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        val focusGain: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        val acceptsDelayedFocus: Boolean = false,
        val pauseOnDuck: Boolean = false,
        val onFocusChange: ((AudioFocusState) -> Unit)? = null
    )
    
    /**
     * Requests audio focus for conversation.
     */
    fun requestAudioFocus(config: AudioFocusConfig = AudioFocusConfig()): Boolean {
        Logger.d("Requesting audio focus")
        
        // Create focus change listener
        focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            val state = when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Logger.d("Audio focus gained")
                    AudioFocusState.GAINED
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Logger.d("Audio focus lost")
                    AudioFocusState.LOST
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Logger.d("Audio focus lost transiently")
                    AudioFocusState.LOST_TRANSIENT
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Logger.d("Audio focus lost transiently, can duck")
                    AudioFocusState.LOST_TRANSIENT_CAN_DUCK
                }
                else -> {
                    Logger.w("Unknown audio focus change: $focusChange")
                    return@OnAudioFocusChangeListener
                }
            }
            
            _focusState.value = state
            config.onFocusChange?.invoke(state)
            updateAudioState()
        }
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusApi26(config)
        } else {
            requestAudioFocusLegacy(config)
        }
        
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _focusState.value = AudioFocusState.GAINED
            updateAudioState()
            Logger.d("Audio focus granted")
            return true
        }
        
        Logger.w("Audio focus request failed: $result")
        return false
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusApi26(config: AudioFocusConfig): Int {
        audioFocusRequest = AudioFocusRequest.Builder(config.focusGain)
            .setAudioAttributes(config.audioAttributes)
            .setAcceptsDelayedFocusGain(config.acceptsDelayedFocus)
            .setOnAudioFocusChangeListener(focusChangeListener!!)
            .build()
        
        return audioManager.requestAudioFocus(audioFocusRequest!!)
    }
    
    @Suppress("DEPRECATION")
    private fun requestAudioFocusLegacy(config: AudioFocusConfig): Int {
        return audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_VOICE_CALL,
            config.focusGain
        )
    }
    
    /**
     * Releases audio focus.
     */
    fun releaseAudioFocus() {
        Logger.d("Releasing audio focus")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            focusChangeListener?.let {
                audioManager.abandonAudioFocus(it)
            }
        }
        
        audioFocusRequest = null
        focusChangeListener = null
        _focusState.value = AudioFocusState.NONE
        updateAudioState()
    }
    
    /**
     * Sets the audio mode for voice communication.
     */
    fun setVoiceCommunicationMode() {
        Logger.d("Setting voice communication mode")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        _audioState.value = _audioState.value.copy(audioMode = AudioMode.VOICE_COMMUNICATION)
    }
    
    /**
     * Restores normal audio mode.
     */
    fun setNormalMode() {
        Logger.d("Setting normal audio mode")
        audioManager.mode = AudioManager.MODE_NORMAL
        _audioState.value = _audioState.value.copy(audioMode = AudioMode.NORMAL)
    }
    
    /**
     * Enables or disables speakerphone.
     */
    fun setSpeakerphoneOn(on: Boolean) {
        Logger.d("Setting speakerphone: $on")
        audioManager.isSpeakerphoneOn = on
        updateAudioState()
    }
    
    /**
     * Checks if Bluetooth audio is connected.
     */
    fun isBluetoothConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { 
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
        }
    }
    
    /**
     * Checks if wired headset is connected.
     */
    fun isWiredHeadsetConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { 
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn
        }
    }
    
    /**
     * Routes audio to Bluetooth if available.
     */
    fun routeToBluetoothIfAvailable(): Boolean {
        if (!isBluetoothConnected()) {
            Logger.d("No Bluetooth device connected")
            return false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+, use setCommunicationDevice
            val devices = audioManager.availableCommunicationDevices
            val bluetoothDevice = devices.firstOrNull {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            bluetoothDevice?.let {
                audioManager.setCommunicationDevice(it)
                Logger.d("Routed to Bluetooth device: ${it.productName}")
                return true
            }
        } else {
            // Legacy Bluetooth SCO routing
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Logger.d("Started Bluetooth SCO")
                return true
            }
        }
        
        return false
    }
    
    /**
     * Stops Bluetooth audio routing.
     */
    fun stopBluetoothRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
        Logger.d("Stopped Bluetooth routing")
    }
    
    /**
     * Gets the current volume level.
     */
    fun getVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }
    
    /**
     * Sets the volume level.
     */
    fun setVolume(volume: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val safeVolume = volume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            safeVolume,
            AudioManager.FLAG_SHOW_UI
        )
        updateAudioState()
    }
    
    /**
     * Adjusts volume up or down.
     */
    fun adjustVolume(direction: VolumeDirection) {
        val flag = when (direction) {
            VolumeDirection.UP -> AudioManager.ADJUST_RAISE
            VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
            VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
            VolumeDirection.UNMUTE -> AudioManager.ADJUST_UNMUTE
        }
        
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            flag,
            AudioManager.FLAG_SHOW_UI
        )
        updateAudioState()
    }
    
    /**
     * Volume adjustment directions.
     */
    enum class VolumeDirection {
        UP, DOWN, MUTE, UNMUTE
    }
    
    /**
     * Updates the current audio state.
     */
    private fun updateAudioState() {
        _audioState.value = AudioState(
            hasFocus = _focusState.value == AudioFocusState.GAINED,
            isBluetoothConnected = isBluetoothConnected(),
            isSpeakerphoneOn = audioManager.isSpeakerphoneOn,
            isWiredHeadsetConnected = isWiredHeadsetConnected(),
            volume = getVolume(),
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
            audioMode = when (audioManager.mode) {
                AudioManager.MODE_IN_COMMUNICATION -> AudioMode.VOICE_COMMUNICATION
                AudioManager.MODE_IN_CALL -> AudioMode.IN_CALL
                AudioManager.MODE_RINGTONE -> AudioMode.RINGTONE
                else -> AudioMode.NORMAL
            }
        )
    }
    
    /**
     * Releases all audio resources.
     */
    fun release() {
        releaseAudioFocus()
        setNormalMode()
        stopBluetoothRouting()
    }
}
