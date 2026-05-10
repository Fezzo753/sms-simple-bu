package com.sms_export_viewer.data

import android.content.ContentResolver
import android.provider.Telephony.Sms
import com.sms_export_viewer.core.Event
import com.sms_export_viewer.core.normalizeToE164

class SmsReader(private val resolver: ContentResolver) {
    fun read(
        addresses: Set<String>,
        onProgress: ProgressUpdater = NoopProgress
    ): Sequence<Event.Sms> = sequence {
        val cols = arrayOf(Sms.ADDRESS, Sms.DATE, Sms.TYPE, Sms.BODY, Sms.READ)
        resolver.query(Sms.CONTENT_URI, cols, null, null, "${Sms.DATE} ASC")?.use { c ->
            val total = c.count
            val ai = c.getColumnIndexOrThrow(Sms.ADDRESS)
            val di = c.getColumnIndexOrThrow(Sms.DATE)
            val ti = c.getColumnIndexOrThrow(Sms.TYPE)
            val bi = c.getColumnIndexOrThrow(Sms.BODY)
            val ri = c.getColumnIndexOrThrow(Sms.READ)
            var idx = 0
            while (c.moveToNext()) {
                if (idx % 50 == 0) onProgress(idx, total)
                idx++
                val addr = c.getString(ai) ?: continue
                val e164 = normalizeToE164(addr)
                if (addresses.isNotEmpty() && e164 !in addresses) continue
                yield(
                    Event.Sms(
                        addr = e164,
                        date = c.getLong(di),
                        type = c.getInt(ti),
                        body = c.getString(bi) ?: "",
                        read = c.getInt(ri) == 1
                    )
                )
            }
            onProgress(total, total)
        } ?: onProgress(0, 0)
    }
}
