package com.dodeal.deviceagent.data.queue

import com.dodeal.deviceagent.domain.model.LocationPoint

/**
 * Durable, encrypted buffer for location points. Dedup is by LocationPoint.eventId so re-enqueuing
 * the same point (retry) is a no-op and batch uploads never duplicate. The file-backed impl can be
 * swapped for Room/SQLCipher behind this interface without touching the engine or uploader.
 */
interface LocationQueue {
    fun enqueue(point: LocationPoint)
    fun peekBatch(limit: Int): List<LocationPoint>
    fun remove(eventIds: List<String>)
    fun size(): Int
}
