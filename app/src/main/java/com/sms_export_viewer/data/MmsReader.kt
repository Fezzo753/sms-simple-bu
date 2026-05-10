package com.sms_export_viewer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony.Mms
import com.sms_export_viewer.core.Event
import com.sms_export_viewer.core.normalizeToE164

class MmsReader(private val resolver: ContentResolver) {

    fun read(
        addresses: Set<String>,
        onProgress: ProgressUpdater = NoopProgress
    ): Sequence<Event.Mms> = sequence {
        val rows = ArrayList<MmsRow>()
        resolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms._ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.READ),
            null, null, "${Mms.DATE} ASC"
        )?.use { c ->
            val idi = c.getColumnIndexOrThrow(Mms._ID)
            val di = c.getColumnIndexOrThrow(Mms.DATE)
            val ti = c.getColumnIndexOrThrow(Mms.MESSAGE_BOX)
            val ri = c.getColumnIndexOrThrow(Mms.READ)
            while (c.moveToNext()) {
                rows += MmsRow(
                    id = c.getLong(idi),
                    dateSec = c.getLong(di),
                    type = c.getInt(ti),
                    read = c.getInt(ri) == 1
                )
            }
        }

        val total = rows.size
        if (total == 0) {
            onProgress(0, 0)
            return@sequence
        }

        val addressById = readAllAddresses(rows.map { it.id })

        for ((idx, row) in rows.withIndex()) {
            if (idx % 25 == 0) onProgress(idx, total)
            val raw = addressById[row.id] ?: continue
            val e164 = normalizeToE164(raw)
            if (addresses.isNotEmpty() && e164 !in addresses) continue
            val (body, parts) = readBodyAndParts(row.id)
            yield(
                Event.Mms(
                    addr = e164,
                    date = row.dateSec * 1000,
                    type = row.type,
                    body = body,
                    read = row.read,
                    parts = parts
                )
            )
        }
        onProgress(total, total)
    }

    private fun readAllAddresses(mmsIds: List<Long>): Map<Long, String> {
        if (mmsIds.isEmpty()) return emptyMap()
        val map = HashMap<Long, String>(mmsIds.size)

        mmsIds.chunked(450).forEach { chunk ->
            val batched = runCatching {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()
                resolver.query(
                    Uri.parse("content://mms/addr"),
                    arrayOf("msg_id", "address"),
                    "type=$FROM_TYPE AND msg_id IN ($placeholders)",
                    args, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val addr = c.getString(1) ?: continue
                        map.putIfAbsent(id, addr)
                    }
                }
                true
            }.getOrDefault(false)

            if (!batched) {
                chunk.forEach { id ->
                    if (id !in map) {
                        val a = readAddressOne(id)
                        if (a != null) map[id] = a
                    }
                }
            }
        }
        return map
    }

    private fun readAddressOne(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        resolver.query(
            uri,
            arrayOf("address"),
            "type=$FROM_TYPE",
            null, null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun readBodyAndParts(mmsId: Long): Pair<String, List<String>> {
        val sb = StringBuilder()
        val parts = mutableListOf<String>()
        resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf("ct", "text", "_data"),
            "mid=?",
            arrayOf(mmsId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(0) ?: continue
                parts += ct
                when (ct) {
                    "text/plain" -> sb.append(c.getString(1) ?: "")
                    in MEDIA_TYPES -> sb.append("[${ct.substringBefore("/")}]")
                }
            }
        }
        return sb.toString() to parts
    }

    private data class MmsRow(val id: Long, val dateSec: Long, val type: Int, val read: Boolean)

    private companion object {
        const val FROM_TYPE = 137
        val MEDIA_TYPES = setOf(
            "image/jpeg", "image/png", "image/gif",
            "video/mp4", "audio/amr", "audio/mp4"
        )
    }
}
