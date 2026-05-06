package com.simplebackup.app

import android.app.Application

class SimpleBackupApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
