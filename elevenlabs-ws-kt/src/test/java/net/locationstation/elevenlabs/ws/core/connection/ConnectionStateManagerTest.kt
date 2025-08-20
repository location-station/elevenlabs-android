package net.locationstation.elevenlabs.ws.core.connection

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.locationstation.elevenlabs.ws.logging.Logger
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ConnectionStateManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateManagerTest {
    
    private lateinit var stateManager: ConnectionStateManager
    
    @Before
    fun setup() {
        // Set up test logger that doesn't use Android Log
        Logger.setLogWriter(object : Logger.LogWriter {
            override fun log(level: Logger.LogLevel, tag: String, message: String, throwable: Throwable?) {
                // Print to console for test visibility
                println("[$level] $tag: $message")
                throwable?.printStackTrace()
            }
        })
        
        stateManager = ConnectionStateManager()
    }
    
    @Test
    fun `initial state should be Idle`() = runTest {
        assertThat(stateManager.state.value).isInstanceOf(ConnectionState.Idle::class.java)
        assertThat(stateManager.isConnected()).isFalse()
        assertThat(stateManager.isTransitioning()).isFalse()
    }
    
    @Test
    fun `valid transition from Idle to Connecting should succeed`() = runTest {
        val result = stateManager.transitionTo(ConnectionState.Connecting())
        
        assertThat(result).isTrue()
        assertThat(stateManager.state.value).isInstanceOf(ConnectionState.Connecting::class.java)
        assertThat(stateManager.isTransitioning()).isTrue()
    }
    
    @Test
    fun `invalid transition from Idle to Connected should fail`() = runTest {
        val initialState = stateManager.state.value
        val result = stateManager.transitionTo(
            ConnectionState.Connected(sessionId = "test-session")
        )
        
        assertThat(result).isFalse()
        assertThat(stateManager.state.value).isEqualTo(initialState)
    }
    
    @Test
    fun `state events are emitted on valid transitions`() = runTest {
        stateManager.events.test {
            stateManager.transitionTo(ConnectionState.Connecting())
            
            val event = awaitItem()
            assertThat(event).isInstanceOf(ConnectionEvent.StateChanged::class.java)
            
            val stateChanged = event as ConnectionEvent.StateChanged
            assertThat(stateChanged.from).isInstanceOf(ConnectionState.Idle::class.java)
            assertThat(stateChanged.to).isInstanceOf(ConnectionState.Connecting::class.java)
            
            // Cancel the test to clean up
            cancel()
        }
    }
    
    @Test
    fun `complete connection lifecycle should work correctly`() = runTest {
        // Test state transitions without relying on exact event ordering
        
        // Connect
        assertThat(stateManager.transitionTo(ConnectionState.Connecting())).isTrue()
        assertThat(stateManager.state.value).isInstanceOf(ConnectionState.Connecting::class.java)
        
        assertThat(stateManager.transitionTo(
            ConnectionState.Connected("session-123")
        )).isTrue()
        val connectedState = stateManager.state.value as ConnectionState.Connected
        assertThat(connectedState.sessionId).isEqualTo("session-123")
        assertThat(stateManager.isConnected()).isTrue()
        
        // Disconnect
        assertThat(stateManager.transitionTo(
            ConnectionState.Disconnecting(DisconnectReason.USER_INITIATED)
        )).isTrue()
        assertThat(stateManager.state.value).isInstanceOf(ConnectionState.Disconnecting::class.java)
        
        assertThat(stateManager.transitionTo(
            ConnectionState.Disconnected(
                DisconnectReason.USER_INITIATED, 
                canReconnect = false
            )
        )).isTrue()
        val disconnectedState = stateManager.state.value as ConnectionState.Disconnected
        assertThat(disconnectedState.reason).isEqualTo(DisconnectReason.USER_INITIATED)
        assertThat(disconnectedState.canReconnect).isFalse()
        assertThat(stateManager.isConnected()).isFalse()
    }
    
    @Test
    fun `events are properly emitted during state transitions`() = runTest {
        // Use a simple approach without Turbine to avoid timing issues
        val events = mutableListOf<ConnectionEvent>()
        
        // Collect events using launch instead of turbine
        val collectJob = launch {
            stateManager.events.collect { event ->
                events.add(event)
            }
        }
        
        // Small delay to ensure collector starts
        delay(50)
        
        // Perform first transition
        stateManager.transitionTo(ConnectionState.Connecting())
        delay(50) // Allow event to be emitted
        
        // Perform second transition
        stateManager.transitionTo(ConnectionState.Connected("test-session"))
        delay(50) // Allow events to be emitted
        
        // Stop collecting
        collectJob.cancel()

        // Verify we got at least the state change events
        assertThat(events.size).isAtLeast(2)
        
        // Find the state change events
        val stateChangeEvents = events.filterIsInstance<ConnectionEvent.StateChanged>()
        assertThat(stateChangeEvents).hasSize(2)
        
        // Verify first transition: Idle -> Connecting
        val firstTransition = stateChangeEvents[0]
        assertThat(firstTransition.from).isInstanceOf(ConnectionState.Idle::class.java)
        assertThat(firstTransition.to).isInstanceOf(ConnectionState.Connecting::class.java)
        
        // Verify second transition: Connecting -> Connected
        val secondTransition = stateChangeEvents[1]
        assertThat(secondTransition.from).isInstanceOf(ConnectionState.Connecting::class.java)
        assertThat(secondTransition.to).isInstanceOf(ConnectionState.Connected::class.java)
        
        // Verify we also got a quality event
        val qualityEvents = events.filterIsInstance<ConnectionEvent.QualityChanged>()
        assertThat(qualityEvents).hasSize(1)
        assertThat(qualityEvents[0].quality).isEqualTo(ConnectionQuality.GOOD)
    }
}
