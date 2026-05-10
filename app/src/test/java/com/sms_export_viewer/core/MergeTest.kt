package com.sms_export_viewer.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MergeTest {
    private fun sms(addr: String, date: Long, body: String = "x", type: Int = 1) =
        Event.Sms(addr = addr, date = date, type = type, body = body, read = true)

    @Test fun `merging into null existing returns all incoming sorted`() {
        val result = merge(
            existing = null,
            incoming = listOf(sms("+15559876543", 200), sms("+15559876543", 100)),
            devicePhone = "+15551234567",
            contactNames = mapOf("+15559876543" to "Bob")
        )
        assertThat(result.events.map { it.date }).containsExactly(100L, 200L).inOrder()
    }

    @Test fun `full overlap adds zero events`() {
        val a = sms("+15559876543", 100)
        val existing = Backup(devicePhone = "+15551234567", generatedAt = 0, contacts = emptyMap(), events = listOf(a))
        val result = merge(existing, listOf(a), "+15551234567", emptyMap())
        assertThat(result.events).hasSize(1)
    }

    @Test fun `partial overlap dedupes by key`() {
        val a = sms("+15559876543", 100)
        val b = sms("+15559876543", 200)
        val existing = Backup(devicePhone = "+15551234567", generatedAt = 0, contacts = emptyMap(), events = listOf(a))
        val result = merge(existing, listOf(a, b), "+15551234567", emptyMap())
        assertThat(result.events).containsExactly(a, b).inOrder()
    }

    @Test fun `phone format variants normalize to same dedupe key`() {
        val raw = sms("(555) 987-6543", 100)
        val existing = Backup(
            devicePhone = "+15551234567", generatedAt = 0, contacts = emptyMap(),
            events = listOf(sms("+15559876543", 100))
        )
        val result = merge(existing, listOf(raw), "+15551234567", emptyMap())
        assertThat(result.events).hasSize(1)
    }

    @Test fun `contacts map is rebuilt with first_seen and last_seen`() {
        val result = merge(
            existing = null,
            incoming = listOf(
                sms("+15559876543", 100),
                sms("+15559876543", 300),
                sms("+15559876543", 200)
            ),
            devicePhone = "+15551234567",
            contactNames = mapOf("+15559876543" to "Alice")
        )
        val info = result.contacts.getValue("+15559876543")
        assertThat(info.name).isEqualTo("Alice")
        assertThat(info.firstSeen).isEqualTo(100L)
        assertThat(info.lastSeen).isEqualTo(300L)
    }

    @Test fun `existing contact name preserved when no new contactNames provided`() {
        val existing = Backup(
            devicePhone = "+15551234567", generatedAt = 0,
            contacts = mapOf("+15559876543" to ContactInfo("Alice", 100, 100)),
            events = listOf(sms("+15559876543", 100))
        )
        val result = merge(existing, listOf(sms("+15559876543", 200)), "+15551234567", emptyMap())
        assertThat(result.contacts.getValue("+15559876543").name).isEqualTo("Alice")
    }

    @Test fun `differing event kinds with same addr+date+type+body do not collide`() {
        val s = Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true)
        val m = Event.Mms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true, parts = emptyList())
        val result = merge(null, listOf(s, m), "+15551234567", emptyMap())
        assertThat(result.events).hasSize(2)
    }

    @Test fun `events sorted by date ascending`() {
        val a = sms("+15559876543", 300)
        val b = sms("+15559876543", 100)
        val c = sms("+15559876543", 200)
        val result = merge(null, listOf(a, b, c), "+15551234567", emptyMap())
        assertThat(result.events.map { it.date }).containsExactly(100L, 200L, 300L).inOrder()
    }
}
