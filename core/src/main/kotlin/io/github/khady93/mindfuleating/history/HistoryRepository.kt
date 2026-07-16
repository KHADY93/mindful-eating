package io.github.khady93.mindfuleating.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.khady93.mindfuleating.settings.dataStore
import kotlinx.coroutines.flow.map

data class SessionRecord(
    val startTimeMillis: Long,
    val biteCount: Int,
    val durationSeconds: Long,
    val accentColorArgb: Int = LEGACY_COLOR_ARGB,
)

private val SESSION_HISTORY_KEY = stringPreferencesKey("session_history")
private val SESSION_TOMBSTONES_KEY = stringPreferencesKey("session_tombstones")
private const val MAX_HISTORY_SIZE = 500
private const val MAX_TOMBSTONES = 1000
private const val FIELD_DELIMITER = "|"
private const val RECORD_DELIMITER = "\n"

// Neutral grey shown for entries recorded before per-session color was tracked.
private const val LEGACY_COLOR_ARGB = 0xFF9E9E9E.toInt()

class HistoryRepository(private val context: Context) {

    val sessionsFlow = context.dataStore.data.map { prefs -> decodeRecords(prefs[SESSION_HISTORY_KEY]) }

    /** Deleted-session keys, synced alongside records so a delete on one device isn't
     * resurrected by a merge from another device that still has the record. */
    val tombstonesFlow = context.dataStore.data.map { prefs -> decodeTombstones(prefs[SESSION_TOMBSTONES_KEY]) }

    suspend fun addSession(record: SessionRecord) {
        context.dataStore.edit { prefs ->
            val existing = decodeRecords(prefs[SESSION_HISTORY_KEY])
            val updated = (listOf(record) + existing).take(MAX_HISTORY_SIZE)
            prefs[SESSION_HISTORY_KEY] = encodeRecords(updated)
        }
    }

    /** Deletes the session that started at [startTimeMillis] — unique enough in practice to key on. */
    suspend fun deleteSession(startTimeMillis: Long) {
        context.dataStore.edit { prefs ->
            val remaining = decodeRecords(prefs[SESSION_HISTORY_KEY])
                .filterNot { it.startTimeMillis == startTimeMillis }
            prefs[SESSION_HISTORY_KEY] = encodeRecords(remaining)
            val tombstones = (decodeTombstones(prefs[SESSION_TOMBSTONES_KEY]) + startTimeMillis)
                .toList()
                .takeLast(MAX_TOMBSTONES)
                .toSet()
            prefs[SESSION_TOMBSTONES_KEY] = encodeTombstones(tombstones)
        }
    }

    /**
     * Merges a remote (synced) snapshot into local storage: records are unioned by their unique
     * [SessionRecord.startTimeMillis] key, tombstones are unioned too, and any record whose key
     * is in the merged tombstone set is dropped — so a delete on one device isn't resurrected by
     * a concurrent add from the other device's sync.
     */
    suspend fun mergeRemote(remoteRecords: List<SessionRecord>, remoteTombstones: Set<Long>) {
        context.dataStore.edit { prefs ->
            val localRecords = decodeRecords(prefs[SESSION_HISTORY_KEY])
            val localTombstones = decodeTombstones(prefs[SESSION_TOMBSTONES_KEY])

            val mergedTombstonesFull = localTombstones + remoteTombstones
            val mergedTombstones = if (mergedTombstonesFull.size > MAX_TOMBSTONES) {
                mergedTombstonesFull.toList().takeLast(MAX_TOMBSTONES).toSet()
            } else {
                mergedTombstonesFull
            }
            val mergedRecords = (localRecords + remoteRecords)
                .distinctBy { it.startTimeMillis }
                .filterNot { it.startTimeMillis in mergedTombstones }
                .sortedByDescending { it.startTimeMillis }
                .take(MAX_HISTORY_SIZE)

            prefs[SESSION_HISTORY_KEY] = encodeRecords(mergedRecords)
            prefs[SESSION_TOMBSTONES_KEY] = encodeTombstones(mergedTombstones)
        }
    }

    private fun decodeRecords(raw: String?): List<SessionRecord> {
        return (raw ?: "").split(RECORD_DELIMITER).filter { it.isNotBlank() }.mapNotNull(::parseRecord)
    }

    private fun encodeRecords(records: List<SessionRecord>): String {
        return records.joinToString(RECORD_DELIMITER, transform = ::encodeRecord)
    }

    private fun decodeTombstones(raw: String?): Set<Long> {
        return (raw ?: "").split(RECORD_DELIMITER).filter { it.isNotBlank() }
            .mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun encodeTombstones(tombstones: Set<Long>): String {
        return tombstones.joinToString(RECORD_DELIMITER)
    }

    private fun encodeRecord(record: SessionRecord): String {
        return "${record.startTimeMillis}$FIELD_DELIMITER${record.biteCount}$FIELD_DELIMITER" +
            "${record.durationSeconds}$FIELD_DELIMITER${record.accentColorArgb}"
    }

    private fun parseRecord(line: String): SessionRecord? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != 3 && parts.size != 4) return null
        val startTimeMillis = parts[0].toLongOrNull() ?: return null
        val biteCount = parts[1].toIntOrNull() ?: return null
        val durationSeconds = parts[2].toLongOrNull() ?: return null
        val accentColorArgb = parts.getOrNull(3)?.toIntOrNull() ?: LEGACY_COLOR_ARGB
        return SessionRecord(startTimeMillis, biteCount, durationSeconds, accentColorArgb)
    }
}
