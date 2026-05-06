package com.simplebackup.app.data

import android.content.ContentResolver
import android.provider.Telephony.Sms
import com.simplebackup.app.core.Event
import com.simplebackup.app.core.normalizeToE164

class SmsReader(private val resolver: ContentResolver) {
    fun read(addresses: Set<String>): Sequence<Event.Sms> = sequence {
        val cols = arrayOf(Sms.ADDRESS, Sms.DATE, Sms.TYPE, Sms.BODY, Sms.READ)
        resolver.query(Sms.CONTENT_URI, cols, null, null, "${Sms.DATE} ASC")?.use { c ->
            val ai = c.getColumnIndexOrThrow(Sms.ADDRESS)
            val di = c.getColumnIndexOrThrow(Sms.DATE)
            val ti = c.getColumnIndexOrThrow(Sms.TYPE)
            val bi = c.getColumnIndexOrThrow(Sms.BODY)
            val ri = c.getColumnIndexOrThrow(Sms.READ)
            while (c.moveToNext()) {
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
        }
    }
}
