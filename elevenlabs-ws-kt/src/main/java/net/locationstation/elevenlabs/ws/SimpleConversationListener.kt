package net.locationstation.elevenlabs.ws

import net.locationstation.elevenlabs.ws.model.WebSocketMessage
import okhttp3.Response
import okhttp3.WebSocket

/**
 * Simplified listener interface for the V2 client.
 * This adapts between the original ConversationListener and the internal needs.
 */
interface SimpleConversationListener {
    fun onConnectionOpened()
    fun onMessageReceived(message: WebSocketMessage)
    fun onError(error: Exception)
    fun onConnectionClosed()
}

/**
 * Adapter to convert between ConversationListener and SimpleConversationListener.
 */
class ConversationListenerAdapter(
    private val simple: SimpleConversationListener
) : ConversationListener {
    
    override fun onOpen(webSocket: WebSocket, response: Response) {
        simple.onConnectionOpened()
    }
    
    override fun onMessage(message: WebSocketMessage) {
        simple.onMessageReceived(message)
    }
    
    override fun onClosing(code: Int, reason: String) {
        // Not used in simple interface
    }
    
    override fun onClosed(code: Int, reason: String) {
        simple.onConnectionClosed()
    }
    
    override fun onFailure(t: Throwable, response: Response?) {
        simple.onError(Exception(t))
    }
}
