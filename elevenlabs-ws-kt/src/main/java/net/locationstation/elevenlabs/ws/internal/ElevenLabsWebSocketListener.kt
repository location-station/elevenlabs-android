package net.locationstation.elevenlabs.ws.internal

import net.locationstation.elevenlabs.ws.ConversationListener
import net.locationstation.elevenlabs.ws.logging.Logger
import net.locationstation.elevenlabs.ws.model.PingMessage
import net.locationstation.elevenlabs.ws.model.PongMessage
import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Internal WebSocketListener that bridges OkHttp events to the client's listener and flow.
 * @param json The configured Kotlinx Serialization JSON parser.
 * @param listener The user-provided listener for callback-based event handling.
 * @param messageFlow The shared flow for reactive event handling.
 * @param scope The CoroutineScope to launch message emissions on.
 */
internal class ElevenLabsWebSocketListener(
    private val json: Json,
    private val listener: ConversationListener,
    private val messageFlow: MutableSharedFlow<WebSocketMessage>,
    private val scope: CoroutineScope,
    private val webSocketProvider: () -> WebSocket?
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Logger.d("WebSocket opened.")
        listener.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Logger.v("Received message: %s", text)
        // Since this is called on a network thread, we can do the parsing here.
        try {
            val message = json.decodeFromString<WebSocketMessage>(text)

            // Handle ping messages automatically
            if (message is PingMessage) {
                scope.launch {
                    handlePing(message)
                }
            } else {
                // Forward to the listener
                listener.onMessage(message)
                // Emit to the flow
                scope.launch {
                    messageFlow.emit(message)

                }
            }
        } catch (e: Exception) {
            Logger.e(e, "Failed to parse incoming JSON message.")
            listener.onFailure(e, null)
        }
    }

    private fun handlePing(pingMessage: PingMessage) {
        Logger.d("Handling Ping with eventId: ${pingMessage.event.eventId}")
        val pong = PongMessage(eventId = pingMessage.event.eventId)
        try {
            val jsonString = json.encodeToString(PongMessage.serializer(), pong)
            Logger.v("Sending pong: %s", jsonString)
            webSocketProvider()?.send(jsonString)
        } catch (e: Exception) {
            Logger.e(e, "Failed to serialize and send pong.")
        }
    }


    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Logger.d("WebSocket closing: %d - %s", code, reason)
        listener.onClosing(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Logger.d("WebSocket closed: %d - %s", code, reason)
        listener.onClosed(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Logger.e(t, "WebSocket failure.")
        listener.onFailure(t, response)
    }
}