package cz.piskvorky

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jednoduchý kanál mezi dvěma telefony přes veřejnou službu ntfy.sh:
 * kdo zná název místnosti ([topic]), může do ní posílat i z ní číst zprávy.
 */
class NtfyChannel(private val topic: String) {
    @Volatile
    private var stream: HttpURLConnection? = null

    /** Odešle zprávu; při potížích se sítí to zkusí třikrát a pak vyhodí [IOException]. */
    suspend fun publish(text: String): Unit = withContext(Dispatchers.IO) {
        var failure: IOException? = null
        repeat(PUBLISH_ATTEMPTS) { attempt ->
            try {
                val connection = URL("$BASE_URL/$topic").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    connection.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code in 200..299) return@withContext
                    failure = IOException("HTTP $code")
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                failure = e
            }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        throw failure ?: IOException("Zprávu se nepodařilo odeslat")
    }

    /**
     * Poslouchá zprávy v místnosti, dokud není korutina zrušena. Při výpadku
     * se připojí znovu a naváže za poslední přijatou zprávou.
     * [onOpen] se zavolá jednou, až je první spojení navázané.
     */
    suspend fun listen(onOpen: suspend () -> Unit, onMessage: suspend (String) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            var lastId: String? = null
            var opened = false
            while (isActive) {
                try {
                    val since = lastId?.let { "?since=$it" } ?: ""
                    val connection = URL("$BASE_URL/$topic/json$since").openConnection() as HttpURLConnection
                    stream = connection
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = STREAM_READ_TIMEOUT_MS
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            val event = JSONObject(line)
                            when (event.optString("event")) {
                                "open" -> if (!opened) {
                                    opened = true
                                    onOpen()
                                }
                                "message" -> {
                                    lastId = event.optString("id").ifEmpty { lastId }
                                    onMessage(event.optString("message"))
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Výpadek sítě nebo poškozený řádek – po chvíli se připojíme znovu.
                } finally {
                    stream?.disconnect()
                    stream = null
                }
                delay(RECONNECT_DELAY_MS)
            }
        }

    /** Ukončí čekající čtení, aby se poslech po zrušení korutiny hned zastavil. */
    fun close() {
        stream?.disconnect()
    }

    private companion object {
        const val BASE_URL = "https://ntfy.sh"
        const val PUBLISH_ATTEMPTS = 3
        const val TIMEOUT_MS = 15_000
        const val STREAM_READ_TIMEOUT_MS = 120_000
        const val RETRY_DELAY_MS = 1_000L
        const val RECONNECT_DELAY_MS = 1_500L
    }
}
