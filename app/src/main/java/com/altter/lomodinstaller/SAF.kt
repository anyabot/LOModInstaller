package com.altter.lomodinstaller

import android.net.Uri

val primaryTreeURi: Uri = Uri.parse(
    String.format("content://com.android.externalstorage.documents/tree/%s", Uri.encode("primary:"))
)

fun getSAFTreeUri(uri: Uri = primaryTreeURi): Uri? {
    var b = uri.toString()
    b = b.substring(b.indexOf("/tree/") + 6)
    b = b.substring(0, b.indexOf("%3A") + 3)
    return Uri.parse(
        String.format(
            "content://com.android.externalstorage.documents/tree/%s",
            b
        )
    ) // Uri.encode("primary:")))
}