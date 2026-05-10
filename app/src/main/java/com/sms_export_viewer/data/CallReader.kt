package com.sms_export_viewer.data

import android.content.ContentResolver
import android.provider.CallLog
import com.sms_export_viewer.core.Event
import com.sms_export_viewer.core.normalizeToE164

class CallReader(private val resolver: ContentResolver) {
    fun read(
        addresses: Set<String>,
        onProgress: ProgressUpdater = NoopProgress
    ): Sequence<Event.Call> = sequence {
        val cols = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )
        resolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} ASC")?.use { c ->
            val total = c.count
            val ai = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val di = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val ti = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val du = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            var idx = 0
            while (c.moveToNext()) {
                if (idx % 50 == 0) onProgress(idx, total)
                idx++
                val raw = c.getString(ai) ?: continue
                val e164 = normalizeToE164(raw)
                if (addresses.isNotEmpty() && e164 !in addresses) continue
                yield(
                    Event.Call(
                        addr = e164,
                        date = c.getLong(di),
                        type = c.getInt(ti),
                        durationSec = c.getInt(du)
                    )
                )
            }
            onProgress(total, total)
        } ?: onProgress(0, 0)
    }
}
