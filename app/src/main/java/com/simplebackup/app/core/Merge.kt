package com.simplebackup.app.core

private fun keyOf(e: Event): String {
    val addr = normalizeToE164(e.addr)
    val payload = when (e) {
        is Event.Sms -> e.body
        is Event.Mms -> e.body
        is Event.Call -> e.durationSec.toString()
    }
    return "${e.kindString()}|$addr|${e.date}|${e.type}|$payload"
}

private fun Event.kindString() = when (this) {
    is Event.Sms -> "sms"
    is Event.Mms -> "mms"
    is Event.Call -> "call"
}

fun merge(
    existing: Backup?,
    incoming: List<Event>,
    devicePhone: String,
    contactNames: Map<String, String?>,
    nowMs: Long = System.currentTimeMillis()
): Backup {
    val seen = HashSet<String>()
    val merged = ArrayList<Event>((existing?.events?.size ?: 0) + incoming.size)
    existing?.events?.forEach { e -> if (seen.add(keyOf(e))) merged.add(e) }
    incoming.forEach { e -> if (seen.add(keyOf(e))) merged.add(e) }
    merged.sortBy { it.date }

    val byAddr = merged.groupBy { normalizeToE164(it.addr) }
    val contacts = byAddr.mapValues { (addr, events) ->
        ContactInfo(
            name = contactNames[addr] ?: existing?.contacts?.get(addr)?.name,
            firstSeen = events.minOf { it.date },
            lastSeen = events.maxOf { it.date }
        )
    }

    return Backup(
        version = 1,
        devicePhone = devicePhone,
        generatedAt = nowMs,
        contacts = contacts,
        events = merged
    )
}
