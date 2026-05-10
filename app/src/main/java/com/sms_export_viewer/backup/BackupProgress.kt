package com.sms_export_viewer.backup

sealed class BackupProgress {
    data object Idle : BackupProgress()
    data class Running(val phase: Phase, val current: Int, val total: Int?) : BackupProgress()
    data class Done(val newMessages: Int, val newCalls: Int) : BackupProgress()
    data class Failed(val message: String) : BackupProgress()

    enum class Phase(val label: String) {
        SMS("Reading messages"),
        MMS("Reading multimedia messages"),
        CALLS("Reading calls"),
        MERGE("Merging"),
        HTML("Generating viewer"),
        SAVE("Saving")
    }
}
