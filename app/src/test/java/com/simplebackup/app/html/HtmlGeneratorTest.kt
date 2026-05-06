package com.simplebackup.app.html

import com.google.common.truth.Truth.assertThat
import com.simplebackup.app.core.Backup
import com.simplebackup.app.core.ContactInfo
import com.simplebackup.app.core.Event
import org.junit.jupiter.api.Test

class HtmlGeneratorTest {
    private val backup = Backup(
        version = 1,
        devicePhone = "+15551234567",
        generatedAt = 1714000000000L,
        contacts = mapOf("+15559876543" to ContactInfo("Alice", 1700000000000L, 1714000000000L)),
        events = listOf(
            Event.Sms(addr = "+15559876543", date = 1700000000000L, type = 1, body = "hi", read = true),
            Event.Call(addr = "+15559876543", date = 1714000000000L, type = 2, durationSec = 120)
        )
    )

    @Test fun `output is self-contained HTML`() {
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(backup)
        assertThat(html).startsWith("<!DOCTYPE html>")
        assertThat(html).contains("</html>")
    }

    @Test fun `output embeds gzip-base64 payload`() {
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(backup)
        assertThat(html).contains("PAYLOAD_START")
        assertThat(html).doesNotContain("{{PAYLOAD}}")
    }

    @Test fun `output contains correct stats`() {
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(backup)
        assertThat(html).contains("Messages: 1")
        assertThat(html).contains("Calls: 1")
        assertThat(html).contains("Contacts: 1")
    }

    @Test fun `period reflects first and last event date`() {
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(backup)
        assertThat(html).contains("Period: ")
    }

    @Test fun `empty events render dash for period`() {
        val empty = backup.copy(events = emptyList(), contacts = emptyMap())
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(empty)
        assertThat(html).contains("Period: —")
    }

    @Test fun `title includes device phone`() {
        val html = HtmlGenerator(templateLoader = { TEST_TEMPLATE }).generate(backup)
        assertThat(html).contains("+15551234567")
    }

    companion object {
        const val TEST_TEMPLATE = """<!DOCTYPE html><html><body>
<div>{{TITLE}}</div>
<div>Messages: {{STATS_MESSAGES}} | Calls: {{STATS_CALLS}} | Contacts: {{STATS_CONTACTS}} | Period: {{STATS_PERIOD}}</div>
<script>const data="PAYLOAD_START{{PAYLOAD}}PAYLOAD_END";</script>
</body></html>"""
    }
}
