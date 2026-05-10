package com.sms_export_viewer.data

typealias ProgressUpdater = (current: Int, total: Int) -> Unit

internal val NoopProgress: ProgressUpdater = { _, _ -> }
