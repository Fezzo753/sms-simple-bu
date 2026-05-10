package com.sms_export_viewer.backup

import com.sms_export_viewer.core.Backup
import com.sms_export_viewer.core.Event
import com.sms_export_viewer.core.merge
import com.sms_export_viewer.data.ProgressUpdater
import com.sms_export_viewer.html.HtmlGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import java.io.File

class Readers(
    val sms: (Set<String>, ProgressUpdater) -> Sequence<Event.Sms>,
    val mms: (Set<String>, ProgressUpdater) -> Sequence<Event.Mms>,
    val calls: (Set<String>, ProgressUpdater) -> Sequence<Event.Call>
)

class BackupOrchestrator(
    private val filesDir: File,
    private val devicePhone: String,
    private val readers: Readers,
    private val contactNames: Map<String, String?>,
    private val htmlGenerator: HtmlGenerator,
    private val json: Json,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _progress = MutableStateFlow<BackupProgress>(BackupProgress.Idle)
    val progress: StateFlow<BackupProgress> = _progress.asStateFlow()

    fun jsonFile(): File = File(filesDir, "Backup_${devicePhone}.json")
    fun htmlFile(): File = File(filesDir, "Backup_${devicePhone}.html")

    suspend fun run(addresses: Set<String>): Result<BackupProgress.Done> = runCatching {
        val existing = loadExisting()
        val existingMessageCount = existing?.events?.count { it is Event.Sms || it is Event.Mms } ?: 0
        val existingCallCount = existing?.events?.count { it is Event.Call } ?: 0

        val smsList = runPhase(BackupProgress.Phase.SMS) { onProgress ->
            readers.sms(addresses, onProgress).toList()
        }
        val mmsList = runPhase(BackupProgress.Phase.MMS) { onProgress ->
            readers.mms(addresses, onProgress).toList()
        }
        val callList = runPhase(BackupProgress.Phase.CALLS) { onProgress ->
            readers.calls(addresses, onProgress).toList()
        }

        _progress.value = BackupProgress.Running(BackupProgress.Phase.MERGE, 0, null)
        yield()
        val incoming = smsList + mmsList + callList
        val merged = merge(existing, incoming, devicePhone, contactNames, nowProvider())

        _progress.value = BackupProgress.Running(BackupProgress.Phase.HTML, 0, null)
        yield()
        val html = htmlGenerator.generate(merged)

        _progress.value = BackupProgress.Running(BackupProgress.Phase.SAVE, 0, null)
        yield()
        writeAtomic(jsonFile(), json.encodeToString(Backup.serializer(), merged))
        writeAtomic(htmlFile(), html)

        val newMessageCount = merged.events.count { it is Event.Sms || it is Event.Mms } - existingMessageCount
        val newCallCount = merged.events.count { it is Event.Call } - existingCallCount
        BackupProgress.Done(newMessageCount, newCallCount).also { _progress.value = it }
    }.onFailure { e ->
        _progress.value = BackupProgress.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
    }

    private suspend inline fun <T> runPhase(
        phase: BackupProgress.Phase,
        block: (ProgressUpdater) -> T
    ): T {
        _progress.value = BackupProgress.Running(phase, 0, null)
        yield()
        val updater: ProgressUpdater = { current, total ->
            _progress.value = BackupProgress.Running(phase, current, total.takeIf { it > 0 })
        }
        return block(updater)
    }

    private fun loadExisting(): Backup? {
        val f = jsonFile()
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Backup.serializer(), f.readText()) }.getOrNull()
    }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }
}
