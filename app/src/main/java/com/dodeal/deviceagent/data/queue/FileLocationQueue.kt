package com.dodeal.deviceagent.data.queue

import android.content.Context
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.LocationPoint
import java.io.File

/**
 * Encrypted, file-backed queue. Each point is one file named "<createdAtMillis>_<eventId>.bin"
 * holding the AES/GCM-encrypted payload. The eventId in the name gives O(1) dedup; the timestamp
 * prefix gives cheap chronological ordering for batch upload. Swap for Room/SQLCipher later behind
 * LocationQueue without touching callers.
 */
class FileLocationQueue(
    context: Context,
    private val cipher: PayloadCipher = PayloadCipher(),
) : LocationQueue {

    private val dir: File = File(context.applicationContext.filesDir, "location_queue").apply { mkdirs() }

    @Synchronized
    override fun enqueue(point: LocationPoint) {
        if (files().any { it.eventId == point.eventId }) return // dedup
        val file = File(dir, "${System.currentTimeMillis()}_${point.eventId}.bin")
        runCatching {
            file.writeBytes(cipher.encrypt(QueuedPointCodec.encode(point)))
        }.onFailure {
            AgentLog.e(AgentLog.Category.OFFLINE_QUEUE, "enqueue failed", it)
        }
    }

    @Synchronized
    override fun peekBatch(limit: Int): List<LocationPoint> =
        files().sortedBy { it.createdAt }.take(limit).mapNotNull { entry ->
            runCatching { QueuedPointCodec.decode(cipher.decrypt(entry.file.readBytes())) }.getOrNull()
        }

    @Synchronized
    override fun remove(eventIds: List<String>) {
        val set = eventIds.toHashSet()
        files().filter { it.eventId in set }.forEach { it.file.delete() }
    }

    @Synchronized
    override fun size(): Int = files().size

    private data class Entry(val file: File, val createdAt: Long, val eventId: String)

    private fun files(): List<Entry> =
        (dir.listFiles()?.toList() ?: emptyList()).mapNotNull { f ->
            val name = f.name.removeSuffix(".bin")
            val sep = name.indexOf('_')
            if (sep <= 0) return@mapNotNull null
            val ts = name.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
            Entry(f, ts, name.substring(sep + 1))
        }
}
