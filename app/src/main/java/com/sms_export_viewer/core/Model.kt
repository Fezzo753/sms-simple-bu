package com.sms_export_viewer.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val version: Int = 1,
    @SerialName("device_phone") val devicePhone: String,
    @SerialName("generated_at") val generatedAt: Long,
    val contacts: Map<String, ContactInfo>,
    val events: List<Event>
)

@Serializable
data class ContactInfo(
    val name: String? = null,
    @SerialName("first_seen") val firstSeen: Long,
    @SerialName("last_seen") val lastSeen: Long
)

@Serializable
sealed class Event {
    abstract val addr: String
    abstract val date: Long
    abstract val type: Int

    @Serializable
    @SerialName("sms")
    data class Sms(
        override val addr: String,
        override val date: Long,
        override val type: Int,
        val body: String,
        val read: Boolean
    ) : Event()

    @Serializable
    @SerialName("mms")
    data class Mms(
        override val addr: String,
        override val date: Long,
        override val type: Int,
        val body: String,
        val read: Boolean,
        val parts: List<String>
    ) : Event()

    @Serializable
    @SerialName("call")
    data class Call(
        override val addr: String,
        override val date: Long,
        override val type: Int,
        @SerialName("duration_sec") val durationSec: Int
    ) : Event()
}
