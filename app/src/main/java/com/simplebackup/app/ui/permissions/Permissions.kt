package com.simplebackup.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS
)

fun grantedPermissions(context: Context): Set<String> =
    REQUIRED_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }.toSet()

fun missingPermissions(context: Context): Set<String> =
    REQUIRED_PERMISSIONS.toSet() - grantedPermissions(context)

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun rationaleFor(permission: String): String = when (permission) {
    Manifest.permission.READ_SMS ->
        "Read SMS — required to back up text messages and MMS bodies."
    Manifest.permission.READ_CALL_LOG ->
        "Read call log — required to back up call entries."
    Manifest.permission.READ_CONTACTS ->
        "Read contacts — used so backups show names instead of bare numbers."
    else -> permission
}
