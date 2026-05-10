package com.sms_export_viewer.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PhoneNumberTest {
    @Test fun `parses parenthesized US number`() {
        assertThat(normalizeToE164("(555) 987-6543", "US")).isEqualTo("+15559876543")
    }

    @Test fun `parses already-E164 number`() {
        assertThat(normalizeToE164("+15559876543", "US")).isEqualTo("+15559876543")
    }

    @Test fun `parses 10-digit US number`() {
        assertThat(normalizeToE164("5559876543", "US")).isEqualTo("+15559876543")
    }

    @Test fun `parses spaces and dashes`() {
        assertThat(normalizeToE164("555-987-6543", "US")).isEqualTo("+15559876543")
    }

    @Test fun `returns original for unparseable strings`() {
        assertThat(normalizeToE164("AMAZON", "US")).isEqualTo("AMAZON")
        assertThat(normalizeToE164("12345", "US")).isEqualTo("12345")
    }

    @Test fun `respects non-US default country`() {
        assertThat(normalizeToE164("020 7946 0958", "GB")).isEqualTo("+442079460958")
    }

    @Test fun `blank input returned as-is`() {
        assertThat(normalizeToE164("", "US")).isEqualTo("")
    }
}
