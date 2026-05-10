package com.sms_export_viewer.ui

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun shareBackup(activity: Activity, htmlFile: File) {
    if (!htmlFile.exists()) return
    val uri = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.fileprovider",
        htmlFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, "Share backup"))
}
