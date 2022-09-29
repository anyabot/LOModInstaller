package com.altter.lomodinstaller

import android.net.Uri

class DocumentFileMeta {
    var parentUri: Uri? = null
    var uri: Uri? = null
    var name: String? = null
    var size: Long = 0
    var timestamp: Long = 0
    var mime: String? = null
    var isDirectory: Boolean = false
    var canWrite: Boolean = false
}