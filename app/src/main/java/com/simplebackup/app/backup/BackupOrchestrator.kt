package com.simplebackup.app.backup

import com.simplebackup.app.core.Backup
import com.simplebackup.app.core.Event
import com.simplebackup.app.core.merge
import com.simplebackup.app.html.HtmlGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import java.io.File

class Readers(
    val sms: (Set<String>) -> Sequence<Event.Sms>,
    val mms: (Set<String>) -> Sequence<Event.Mms>,
    val calls: (Set<String>) -> Sequence<Event.Call>
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

        _progress.value = BackupProgress.Running(BackupProgress.Phase.SMS, 0, null)
        yield()
        val smsList = readers.sms(addresses).toList()

        _progress.value = BackupProgress.Running(BackupProgress.Phase.MMS, 0, null)
        yield()
        val mmsList = readers.mms(addresses).toList()

        _progress.value = BackupProgress.Running(BackupProgress.Phase.CALLS, 0, null)
        yield()
        val callList = readers.calls(addresses).toList()

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
            // Fallback: copy then delete
            target.writeText(content)
            tmp.delete()
        }
    }
}
