package com.sms_export_viewer.data

import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import com.sms_export_viewer.core.normalizeToE164

data class DiscoveredContact(val e164: String, val displayName: String?)

class ContactDiscovery(private val resolver: ContentResolver) {

    fun discoverActiveContacts(): List<DiscoveredContact> {
        val nameByE164 = buildNameMap()
        val addresses = HashSet<String>()
        addresses += distinctAddrs(Telephony.Sms.CONTENT_URI, Telephony.Sms.ADDRESS)
        addresses += distinctAddrs(CallLog.Calls.CONTENT_URI, CallLog.Calls.NUMBER)
        return addresses.map { DiscoveredContact(it, nameByE164[it]) }
            .sortedBy { it.displayName ?: it.e164 }
    }

    fun loadAllContacts(): List<DiscoveredContact> = buildNameMap()
        .map { (e164, name) -> DiscoveredContact(e164, name) }
        .sortedBy { it.displayName ?: it.e164 }

    fun nameMap(): Map<String, String?> = buildNameMap()

    private fun buildNameMap(): Map<String, String> {
        val out = HashMap<String, String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out.putIfAbsent(normalizeToE164(raw), name)
            }
        }
        return out
    }

    private fun distinctAddrs(uri: android.net.Uri, col: String): Set<String> {
        val out = HashSet<String>()
        resolver.query(uri, arrayOf(col), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += normalizeToE164(it) }
        }
        return out
    }
}
