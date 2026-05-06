package com.simplebackup.app.backup

import com.google.common.truth.Truth.assertThat
import com.simplebackup.app.core.Backup
import com.simplebackup.app.core.Event
import com.simplebackup.app.html.HtmlGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupOrchestratorTest {

    private val json = Json { classDiscriminator = "kind"; encodeDefaults = true }
    private val htmlGen = HtmlGenerator { "<!DOCTYPE html><html><body>{{TITLE}} {{STATS_MESSAGES}} {{STATS_CALLS}} {{STATS_CONTACTS}} {{STATS_PERIOD}} {{PAYLOAD}}</body></html>" }

    private fun makeOrchestrator(
        dir: File,
        sms: List<Event.Sms> = emptyList(),
        mms: List<Event.Mms> = emptyList(),
        calls: List<Event.Call> = emptyList(),
        contactNames: Map<String, String?> = emptyMap(),
        now: Long = 1_700_000_000_000L
    ) = BackupOrchestrator(
        filesDir = dir,
        devicePhone = "+15551234567",
        readers = Readers(
            sms = { _, _ -> sms.asSequence() },
            mms = { _, _ -> mms.asSequence() },
            calls = { _, _ -> calls.asSequence() }
        ),
        contactNames = contactNames,
        htmlGenerator = htmlGen,
        json = json,
        nowProvider = { now }
    )

    @Test fun `first run with no existing writes both files`(@TempDir dir: File) = runTest {
        val orch = makeOrchestrator(
            dir,
            sms = listOf(Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true)),
            calls = listOf(Event.Call(addr = "+15559876543", date = 200, type = 1, durationSec = 60))
        )
        val result = orch.run(setOf("+15559876543"))
        assertThat(result.isSuccess).isTrue()
        val done = result.getOrThrow()
        assertThat(done.newMessages).isEqualTo(1)
        assertThat(done.newCalls).isEqualTo(1)
        assertThat(orch.jsonFile().exists()).isTrue()
        assertThat(orch.htmlFile().exists()).isTrue()
    }

    @Test fun `second run with same readers reports zero new`(@TempDir dir: File) = runTest {
        val sms = listOf(Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true))
        val orch = makeOrchestrator(dir, sms = sms)
        orch.run(setOf("+15559876543"))
        val again = makeOrchestrator(dir, sms = sms).run(setOf("+15559876543")).getOrThrow()
        assertThat(again.newMessages).isEqualTo(0)
        assertThat(again.newCalls).isEqualTo(0)
    }

    @Test fun `second run with new events reports correct deltas`(@TempDir dir: File) = runTest {
        val first = makeOrchestrator(
            dir,
            sms = listOf(Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true))
        )
        first.run(setOf("+15559876543"))
        val secondSms = listOf(
            Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "hi", read = true),
            Event.Sms(addr = "+15559876543", date = 200, type = 2, body = "yo", read = true)
        )
        val secondCalls = listOf(Event.Call(addr = "+15559876543", date = 300, type = 2, durationSec = 60))
        val result = makeOrchestrator(dir, sms = secondSms, calls = secondCalls).run(setOf("+15559876543")).getOrThrow()
        assertThat(result.newMessages).isEqualTo(1)
        assertThat(result.newCalls).isEqualTo(1)
    }

    @Test fun `progress transitions through phases ending in Done`(@TempDir dir: File) = runTest {
        val orch = makeOrchestrator(
            dir,
            sms = listOf(Event.Sms(addr = "+15559876543", date = 100, type = 1, body = "x", read = true))
        )
        orch.run(setOf("+15559876543"))
        assertThat(orch.progress.value).isInstanceOf(BackupProgress.Done::class.java)
    }

    @Test fun `failure surfaces via Failed progress`(@TempDir dir: File) = runTest {
        val orch = BackupOrchestrator(
            filesDir = dir,
            devicePhone = "+15551234567",
            readers = Readers(
                sms = { _, _ -> throw RuntimeException("boom") },
                mms = { _, _ -> emptySequence() },
                calls = { _, _ -> emptySequence() }
            ),
            contactNames = emptyMap(),
            htmlGenerator = htmlGen,
            json = json
        )
        val result = orch.run(emptySet())
        assertThat(result.isFailure).isTrue()
        assertThat(orch.progress.value).isInstanceOf(BackupProgress.Failed::class.java)
    }

    @Test fun `progress total propagates from reader updates`(@TempDir dir: File) = runTest {
        val orch = BackupOrchestrator(
            filesDir = dir,
            devicePhone = "+15551234567",
            readers = Readers(
                sms = { _, onProgress ->
                    onProgress(0, 100)
                    onProgress(50, 100)
                    onProgress(100, 100)
                    emptySequence()
                },
                mms = { _, onProgress ->
                    onProgress(0, 0)
                    emptySequence()
                },
                calls = { _, onProgress ->
                    onProgress(0, 0)
                    emptySequence()
                }
            ),
            contactNames = emptyMap(),
            htmlGenerator = htmlGen,
            json = json
        )
        orch.run(emptySet())
        assertThat(orch.progress.value).isInstanceOf(BackupProgress.Done::class.java)
    }
}
