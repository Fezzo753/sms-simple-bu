package com.sms_export_viewer

import android.app.Application

class SimpleBackupApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
