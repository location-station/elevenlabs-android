package net.locationstation.elevenlabs.ws.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A sealed interface representing all possible messages that can be sent or received
 * over the ElevenLabs Conversational AI WebSocket connection.
 *
 * This version is based on the full API structure provided via HTML.
 */
@Serializable
sealed interface WebSocketMessage

// =====================================================================================
// MARK: - Client to Server Messages (Publish)
// Messages that your application will send to the ElevenLabs server.
// =====================================================================================

/**
 * Initial message from the client to start the conversation, containing all configuration.
 * 
 * All fields are optional to accommodate different server configurations.
 * Some servers may not allow overriding certain fields like firstMessage.
 */
@Serializable
@SerialName("conversation_initiation_client_data")
data class ConversationInitiationClientData(
    @SerialName("conversation_config_override") val conversationConfigOverride: ConversationConfigOverride? = null,  // Made optional
    @SerialName("custom_llm_extra_body") val customLlmExtraBody: CustomLlmExtraBody? = null,
    @SerialName("dynamic_variables") val dynamicVariables: Map<String, String>? = null
) : WebSocketMessage

/**
 * Overrides for the conversation configuration.
 */
@Serializable
data class ConversationConfigOverride(
    val agent: AgentConfig? = null,  // Made optional
)

/**
 * Configuration for the agent.
 * 
 * All fields are optional. The server may have pre-configured values that
 * cannot be overridden. Omit fields that the server doesn't allow to be changed.
 */
@Serializable
data class AgentConfig(
    val prompt: PromptConfig? = null,  // Made optional
    @SerialName("first_message") val firstMessage: String? = null,  // Made optional with default null
    val language: String? = null  // Also made optional for flexibility
)

/**
 * The agent's system prompt.
 */
@Serializable
data class PromptConfig(
    val prompt: String
)

/**
 * Text-to-speech configuration.
 */
@Serializable
data class TtsConfig(
    @SerialName("voice_id") val voiceId: String
)

/**
 * Extra body parameters for custom LLM.
 */
@Serializable
data class CustomLlmExtraBody(
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int
)

/**
 * A chunk of user audio sent to the server. Note that this message is unique as it
 * does not have a "type" field in its JSON structure. It is handled by its top-level key.
 * Because of this, it does not inherit from WebSocketMessage.
 *
 * @property audioChunk Base64-encoded string of the raw audio data.
 */
@Serializable
data class UserAudioChunk(
    @SerialName("user_audio_chunk") val audioChunk: String
)

/**
 * The client's response to the server's `ping` message.
 *
 * @property eventId The ID from the corresponding ping event.
 */
@Serializable
@SerialName("pong")
data class PongMessage(
    @SerialName("event_id") val eventId: Long
) : WebSocketMessage

/**
 * The result of a tool called by the client.
 *
 * @property toolCallId The ID of the tool call this result corresponds to.
 * @property result The string representation of the tool's output.
 * @property isError Indicates if the tool execution resulted in an error.
 */
@Serializable
@SerialName("client_tool_result")
data class ClientToolResult(
    @SerialName("tool_call_id") val toolCallId: String,
    val result: String,
    @SerialName("is_error") val isError: Boolean = false
) : WebSocketMessage

/**
 * Informs the server about a change in the user's context (e.g., navigating to a new page).
 *
 * @property text A description of the contextual change.
 */
@Serializable
@SerialName("contextual_update")
data class ContextualUpdate(
    val text: String
) : WebSocketMessage

/**
 * Sends a text message from the user to the agent.
 *
 * @property text The user's message.
 */
@Serializable
@SerialName("user_message")
data class UserMessage(
    val text: String
) : WebSocketMessage

/**
 * Signals that the user has performed some activity.
 * This is a singleton object as it carries no data.
 */
@Serializable
@SerialName("user_activity")
object UserActivity : WebSocketMessage


// =====================================================================================
// MARK: - Server to Client Messages (Subscribe)
// Messages that your application will receive from the ElevenLabs server.
// =====================================================================================

/**
 * Provides metadata upon the initiation of a conversation.
 *
 * @property event The nested object containing the actual metadata.
 */
@Serializable
@SerialName("conversation_initiation_metadata")
data class ConversationInitiationMetadata(
    @SerialName("conversation_initiation_metadata_event") val event: MetadataEvent
) : WebSocketMessage

/**
 * The actual metadata payload for a ConversationInitiationMetadata message.
 *
 * @property conversationId A unique identifier for the conversation session.
 * @property agentOutputAudioFormat The audio format of the bot's output.
 * @property userInputAudioFormat The audio format expected for the user's input.
 */
@Serializable
data class MetadataEvent(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("agent_output_audio_format") val agentOutputAudioFormat: String,
    @SerialName("user_input_audio_format") val userInputAudioFormat: String
)

/**
 * Provides the Voice Activity Detection (VAD) score.
 *
 * @property event The nested object containing the VAD score.
 */
@Serializable
@SerialName("vad_score")
data class VadScore(
    @SerialName("vad_score_event") val event: VadScoreEvent
) : WebSocketMessage

/**
 * The actual payload for a VadScore message.
 *
 * @property vadScore The VAD score, a float between 0.0 and 1.0.
 */
@Serializable
data class VadScoreEvent(
    @SerialName("vad_score") val vadScore: Float
)

/**
 * Contains the transcription of the user's speech.
 *
 * @property event The nested object containing the transcription details.
 */
@Serializable
@SerialName("user_transcript")
data class UserTranscript(
    @SerialName("user_transcription_event") val event: UserTranscriptionEvent
) : WebSocketMessage

/**
 * The actual payload for a UserTranscript message.
 * @property userTranscript The transcribed text from the user.
 */
@Serializable
data class UserTranscriptionEvent(
    @SerialName("user_transcript") val userTranscript: String
)

/**
 * A tentative, non-final response from the agent.
 *
 * @property event The nested object containing the tentative response.
 */
@Serializable
@SerialName("internal_tentative_agent_response")
data class InternalTentativeAgentResponse(
    @SerialName("tentative_agent_response_internal_event") val event: TentativeAgentResponseInternalEvent
) : WebSocketMessage

/**
 * The actual payload for an InternalTentativeAgentResponse message.
 * @property tentativeAgentResponse The partial text of the agent's response.
 */
@Serializable
data class TentativeAgentResponseInternalEvent(
    @SerialName("tentative_agent_response") val tentativeAgentResponse: String
)

/**
 * A final response from the agent.
 *
 * @property event The nested object containing the agent response details.
 */
@Serializable
@SerialName("agent_response")
data class AgentResponse(
    @SerialName("agent_response_event") val event: AgentResponseEvent
) : WebSocketMessage

/**
 * The actual payload for an AgentResponse message.
 * @property agentResponse The final text of the agent's response.
 */
@Serializable
data class AgentResponseEvent(
    @SerialName("agent_response") val agentResponse: String
)

/**
 * A chunk of the agent's audio response.
 *
 * @property event The nested object containing the audio data.
 */
@Serializable
@SerialName("audio")
data class AudioMessage(
    @SerialName("audio_event") val event: AudioEvent
) : WebSocketMessage

/**
 * The actual payload for an AudioMessage.
 *
 * @property audioBase64 Base64-encoded string of the agent's audio response data.
 * @property eventId A unique identifier for this audio event.
 */
@Serializable
data class AudioEvent(
    @SerialName("audio_base_64") val audioBase64: String,
    @SerialName("event_id") val eventId: Long
)

/**
 * A keep-alive message from the server. The client should respond with a `pong`.
 *
 * @property event The nested object containing the ping details.
 */
@Serializable
@SerialName("ping")
data class PingMessage(
    @SerialName("ping_event") val event: PingEvent
) : WebSocketMessage

/**
 * The actual payload for a PingMessage.
 *
 * @property eventId A unique ID for this ping, to be echoed in the pong.
 * @property pingMs The latency in milliseconds.
 */
@Serializable
data class PingEvent(
    @SerialName("event_id") val eventId: Long,
    @SerialName("ping_ms") val pingMs: Int? = null
)

/**
 * Informs the client that it should execute a tool.
 *
 * @property call The nested object containing the tool call details.
 */
@Serializable
@SerialName("client_tool_call")
data class ClientToolCall(
    @SerialName("client_tool_call") val call: ToolCall
) : WebSocketMessage

/**
 * The actual payload for a ClientToolCall.
 *
 * @property toolName The name of the tool to execute.
 * @property toolCallId A unique ID for this specific tool invocation.
 * @property parameters A map of parameters for the tool.
 */
@Serializable
data class ToolCall(
    @SerialName("tool_name") val toolName: String,
    @SerialName("tool_call_id") val toolCallId: String,
    val parameters: Map<String, String>
)

/**
 * Indicates that an interruption has occurred in the conversation.
 * This event is sent when the agent's speech is interrupted, typically by the user starting to speak.
 *
 * @property event The nested object containing the interruption details.
 */
@Serializable
@SerialName("interruption")
data class InterruptionMessage(
    @SerialName("interruption_event") val event: InterruptionEvent
) : WebSocketMessage

/**
 * The actual payload for an InterruptionMessage.
 *
 * @property eventId A unique identifier for this interruption event.
 */
@Serializable
data class InterruptionEvent(
    @SerialName("event_id") val eventId: Long
)

/**
 * Indicates that an agent response has been corrected.
 * This event provides both the original and corrected versions of the agent's response.
 *
 * @property event The nested object containing the correction details.
 */
@Serializable
@SerialName("agent_response_correction")
data class AgentResponseCorrection(
    @SerialName("agent_response_correction_event") val event: AgentResponseCorrectionEvent
) : WebSocketMessage

/**
 * The actual payload for an AgentResponseCorrection message.
 *
 * @property originalAgentResponse The original text that was sent by the agent.
 * @property correctedAgentResponse The corrected version of the agent's response.
 */
@Serializable
data class AgentResponseCorrectionEvent(
    @SerialName("original_agent_response") val originalAgentResponse: String,
    @SerialName("corrected_agent_response") val correctedAgentResponse: String
)