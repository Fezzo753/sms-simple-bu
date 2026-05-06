package com.simplebackup.app.html

import com.simplebackup.app.core.Backup
import com.simplebackup.app.core.Event
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HtmlGenerator(private val templateLoader: () -> String) {
    private val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
    }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun generate(backup: Backup): String {
        val payloadJson = json.encodeToString(Backup.serializer(), backup)
        val payload = Compression.gzipBase64(payloadJson)
        val sms = backup.events.count { it is Event.Sms || it is Event.Mms }
        val calls = backup.events.count { it is Event.Call }
        val period = if (backup.events.isEmpty()) "—"
        else "${dateFmt.format(Date(backup.events.first().date))} – ${dateFmt.format(Date(backup.events.last().date))}"
        return templateLoader()
            .replace("{{TITLE}}", "SimpleBackup — ${backup.devicePhone}")
            .replace("{{STATS_MESSAGES}}", sms.toString())
            .replace("{{STATS_CALLS}}", calls.toString())
            .replace("{{STATS_CONTACTS}}", backup.contacts.size.toString())
            .replace("{{STATS_PERIOD}}", period)
            .replace("{{PAYLOAD}}", payload)
    }
}
