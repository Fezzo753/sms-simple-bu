package com.simplebackup.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony.Mms
import com.simplebackup.app.core.Event
import com.simplebackup.app.core.normalizeToE164

class MmsReader(private val resolver: ContentResolver) {
    fun read(addresses: Set<String>): Sequence<Event.Mms> = sequence {
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
                val id = c.getLong(idi)
                val addr = readAddress(id) ?: continue
                val e164 = normalizeToE164(addr)
                if (addresses.isNotEmpty() && e164 !in addresses) continue
                val (body, parts) = readBodyAndParts(id)
                yield(
                    Event.Mms(
                        addr = e164,
                        date = c.getLong(di) * 1000,
                        type = c.getInt(ti),
                        body = body,
                        read = c.getInt(ri) == 1,
                        parts = parts
                    )
                )
            }
        }
    }

    private fun readAddress(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        resolver.query(uri, arrayOf("address", "type"), "type=137", null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
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

    private companion object {
        val MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/gif", "video/mp4", "audio/amr", "audio/mp4")
    }
}
