package com.simplebackup.app.data

typealias ProgressUpdater = (current: Int, total: Int) -> Unit

internal val NoopProgress: ProgressUpdater = { _, _ -> }
