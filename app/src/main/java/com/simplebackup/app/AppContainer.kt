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
        if (!override.isNullOrBlank()) return normalizeToE164(override)
        return try {
            val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val raw = tm?.line1Number
            if (!raw.isNullOrBlank()) normalizeToE164(raw) else "+0000000000"
        } catch (e: SecurityException) {
            "+0000000000"
        }
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
