package dev.stan.yotsuba.core.network

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/**
 * Counts response bytes per board as they stream through and hands the totals to [sink] in
 * batches, so a catalog scroll is one row per board and not one per thumbnail.
 *
 * A network interceptor on purpose: it sees wire bytes (gzip still compressed) and never a
 * cache hit. Content-Length is not enough at that level, 4chan's JSON comes chunked, so the
 * body's source is wrapped and every read adds to the board's running total. The totals go
 * to [sink] when a read takes them past [flushBytes] or past [flushAfterMs] since the last
 * flush, and on process stop (the application registers this as a lifecycle observer).
 * There is no timer: bytes left idle wait for the next read or the next background.
 */
class DataUsageMeter(
    private val sink: (board: String?, bytes: Long) -> Unit,
    private val flushBytes: Long = 1L shl 20,
    private val flushAfterMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : Interceptor, DefaultLifecycleObserver {
    private val pending = HashMap<String?, Long>()
    private var pendingBytes = 0L
    private var lastFlushAt = clock()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val board = boardOf(chain.request().url)
        val body = response.body
        val counted = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n > 0) add(board, n)
                return n
            }
        }
        return response.newBuilder().body(counted.buffer().asResponseBody(body.contentType(), body.contentLength())).build()
    }

    private fun add(board: String?, bytes: Long) {
        val due = synchronized(pending) {
            pending[board] = (pending[board] ?: 0L) + bytes
            pendingBytes += bytes
            pendingBytes >= flushBytes || clock() - lastFlushAt >= flushAfterMs
        }
        if (due) flush()
    }

    /** Hands everything pending to the sink now. */
    fun flush() {
        val batch = synchronized(pending) {
            val out = pending.toMap()
            pending.clear()
            pendingBytes = 0L
            lastFlushAt = clock()
            out
        }
        batch.forEach { (board, bytes) -> sink(board, bytes) }
    }

    override fun onStop(owner: LifecycleOwner) = flush()

    companion object {
        /**
         * The board a request is for, or null when the host names none. 4chan's API and
         * image hosts put it first in the path (`/g/catalog.json`, `/g/123s.jpg`; a bare
         * `/boards.json` is nobody's). Archives name it in the query (`?board=g`) or as the
         * first path segment that is a board some archive carries, which also covers their
         * separate media hosts.
         */
        fun boardOf(url: HttpUrl): String? {
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (url.host.endsWith(".4cdn.org")) return segments.firstOrNull()?.takeIf { segments.size > 1 }
            // ponytail: any other host gets the archive rule, so a foreign URL with a
            // path segment like /a/ lands on that board. Only archives use this client
            // today; list their media hosts here if that stops being true.
            return url.queryParameter("board") ?: segments.firstOrNull { ArchiveHosts.sourcesFor(it).isNotEmpty() }
        }
    }
}
