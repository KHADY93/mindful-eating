package io.github.khady93.mindfuleating.sync

import android.content.Context
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.khady93.mindfuleating.history.HistoryRepository
import io.github.khady93.mindfuleating.history.SessionRecord
import kotlinx.coroutines.runBlocking

/**
 * Syncs session history between the watch and phone apps. Unlike settings, this can't be a
 * simple last-write-wins overwrite: both devices can independently add or delete sessions, so
 * [HistoryRepository.mergeRemote] unions records by their unique key and applies a synced
 * tombstone set rather than blindly replacing one side's list with the other's.
 */
object HistorySync {
    const val PATH = "/history"
    private const val KEY_RECORDS = "records"
    private const val KEY_TOMBSTONES = "tombstones"
    private const val FIELD_DELIMITER = "|"
    private const val ITEM_DELIMITER = "\n"

    // Marks the payload we just applied FROM a remote sync, so the merge it causes locally
    // isn't mistaken for new local data and re-pushed right back out (echo prevention).
    @Volatile private var lastAppliedPayload: String? = null

    fun pushIfChanged(context: Context, records: List<SessionRecord>, tombstones: Set<Long>) {
        val payload = encodePayload(records, tombstones)
        if (payload == lastAppliedPayload) return
        lastAppliedPayload = null

        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString(KEY_RECORDS, encodeRecords(records))
            dataMap.putString(KEY_TOMBSTONES, encodeTombstones(tombstones))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    fun onRemoteDataChanged(context: Context, item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val remoteRecords = decodeRecords(dataMap.getString(KEY_RECORDS))
        val remoteTombstones = decodeTombstones(dataMap.getString(KEY_TOMBSTONES))
        lastAppliedPayload = encodePayload(remoteRecords, remoteTombstones)
        val historyRepository = HistoryRepository(context)
        runBlocking { historyRepository.mergeRemote(remoteRecords, remoteTombstones) }
    }

    private fun encodePayload(records: List<SessionRecord>, tombstones: Set<Long>): String {
        return "${encodeRecords(records)}::${encodeTombstones(tombstones)}"
    }

    private fun encodeRecords(records: List<SessionRecord>): String {
        return records.joinToString(ITEM_DELIMITER) { record ->
            "${record.startTimeMillis}$FIELD_DELIMITER${record.biteCount}$FIELD_DELIMITER" +
                "${record.durationSeconds}$FIELD_DELIMITER${record.accentColorArgb}"
        }
    }

    private fun decodeRecords(raw: String?): List<SessionRecord> {
        return (raw ?: "").split(ITEM_DELIMITER).filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split(FIELD_DELIMITER)
            if (parts.size != 4) return@mapNotNull null
            val startTimeMillis = parts[0].toLongOrNull() ?: return@mapNotNull null
            val biteCount = parts[1].toIntOrNull() ?: return@mapNotNull null
            val durationSeconds = parts[2].toLongOrNull() ?: return@mapNotNull null
            val accentColorArgb = parts[3].toIntOrNull() ?: return@mapNotNull null
            SessionRecord(startTimeMillis, biteCount, durationSeconds, accentColorArgb)
        }
    }

    private fun encodeTombstones(tombstones: Set<Long>): String {
        return tombstones.joinToString(ITEM_DELIMITER)
    }

    private fun decodeTombstones(raw: String?): Set<Long> {
        return (raw ?: "").split(ITEM_DELIMITER).filter { it.isNotBlank() }
            .mapNotNull { it.toLongOrNull() }.toSet()
    }
}
