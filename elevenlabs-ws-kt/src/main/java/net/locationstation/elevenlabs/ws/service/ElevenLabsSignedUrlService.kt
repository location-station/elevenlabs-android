package net.locationstation.elevenlabs.ws.service

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Private data class for serializing the request payload
@Serializable
private data class SignedUrlRequest(val agent_id: String)

// Private data class for deserializing the response payload
@Serializable
private data class SignedUrlResponse(val signed_url: String)

/**
 * A service to retrieve a signed websocket URL from the ElevenLabs API.
 *
 * @property webhookUrl The full URL to the webhook endpoint that provides the signed URL.
 */
class ElevenLabsSignedUrlService(private val webhookUrl: String) {

    // Reusable instances of OkHttpClient and Json parser
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Asynchronously calls the webhook to get a signed URL for a given ElevenLabs agent.
     *
     * This function is a suspend function and must be called from a coroutine or another
     * suspend function. It handles the HTTP POST request, JSON serialization/deserialization,
     * and returns the signed URL.
     *
     * @param agentId The ID of the ElevenLabs agent (e.g., "agent_01jws672j2fvy9401y87e4csr6").
     * @return The signed websocket URL as a [String].
     * @throws IOException if the network request fails, the server returns an unsuccessful
     * HTTP status code, or the response body is invalid.
     */
    suspend fun getSignedUrl(agentId: String): String {
        // 1. Create the request payload and serialize it to JSON
        val requestPayload = SignedUrlRequest(agent_id = agentId)
        val jsonBody = json.encodeToString(SignedUrlRequest.serializer(), requestPayload)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        // 2. Build the HTTP request
        val request = Request.Builder()
            .url(webhookUrl)
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        // 3. Execute the request asynchronously and bridge OkHttp's callback to coroutines
        val response = client.newCall(request).await()

        // 4. Check for a successful response and parse the body
        if (!response.isSuccessful) {
            throw IOException("Unexpected HTTP code ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Received an empty response body.")

        // 5. Deserialize the JSON response and return the URL
        val signedUrlResponse = json.decodeFromString(SignedUrlResponse.serializer(), responseBody)
        return signedUrlResponse.signed_url
    }

    /**
     * An extension function to bridge OkHttp's Call.enqueue with Kotlin's suspend functions.
     * This allows for cleaner, non-blocking asynchronous code.
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Don't resume if the coroutine was already cancelled
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })

        // When the coroutine is cancelled, cancel the OkHttp call
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore exceptions on cancellation
            }
        }
    }
}