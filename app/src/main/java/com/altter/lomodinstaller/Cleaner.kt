package com.altter.lomodinstaller

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Switch
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.io.FileUtils
import java.io.File

@RequiresApi(Build.VERSION_CODES.N)
fun findMatchedDoc(path: String, doc: DocumentFile): DocumentFile? {
    val doc1 = doc.findFile(path) ?: return null
    val doc2 = doc1.findFile("files") ?: return null
    val doc3 = doc2.findFile("UnityCache") ?: return null
    val doc4 = doc3.findFile("Shared")
    if (doc4 != null) return doc4
    return null
}

fun sizeReadable(size: Long): String {
    var dSize: Float = size.toFloat()
    var step = 0
    while (dSize >= 1000) {
        dSize /= 1024
        step++
    }

    val s = dSize.toString()
    return when {
        step == 1 -> s + "KBs"
        step == 2 -> s + "MBs"
        step == 3 -> s + "GBs"
        step == 4 -> s + "PBs"
        else -> size.toString() + "bytes"
    }
}

fun xcopy(context: Context, from: DocumentFile, to: DocumentFile): Boolean {
    val content = xread(context, from) ?: return false
    val uri = to.uri
//    if (to.exists()) xdelete(to)
    return xwrite(context, uri, content)
}

fun xwrite(context: Context, uri: Uri, content: ByteArray): Boolean {
    val file = DocumentFile.fromSingleUri(context, uri) ?: return false
    if (!file.canWrite()) return false

    val resolver = context.contentResolver
    val stream = resolver.openOutputStream(file.uri, "wt") ?: return false
    stream.write(content)
    stream.flush()
    stream.close()

    return true
}

fun xread(context: Context, file: DocumentFile): ByteArray? {

    val resolver = context.contentResolver
    val stream = resolver.openInputStream(file.uri) ?: return null
    val ret = stream.readBytes()
    stream.close()

    return ret
}

fun xdelete(file: DocumentFile): Boolean {
    return file.delete()
}

fun Patcher(
    context: Context,
    storages: List<String>,
    mod: String,
    switches: HashMap<Switch, String>,
    Log: (String) -> Unit
) {
    fun findMatchedStorageFileList(path: String): Array<File>? {
        for (storage in storages) {
            val target = when {
                storage == "emulated" -> "emulated/0"
                else -> storage
            }
            val f = File("/storage/$target/$path")
            if (!f.exists()) continue

            return f.listFiles()
        }
        return null
    }

    fun findMatchedStorageName(path: String, name: String): File? {
        for (storage in storages) {
            val target = when {
                storage == "emulated" -> "emulated/0"
                else -> storage
            }
            val f = File("/storage/$target/$path/$name")
            if (!f.exists()) continue

            return f
        }
        return null
    }
    Log("Start")
    for ((switch, path) in switches) {
        if (!switch.isChecked) continue // 스위치 체크 안된거 넘어가기

        val fullPath = "$path/files/UnityCache/Shared/"
        val dirs = findMatchedStorageFileList(fullPath)
        if (dirs == null) {
            Log(context.getString(R.string.NO_DATA_DOC))
            continue
        }

        val modDirs = findMatchedStorageFileList(mod)
        if (modDirs == null) {
            Log(context.getString(R.string.NO_MOD_DOC))
            continue
        }

        for (target in modDirs) { // 대상 디렉터리들
            if (!target.isDirectory) continue
            val newTarget = findMatchedStorageName(fullPath, target.name) ?: continue
            if (!newTarget.isDirectory) continue
            val gibberish = newTarget.listFiles()
            if (gibberish.size != 1) {
                Log(String.format(context.getString(R.string.MORE_THAN_ONE), newTarget.name))
                continue
            }
            Log(String.format(context.getString(R.string.COPY_TRY), target.name))
            var check = false
            for (data in target.listFiles()) {
                if (data.isDirectory) {
                    for (data2 in data.listFiles()) {
                        if (data2.name == "__data") {
                            check = true
                            FileUtils.copyFileToDirectory(data2, gibberish[0])
                            Log(String.format(context.getString(R.string.COPY_DONE), target.name))
                        }
                    }
                }
                else if (data.name == "__data") {
                    check = true
                    FileUtils.copyFileToDirectory(data, gibberish[0])
                    Log(String.format(context.getString(R.string.COPY_DONE), target.name))
                }
            }
            if (!check) Log(String.format(context.getString(R.string.SRC_NO_DATA), target.name))
        }

    }
    Log("Done")
}

@RequiresApi(Build.VERSION_CODES.N)
fun PatcherSAF(
    context: Context,
    dataDoc: DocumentFile?,
    modDoc: DocumentFile?,
    switches: HashMap<Switch, String>,
    Log: (String) -> Unit
) {
    if (modDoc == null) {
        Log(context.getString(R.string.NO_MOD_DOC))
        return
    }
    if (dataDoc == null) {
        Log(context.getString(R.string.NO_DATA_DOC))
        return
    }
    Log("Start")
    for ((switch, path) in switches) {
        if (!switch.isChecked) continue // 스위치 체크 안된거 넘어가기
        val shared = findMatchedDoc(path, dataDoc) ?: continue
        val uri = shared.uri
        val uripath = uri.path ?: continue
        for (target in modDoc.listFiles()) {
            if (target.name == null) continue
            var temp_uri: Uri? = null
            val temp_id = uripath.substringAfterLast("/document/") + "/" + target.name

            temp_uri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                temp_id
            )
            if (temp_uri == null) continue
            val newtarget = DocumentFile.fromTreeUri(context, temp_uri)
            if (newtarget == null) {
                Log(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            if (!newtarget.exists()){
                Log(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            var check = false
            for (f in target.listFiles()) {
                if (f == null) continue
                if (f.isDirectory) {
                    for (f2 in f.listFiles()) {
                        if (f2.name != "__data") continue
                        Log(String.format(context.getString(R.string.COPY_TRY), target.name))
                        check = true
                        try {
                            val targetList = newtarget.listFiles()
                            if (targetList.size != 1) {
                                Log(String.format(context.getString(R.string.MORE_THAN_ONE), newtarget.name))
                                continue
                            }
                            val gibberish = targetList[0]
                            var report = false
                            for (dat in gibberish.listFiles()) {
                                if (dat.name == "__data") {
                                    report = true
                                    if (xcopy(context, f2, dat)) Log(String.format(context.getString(R.string.COPY_DONE), target.name))
                                }
                            }
                            if (!report) Log(String.format(context.getString(R.string.DEST_NO_DATA), target.name))
                        }
                        catch(e: Exception) {
                            Log(e.toString())
                        }
                    }
                }
                if (f.name != "__data") continue
                Log(String.format(context.getString(R.string.COPY_TRY), target.name))
                check = true
                try {
                    val targetList = newtarget.listFiles()
                    if (targetList.size != 1) {
                        Log(String.format(context.getString(R.string.MORE_THAN_ONE), newtarget.name))
                        continue
                    }
                    val gibberish = targetList[0]
                    var report = false
                    for (dat in gibberish.listFiles()) {
                        if (dat.name == "__data") {
                            report = true
                            if (xcopy(context, f, dat)) Log(String.format(context.getString(R.string.COPY_DONE), target.name))
                        }
                    }
                    if (!report) Log(String.format(context.getString(R.string.DEST_NO_DATA), target.name))
                }
                catch(e: Exception) {
                    Log(e.toString())
                }
            }
            if (!check) Log(String.format(context.getString(R.string.SRC_NO_DATA), target.name))
        }

    }
    Log("Done")
}
