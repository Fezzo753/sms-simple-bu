package com.simplebackup.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ModelTest {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = Backup(
            version = 1,
            devicePhone = "+15551234567",
            generatedAt = 1715000000000L,
            contacts = mapOf(
                "+15559876543" to ContactInfo(name = "Alice", firstSeen = 1690000000000L, lastSeen = 1714000000000L)
            ),
            events = listOf(
                Event.Sms(addr = "+15559876543", date = 1714000000000L, type = 1, body = "hi", read = true),
                Event.Mms(addr = "+15559876543", date = 1714000001000L, type = 2, body = "yo", read = true, parts = listOf("text/plain")),
                Event.Call(addr = "+15559876543", date = 1714000002000L, type = 1, durationSec = 263)
            )
        )
        val text = json.encodeToString(Backup.serializer(), original)
        val decoded = json.decodeFromString(Backup.serializer(), text)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `kind discriminator is present in serialized output`() {
        val backup = Backup(
            version = 1, devicePhone = "+1", generatedAt = 0, contacts = emptyMap(),
            events = listOf(Event.Sms(addr = "+1", date = 0, type = 1, body = "x", read = true))
        )
        val text = json.encodeToString(Backup.serializer(), backup)
        assertThat(text).contains("\"kind\":\"sms\"")
    }

    @Test
    fun `snake_case field names match design schema`() {
        val backup = Backup(
            version = 1, devicePhone = "+15551234567", generatedAt = 1715000000000L,
            contacts = mapOf(
                "+15559876543" to ContactInfo(name = "Alice", firstSeen = 1690000000000L, lastSeen = 1714000000000L)
            ),
            events = listOf(Event.Call(addr = "+15559876543", date = 1, type = 1, durationSec = 60))
        )
        val text = json.encodeToString(Backup.serializer(), backup)
        assertThat(text).contains("\"device_phone\":")
        assertThat(text).contains("\"generated_at\":")
        assertThat(text).contains("\"first_seen\":")
        assertThat(text).contains("\"last_seen\":")
        assertThat(text).contains("\"duration_sec\":")
    }
}
