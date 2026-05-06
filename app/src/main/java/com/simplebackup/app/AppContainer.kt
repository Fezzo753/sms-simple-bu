package com.simplebackup.app

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import com.simplebackup.app.backup.BackupOrchestrator
import com.simplebackup.app.backup.Readers
import com.simplebackup.app.core.normalizeToE164
import com.simplebackup.app.data.CallReader
import com.simplebackup.app.data.ContactDiscovery
import com.simplebackup.app.data.MmsReader
import com.simplebackup.app.data.SettingsRepository
import com.simplebackup.app.data.SmsReader
import com.simplebackup.app.html.HtmlGenerator
import kotlinx.serialization.json.Json
import java.io.File

class AppContainer(private val app: Application) {
    val settings = SettingsRepository(app)
    val resolver = app.contentResolver
    val discovery = ContactDiscovery(resolver)
    val filesDir: File = app.filesDir
    val htmlGenerator = HtmlGenerator {
        app.assets.open("viewer_template.html").bufferedReader().use { it.readText() }
    }
    val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Suppress("MissingPermission", "HardwareIds", "DEPRECATION")
    fun resolveDevicePhone(override: String?): String {
        val raw = if (!override.isNullOrBlank()) {
            normalizeToE164(override)
        } else {
            try {
                val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val sim = tm?.line1Number
                if (!sim.isNullOrBlank()) normalizeToE164(sim) else "+0000000000"
            } catch (e: SecurityException) {
                "+0000000000"
            }
        }
        return sanitizeForFileName(raw)
    }

    /**
     * Strip filesystem-unsafe characters so the resolved phone is always a valid file-name
     * suffix. `+`, digits, dashes, and underscores survive; everything else (spaces, slashes,
     * colons, control chars) becomes `_`. Returns `+0000000000` if the result is blank.
     */
    private fun sanitizeForFileName(s: String): String {
        val cleaned = s.replace(Regex("[^A-Za-z0-9+_\\-]"), "_").trim('_')
        return if (cleaned.isBlank()) "+0000000000" else cleaned
    }

    fun orchestrator(devicePhone: String, contactNames: Map<String, String?>) = BackupOrchestrator(
        filesDir = filesDir,
        devicePhone = devicePhone,
        readers = Readers(
            sms = { addrs, onProgress -> SmsReader(resolver).read(addrs, onProgress) },
            mms = { addrs, onProgress -> MmsReader(resolver).read(addrs, onProgress) },
            calls = { addrs, onProgress -> CallReader(resolver).read(addrs, onProgress) }
        ),
        contactNames = contactNames,
        htmlGenerator = htmlGenerator,
        json = json
    )
}
