package net.locationstation.elevenlabs.ws

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.locationstation.elevenlabs.ws.model.AgentResponseCorrection
import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import org.junit.Test
import org.junit.Assert.*

/**
 * Test serialization and deserialization of the AgentResponseCorrection event.
 */
class AgentResponseCorrectionTest {
    
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        explicitNulls = false
    }
    
    @Test
    fun `test deserialization of agent response correction event`() {
        // Given: A JSON string representing an agent response correction event
        val jsonString = """
            {
                "agent_response_correction_event": {
                    "original_agent_response": "Hi David! I'm your personal AI guide for Location Station — a one-of-a-kind, hyper-personalized news experience tailored just for you.\nThink of me like your friendly co-host, here to learn a bit about you so we can serve you news that feels like it's made just for you — because it will be.\n\nI'll ask you a few casual questions — nothing too nosy, just enough to help us create something truly yours.\nReady when you are! Just say \"Let's go\" or ask me anything.",
                    "corrected_agent_response": "Hi David! I'm your personal AI guide for Location Station — a one-of-a-kind, hyper-personalized news experience tailored just for you.Think of me like your friendly co-host, here to learn a bit about you so we can serve you news that..."
                },
                "type": "agent_response_correction"
            }
        """.trimIndent()
        
        // When: We deserialize the JSON
        val message = json.decodeFromString<WebSocketMessage>(jsonString)
        
        // Then: The message should be correctly deserialized as AgentResponseCorrection
        assertTrue(message is AgentResponseCorrection)
        
        val correction = message as AgentResponseCorrection
        assertTrue(correction.event.originalAgentResponse.contains("Hi David!"))
        assertTrue(correction.event.correctedAgentResponse.contains("Hi David!"))
        assertTrue(correction.event.originalAgentResponse.contains("nothing too nosy"))
        assertTrue(correction.event.correctedAgentResponse.endsWith("..."))
    }
    
    @Test
    fun `test serialization of agent response correction event`() {
        // Given: An AgentResponseCorrection object
        val correction = AgentResponseCorrection(
            event = net.locationstation.elevenlabs.ws.model.AgentResponseCorrectionEvent(
                originalAgentResponse = "Original text here",
                correctedAgentResponse = "Corrected text here"
            )
        )
        
        // When: We serialize it to JSON
        val jsonString = json.encodeToString(WebSocketMessage.serializer(), correction)
        
        // Then: The JSON should contain the expected structure
        assertTrue(jsonString.contains("\"type\":\"agent_response_correction\""))
        assertTrue(jsonString.contains("\"agent_response_correction_event\""))
        assertTrue(jsonString.contains("\"original_agent_response\":\"Original text here\""))
        assertTrue(jsonString.contains("\"corrected_agent_response\":\"Corrected text here\""))
    }
    
    @Test
    fun `test round-trip serialization`() {
        // Given: An AgentResponseCorrection object
        val original = AgentResponseCorrection(
            event = net.locationstation.elevenlabs.ws.model.AgentResponseCorrectionEvent(
                originalAgentResponse = "Test original response",
                correctedAgentResponse = "Test corrected response"
            )
        )
        
        // When: We serialize and then deserialize
        val jsonString = json.encodeToString(WebSocketMessage.serializer(), original)
        val deserialized = json.decodeFromString<WebSocketMessage>(jsonString)
        
        // Then: The deserialized object should match the original
        assertTrue(deserialized is AgentResponseCorrection)
        val deserializedCorrection = deserialized as AgentResponseCorrection
        assertEquals(original.event.originalAgentResponse, deserializedCorrection.event.originalAgentResponse)
        assertEquals(original.event.correctedAgentResponse, deserializedCorrection.event.correctedAgentResponse)
    }
}
