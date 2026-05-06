package com.simplebackup.app.data

import android.content.ContentResolver
import android.provider.CallLog
import com.simplebackup.app.core.Event
import com.simplebackup.app.core.normalizeToE164

class CallReader(private val resolver: ContentResolver) {
    fun read(addresses: Set<String>): Sequence<Event.Call> = sequence {
        val cols = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )
        resolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} ASC")?.use { c ->
            val ai = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val di = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val ti = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val du = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
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
        }
    }
}
