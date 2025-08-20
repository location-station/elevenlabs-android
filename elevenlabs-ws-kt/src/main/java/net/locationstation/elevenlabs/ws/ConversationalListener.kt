package net.locationstation.elevenlabs.ws

import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import okhttp3.Response
import okhttp3.WebSocket

/**
 * Listener for handling events from the ElevenLabs conversational WebSocket.
 */
interface ConversationListener {
    /**
     * Called when the WebSocket connection has been successfully established.
     * @param webSocket The WebSocket instance.
     * @param response The server's response to the upgrade request.
     */
    fun onOpen(webSocket: WebSocket, response: Response)

    /**
     * Called when a structured message is received from the server.
     * @param message A sealed class instance representing the message.
     */
    fun onMessage(message: WebSocketMessage)

    /**
     * Called when the WebSocket is closing.
     * @param code The status code.
     * @param reason The reason for closing.
     */
    fun onClosing(code: Int, reason: String)

    /**
     * Called when the WebSocket has been closed.
     * @param code The status code.
     * @param reason The reason for closing.
     */
    fun onClosed(code: Int, reason: String)

    /**
     * Called when a network or protocol error occurs.
     * @param t The error that occurred.
     * @param response The server's response, if any.
     */
    fun onFailure(t: Throwable, response: Response?)
}