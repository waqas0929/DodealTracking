package com.dodeal.deviceagent.domain.repository

import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.data.queue.LocationQueue
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.location.AgentHealthState
import com.dodeal.deviceagent.domain.model.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the location upload path: every point is durably queued first, then we try to flush the queue
 * as a batch. Because the queue dedups by eventId and the batch endpoint is idempotent on the same
 * ids, a point is never lost and never uploaded twice — whether we're online, offline, or retrying.
 *
 * Uploads go through DeviceApi only (-> LocationPayloadMapper -> Dodeal wire model). This layer has
 * no idea about endpoints or JSON.
 */
interface LocationRepository {
    suspend fun record(point: LocationPoint)
    suspend fun flush(): FlushOutcome
    fun pendingCount(): Int
}

sealed interface FlushOutcome {
    data class Uploaded(val count: Int) : FlushOutcome
    data object NothingToUpload : FlushOutcome
    data class Deferred(val reason: String, val remaining: Int) : FlushOutcome
}

class LocationRepositoryImpl(
    private val deviceApi: DeviceApi,
    private val queue: LocationQueue,
) : LocationRepository {

    private val flushLock = Mutex()

    override suspend fun record(point: LocationPoint) {
        queue.enqueue(point)
        // Safety cap so a long offline period can't grow the queue without bound (spec §12).
        val overflow = queue.size() - MAX_QUEUE
        if (overflow > 0) {
            queue.remove(queue.peekBatch(overflow).map { it.eventId })
        }
        flush()
    }

    override fun pendingCount(): Int = queue.size()

    override suspend fun flush(): FlushOutcome = withContext(Dispatchers.IO) {
        flushLock.withLock {
            var uploaded = 0
            while (true) {
                // Buffered offline fixes upload as a batch (POST /api/device/locations, cap 500).
                // The encrypted queue still gives offline durability + retry; on success the whole
                // chunk is removed by eventId.
                val batch = queue.peekBatch(UPLOAD_CHUNK)
                if (batch.isEmpty()) break

                when (deviceApi.uploadLocationBatch(batch)) {
                    is ApiResult.Success -> {
                        queue.remove(batch.map { it.eventId })
                        AgentHealthState.onUpload()
                        uploaded += batch.size
                    }
                    is ApiResult.Failure -> {
                        val reason = "upload_failed"
                        AgentLog.w(AgentLog.Category.OFFLINE_QUEUE, "flush deferred ($reason); ${queue.size()} pending")
                        return@withLock if (uploaded > 0) FlushOutcome.Uploaded(uploaded)
                        else FlushOutcome.Deferred(reason, queue.size())
                    }
                }
            }
            when {
                uploaded > 0 -> {
                    AgentLog.i(AgentLog.Category.OFFLINE_QUEUE, "flushed $uploaded point(s)")
                    FlushOutcome.Uploaded(uploaded)
                }
                else -> FlushOutcome.NothingToUpload
            }
        }
    }

    private companion object {
        const val UPLOAD_CHUNK = 500
        const val MAX_QUEUE = 20_000
    }
}
