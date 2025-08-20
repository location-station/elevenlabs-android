package net.locationstation.elevenlabs.ws.data.audio

import android.media.AudioManager
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for AndroidAudioManager.
 * Tests audio focus management, Bluetooth routing, and volume control.
 * 
 * Uses Robolectric to provide Android framework implementations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Use API 28 (stable with Robolectric)
class AndroidAudioManagerTest {
    
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var androidAudioManager: AndroidAudioManager
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        
        // Mock all the audio manager properties that might be accessed
        every { audioManager.isSpeakerphoneOn } returns false
        every { audioManager.isBluetoothA2dpOn } returns false
        every { audioManager.isBluetoothScoOn } returns false
        @Suppress("DEPRECATION")
        every { audioManager.isWiredHeadsetOn } returns false
        every { audioManager.mode } returns AudioManager.MODE_NORMAL
        every { audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) } returns 5
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) } returns 10
        
        androidAudioManager = AndroidAudioManager(context)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `requestAudioFocus should request focus and return true when granted`() {
        // Given - API 28 uses AudioFocusRequest
        every { 
            audioManager.requestAudioFocus(any())
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        // When
        val result = androidAudioManager.requestAudioFocus()
        
        // Then
        assertTrue(result)
        assertEquals(AndroidAudioManager.AudioFocusState.GAINED, androidAudioManager.focusState.value)
        verify { 
            audioManager.requestAudioFocus(any())
        }
    }
    
    @Test
    fun `requestAudioFocus should return false when not granted`() {
        // Given - API 28 uses AudioFocusRequest
        every { 
            audioManager.requestAudioFocus(any())
        } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED
        
        // When
        val result = androidAudioManager.requestAudioFocus()
        
        // Then
        assertFalse(result)
        assertEquals(AndroidAudioManager.AudioFocusState.NONE, androidAudioManager.focusState.value)
    }
    
    @Test
    fun `releaseAudioFocus should abandon audio focus`() {
        // Given - API 28 uses AudioFocusRequest
        every { 
            audioManager.requestAudioFocus(any())
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        androidAudioManager.requestAudioFocus()
        
        // When
        androidAudioManager.releaseAudioFocus()
        
        // Then
        assertEquals(AndroidAudioManager.AudioFocusState.NONE, androidAudioManager.focusState.value)
        verify { 
            audioManager.abandonAudioFocusRequest(any())
        }
    }
    
    @Test
    @Config(sdk = [21]) // Test with API 21 for legacy audio focus
    fun `requestAudioFocus should work with legacy API`() {
        // Re-create with API 21
        androidAudioManager = AndroidAudioManager(context)
        
        // Given
        every { 
            audioManager.requestAudioFocus(
                any<AudioManager.OnAudioFocusChangeListener>(),
                any<Int>(),
                any<Int>()
            )
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        // When
        val result = androidAudioManager.requestAudioFocus()
        
        // Then
        assertTrue(result)
        assertEquals(AndroidAudioManager.AudioFocusState.GAINED, androidAudioManager.focusState.value)
        verify { audioManager.requestAudioFocus(
            any<AudioManager.OnAudioFocusChangeListener>(),
            any<Int>(),
            any<Int>()
        ) }
    }
    
    @Test
    fun `setVoiceCommunicationMode should set correct audio mode`() {
        // When
        androidAudioManager.setVoiceCommunicationMode()
        
        // Then
        verify { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        assertEquals(AndroidAudioManager.AudioMode.VOICE_COMMUNICATION, androidAudioManager.audioState.value.audioMode)
    }
    
    @Test
    fun `setSpeakerphoneOn should enable speakerphone`() {
        // When
        androidAudioManager.setSpeakerphoneOn(true)
        
        // Then
        verify { audioManager.isSpeakerphoneOn = true }
    }
    
    @Test
    fun `isBluetoothConnected should return true when Bluetooth is on`() {
        // Given - For API 28 (>= M/23), it will use getDevices()
        val mockDevice = mockk<android.media.AudioDeviceInfo>(relaxed = true)
        every { mockDevice.type } returns android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockDevice)
        
        // When
        val result = androidAudioManager.isBluetoothConnected()
        
        // Then
        assertTrue(result)
        verify { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
    }
    
    @Test
    fun `routeToBluetoothIfAvailable should start SCO when Bluetooth is connected`() {
        // Given - For API 28, it will check Bluetooth using getDevices
        val mockDevice = mockk<android.media.AudioDeviceInfo>(relaxed = true)
        every { mockDevice.type } returns android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockDevice)
        every { audioManager.isBluetoothScoOn } returns false
        
        // When
        val result = androidAudioManager.routeToBluetoothIfAvailable()
        
        // Then
        assertTrue(result)
        verify { audioManager.startBluetoothSco() }
        verify { audioManager.isBluetoothScoOn = true }
    }
    
    @Test
    fun `routeToBluetoothIfAvailable should return false when no Bluetooth device`() {
        // Given - API 28 uses getDevices
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
        
        // When
        val result = androidAudioManager.routeToBluetoothIfAvailable()
        
        // Then
        assertFalse(result)
        verify(exactly = 0) { audioManager.startBluetoothSco() }
    }
    
    @Test
    fun `setVolume should set volume within valid range`() {
        // Given - already mocked in setup
        
        // When
        androidAudioManager.setVolume(5)
        
        // Then
        verify { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 5, AudioManager.FLAG_SHOW_UI) }
    }
    
    @Test
    fun `setVolume should clamp volume to max when too high`() {
        // Given - already mocked in setup
        
        // When
        androidAudioManager.setVolume(15)
        
        // Then
        verify { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 10, AudioManager.FLAG_SHOW_UI) }
    }
    
    @Test
    fun `adjustVolume should adjust stream volume`() {
        // Given - already mocked in setup
        
        // When
        androidAudioManager.adjustVolume(AndroidAudioManager.VolumeDirection.UP)
        
        // Then
        verify { 
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }
    
    @Test
    fun `release should clean up all resources`() {
        // Given - API 28 uses AudioFocusRequest
        every { 
            audioManager.requestAudioFocus(any())
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        
        androidAudioManager.requestAudioFocus()
        androidAudioManager.setVoiceCommunicationMode()
        every { audioManager.isBluetoothScoOn } returns true
        
        // When
        androidAudioManager.release()
        
        // Then
        assertEquals(AndroidAudioManager.AudioFocusState.NONE, androidAudioManager.focusState.value)
        assertEquals(AndroidAudioManager.AudioMode.NORMAL, androidAudioManager.audioState.value.audioMode)
        verify { audioManager.abandonAudioFocusRequest(any()) }
        verify { audioManager.mode = AudioManager.MODE_NORMAL }
        verify { audioManager.isBluetoothScoOn = false }
        verify { audioManager.stopBluetoothSco() }
    }

    @Test
    @Config(sdk = [21]) // Use legacy API for easier listener capture
    fun `audio focus listener should update state on focus changes`() = runTest {
        // Re-create with API 21 for legacy focus handling
        androidAudioManager = AndroidAudioManager(context)
        
        // Given
        val focusChangeListenerSlot = slot<AudioManager.OnAudioFocusChangeListener>()
        
        every {
            audioManager.requestAudioFocus(
                capture(focusChangeListenerSlot),
                eq(AudioManager.STREAM_VOICE_CALL),
                eq(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            )
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        var callbackState: AndroidAudioManager.AudioFocusState? = null
        val config = AndroidAudioManager.AudioFocusConfig(
            onFocusChange = { state -> callbackState = state }
        )

        // When
        androidAudioManager.requestAudioFocus(config)
        
        // Simulate focus loss
        focusChangeListenerSlot.captured.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        // Then
        assertEquals(AndroidAudioManager.AudioFocusState.LOST, callbackState)
        assertEquals(AndroidAudioManager.AudioFocusState.LOST, androidAudioManager.focusState.value)
    }
    
    @Test
    @Config(sdk = [21]) // Use legacy API for easier listener capture  
    fun `audio focus changes should update state correctly`() {
        // Re-create with API 21 for legacy focus handling
        androidAudioManager = AndroidAudioManager(context)
        
        // Given
        val focusChangeListenerSlot = slot<AudioManager.OnAudioFocusChangeListener>()
        
        every {
            audioManager.requestAudioFocus(
                capture(focusChangeListenerSlot),
                any<Int>(),
                any<Int>()
            )
        } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        androidAudioManager.requestAudioFocus()
        
        // Test transient loss
        focusChangeListenerSlot.captured.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(AndroidAudioManager.AudioFocusState.LOST_TRANSIENT, androidAudioManager.focusState.value)
        
        // Test transient loss can duck
        focusChangeListenerSlot.captured.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(AndroidAudioManager.AudioFocusState.LOST_TRANSIENT_CAN_DUCK, androidAudioManager.focusState.value)
        
        // Test regain
        focusChangeListenerSlot.captured.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(AndroidAudioManager.AudioFocusState.GAINED, androidAudioManager.focusState.value)
    }
    
    @Test
    @Config(sdk = [26]) // Test with API 26 for getDevices (API 23 has Robolectric issues)
    fun `isBluetoothConnected should work with API 23 and above`() {
        // Re-create with API 26 (M+)
        androidAudioManager = AndroidAudioManager(context)
        
        // Given - getDevices returns empty array
        every { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
        
        // When
        val result = androidAudioManager.isBluetoothConnected()
        
        // Then
        assertFalse(result)
        verify { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
    }
    
    @Test
    @Config(sdk = [31]) // Test with API 31 for communication device APIs
    fun `stopBluetoothRouting should clear communication device on API 31`() {
        // Re-create with API 31
        androidAudioManager = AndroidAudioManager(context)
        
        // When
        androidAudioManager.stopBluetoothRouting()
        
        // Then
        verify { audioManager.clearCommunicationDevice() }
    }
}
