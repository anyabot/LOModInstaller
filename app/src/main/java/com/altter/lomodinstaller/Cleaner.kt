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
import kotlin.math.log

fun findMatchedDoc(path: String, doc: DocumentFile): DocumentFile? {
    val doc1 = doc.findFile(path) ?: return null
    val doc2 = doc1.findFile("files") ?: return null
    val doc3 = doc2.findFile("UnityCache") ?: return null
    val doc4 = doc3.findFile("Shared")
    if (doc4 != null) return doc4
    return null
}

fun findMatchedDoc13(doc: DocumentFile): DocumentFile? {
    val doc1 = doc.findFile("files") ?: return null
    val doc2 = doc1.findFile("UnityCache") ?: return null
    val doc3 = doc2.findFile("Shared")
    if (doc3 != null) return doc3
    return null
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

fun runPatcher(
    context: Context,
    storages: List<String>,
    mod: String,
    switches: HashMap<Switch, String>,
    mode: Int,
    logFunction: (String) -> Unit
) {
    fun findMatchedStorageFileList(path: String): Array<File>? {
        if (path.contains("/storage/emulated/0")) {
            val f = File(path)
            if (f.exists()) return f.listFiles()
        }
        for (storage in storages) {
            val target = when (storage) {
                "emulated" -> "emulated/0"
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
            val target = when (storage) {
                "emulated" -> "emulated/0"
                else -> storage
            }
            val f = File("/storage/$target/$path/$name")
            if (!f.exists()) continue

            return f
        }
        return null
    }
    logFunction("Start")
    for ((switch, path) in switches) {
        if (!switch.isChecked) continue

        val fullPath = "$path/files/UnityCache/Shared/"
        val dirs = findMatchedStorageFileList(fullPath)
        if (dirs == null) {
            logFunction(context.getString(R.string.NO_DATA_DOC))
            continue
        }

        val modDirs = findMatchedStorageFileList(mod)
        if (modDirs == null) {
            logFunction(context.getString(R.string.NO_MOD_DOC))
            continue
        }

        for (target in modDirs) {
            if (!target.isDirectory) continue

            val newTarget = findMatchedStorageName(fullPath, target.name) ?: continue
            if (!newTarget.isDirectory) continue
            if (mode == 1) {
                logFunction(String.format(context.getString(R.string.COPY_TRY), target.name))
                val gibberish = newTarget.listFiles()
                if (gibberish == null) {
                    logFunction(String.format(context.getString(R.string.DEST_NO_DATA), newTarget.name))
                    continue
                } else if (gibberish.size != 1) {
                    logFunction(String.format(context.getString(R.string.MORE_THAN_ONE), newTarget.name))
                    continue
                } else {
                    var check = false
                    for (data in target.listFiles()!!) {
                        if (data.isDirectory) {
                            for (data2 in data.listFiles()!!) {
                                if (data2.name == "__data") {
                                    check = true
                                    if (gibberish[0].isDirectory) {
                                        FileUtils.copyFileToDirectory(data2, gibberish[0])
                                        logFunction(
                                            String.format(
                                                context.getString(R.string.COPY_DONE),
                                                target.name
                                            )
                                        )
                                    } else {
                                        logFunction(
                                            String.format(
                                                context.getString(R.string.NOT_DIRECTORY_2),
                                                gibberish[0].name,
                                                target.name
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (data.name == "__data") {
                            check = true
                            if (gibberish[0].isDirectory) {
                                FileUtils.copyFileToDirectory(data, gibberish[0])
                                logFunction(
                                    String.format(
                                        context.getString(R.string.COPY_DONE),
                                        target.name
                                    )
                                )
                            } else {
                                logFunction(
                                    String.format(
                                        context.getString(R.string.NOT_DIRECTORY_2),
                                        gibberish[0].name,
                                        target.name
                                    )
                                )
                            }
                        }
                    }
                    if (!check) logFunction(
                        String.format(
                            context.getString(R.string.SRC_NO_DATA),
                            target.name
                        )
                    )
                }
            } else if (mode == 2) {
                logFunction(String.format(context.getString(R.string.DELETE_START), newTarget.name))
                newTarget.deleteRecursively()
            }
        }

    }
    logFunction("Done")
}

fun runPatcherSAF(
    context: Context,
    dataDoc: DocumentFile?,
    modDoc: DocumentFile?,
    switches: HashMap<Switch, String>,
    mode: Int,
    logFunction: (String) -> Unit
) {
    if (modDoc == null) {
        logFunction(context.getString(R.string.NO_MOD_DOC))
        return
    }
    if (dataDoc == null) {
        logFunction(context.getString(R.string.NO_DATA_DOC))
        return
    }
    logFunction("Start")
    for ((switch, path) in switches) {
        if (!switch.isChecked) continue
        val shared = findMatchedDoc(path, dataDoc) ?: continue
        val uri = shared.uri
        val uriPath = uri.path ?: continue
        for (target in modDoc.listFiles()) {
            if (target.name == null) continue
            var tempUri: Uri?
            val tempId = uriPath.substringAfterLast("/document/") + "/" + target.name

            tempUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                tempId
            )
            if (tempUri == null) continue
            val newTarget = DocumentFile.fromTreeUri(context, tempUri)
            if (newTarget == null) {
                logFunction(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            if (!newTarget.exists()) {
                logFunction(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            if (!newTarget.isDirectory) {
                logFunction(String.format(context.getString(R.string.NOT_DIRECTORY), target.name))
                continue
            }
            if (mode == 1) {
                var check = false
                for (f in target.listFiles()) {
                    if (f == null) continue
                    if (f.isDirectory) {
                        for (f2 in f.listFiles()) {
                            if (f2.name != "__data") continue
                            logFunction(String.format(context.getString(R.string.COPY_TRY), target.name))
                            check = true
                            try {
                                val targetList = newTarget.listFiles()
                                if (targetList.size != 1) {
                                    logFunction(
                                        String.format(
                                            context.getString(R.string.MORE_THAN_ONE),
                                            newTarget.name
                                        )
                                    )
                                    continue
                                }
                                val gibberish = targetList[0]
                                var report = false
                                if (gibberish.isDirectory) {
                                    for (dat in gibberish.listFiles()) {
                                        if (dat.name == "__data") {
                                            report = true
                                            if (xcopy(
                                                    context,
                                                    f2,
                                                    dat
                                                )
                                            ) logFunction(
                                                String.format(
                                                    context.getString(R.string.COPY_DONE),
                                                    target.name
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    logFunction(
                                        String.format(
                                            context.getString(R.string.NOT_DIRECTORY_2),
                                            gibberish.name,
                                            target.name
                                        )
                                    )
                                    continue
                                }
                                if (!report) logFunction(
                                    String.format(
                                        context.getString(R.string.DEST_NO_DATA),
                                        target.name
                                    )
                                )
                            } catch (e: Exception) {
                                logFunction(e.toString())
                            }
                        }
                    }
                    if (f.name != "__data") continue
                    logFunction(String.format(context.getString(R.string.COPY_TRY), target.name))
                    check = true
                    try {
                        val targetList = newTarget.listFiles()
                        if (targetList.size != 1) {
                            logFunction(
                                String.format(
                                    context.getString(R.string.MORE_THAN_ONE),
                                    newTarget.name
                                )
                            )
                            continue
                        }
                        val gibberish = targetList[0]
                        var report = false
                        if (gibberish.isDirectory) {
                            for (dat in gibberish.listFiles()) {
                                if (dat.name == "__data") {
                                    report = true
                                    if (xcopy(
                                            context,
                                            f,
                                            dat
                                        )
                                    ) logFunction(
                                        String.format(
                                            context.getString(R.string.COPY_DONE),
                                            target.name
                                        )
                                    )
                                }
                            }
                        } else {
                            logFunction(
                                String.format(
                                    context.getString(R.string.NOT_DIRECTORY_2),
                                    gibberish.name,
                                    target.name
                                )
                            )
                            continue
                        }
                        if (!report) logFunction(
                            String.format(
                                context.getString(R.string.DEST_NO_DATA),
                                target.name
                            )
                        )
                    } catch (e: Exception) {
                        logFunction(e.toString())
                    }
                }
                if (!check) logFunction(String.format(context.getString(R.string.SRC_NO_DATA), target.name))
            } else if (mode == 2) {
                try {
                    logFunction(String.format(context.getString(R.string.DELETE_START), newTarget.name))
                    DocumentsContract.deleteDocument(context.contentResolver, newTarget.uri)
                } catch (e: Exception) {
                    logFunction(e.toString())
                }
            }
        }

    }
    logFunction("Done")
}

@RequiresApi(Build.VERSION_CODES.N)
fun runPatcherSAF13(
    context: Context,
    modDoc: DocumentFile?,
    switches: HashMap<Switch, DocumentFile?>,
    mode: Int,
    logFunction: (String) -> Unit
) {
    if (modDoc == null) {
        logFunction(context.getString(R.string.NO_MOD_DOC))
        return
    }
    logFunction("Start")
    for ((switch, doc) in switches) {
        if (!switch.isChecked) continue
        if (doc == null) {
            continue
        }
        val shared = findMatchedDoc13(doc) ?: continue
        val uri = shared.uri
        val uriPath = uri.path ?: continue
        for (target in modDoc.listFiles()) {
            if (target.name == null) continue
            var tempUri: Uri?
            val tempId = uriPath.substringAfterLast("/document/") + "/" + target.name

            tempUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                tempId
            )
            if (tempUri == null) continue
            val newTarget = DocumentFile.fromTreeUri(context, tempUri)
            if (newTarget == null) {
                logFunction(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            if (!newTarget.exists()) {
                logFunction(String.format(context.getString(R.string.NO_DEST), target.name))
                continue
            }
            if (!newTarget.isDirectory) {
                logFunction(String.format(context.getString(R.string.NOT_DIRECTORY), target.name))
                continue
            }
            if (mode == 1) {
                var check = false
                for (f in target.listFiles()) {
                    if (f == null) continue
                    if (f.isDirectory) {
                        for (f2 in f.listFiles()) {
                            if (f2.name != "__data") continue
                            logFunction(String.format(context.getString(R.string.COPY_TRY), target.name))
                            check = true
                            try {
                                val targetList = newTarget.listFiles()
                                if (targetList.size != 1) {
                                    logFunction(
                                        String.format(
                                            context.getString(R.string.MORE_THAN_ONE),
                                            newTarget.name
                                        )
                                    )
                                    continue
                                }
                                val gibberish = targetList[0]
                                var report = false
                                if (gibberish.isDirectory) {
                                    for (dat in gibberish.listFiles()) {
                                        if (dat.name == "__data") {
                                            report = true
                                            if (xcopy(
                                                    context,
                                                    f2,
                                                    dat
                                                )
                                            ) logFunction(
                                                String.format(
                                                    context.getString(R.string.COPY_DONE),
                                                    target.name
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    logFunction(
                                        String.format(
                                            context.getString(R.string.NOT_DIRECTORY_2),
                                            gibberish.name,
                                            target.name
                                        )
                                    )
                                    continue
                                }
                                if (!report) logFunction(
                                    String.format(
                                        context.getString(R.string.DEST_NO_DATA),
                                        target.name
                                    )
                                )
                            } catch (e: Exception) {
                                logFunction(e.toString())
                            }
                        }
                    }
                    if (f.name != "__data") continue
                    logFunction(String.format(context.getString(R.string.COPY_TRY), target.name))
                    check = true
                    try {
                        val targetList = newTarget.listFiles()
                        if (targetList.size != 1) {
                            logFunction(
                                String.format(
                                    context.getString(R.string.MORE_THAN_ONE),
                                    newTarget.name
                                )
                            )
                            continue
                        }
                        val gibberish = targetList[0]
                        var report = false
                        if (gibberish.isDirectory) {
                            for (dat in gibberish.listFiles()) {
                                if (dat.name == "__data") {
                                    report = true
                                    if (xcopy(
                                            context,
                                            f,
                                            dat
                                        )
                                    ) logFunction(
                                        String.format(
                                            context.getString(R.string.COPY_DONE),
                                            target.name
                                        )
                                    )
                                }
                            }
                        } else {
                            logFunction(
                                String.format(
                                    context.getString(R.string.NOT_DIRECTORY_2),
                                    gibberish.name,
                                    target.name
                                )
                            )
                            continue
                        }
                        if (!report) logFunction(
                            String.format(
                                context.getString(R.string.DEST_NO_DATA),
                                target.name
                            )
                        )
                    } catch (e: Exception) {
                        logFunction(e.toString())
                    }
                }
                if (!check) logFunction(String.format(context.getString(R.string.SRC_NO_DATA), target.name))
            } else if (mode == 2) {
                try {
                    logFunction(String.format(context.getString(R.string.DELETE_START), newTarget.name))
                    DocumentsContract.deleteDocument(context.contentResolver, newTarget.uri)
                } catch (e: Exception) {
                    logFunction(e.toString())
                }
            }
        }

    }
    logFunction("Done")
}

@RequiresApi(Build.VERSION_CODES.N)
fun runPatcherShizuku(
    context: Context,
    modPath: String?,
    switches: HashMap<Switch, String>,
    mode: Int,
    logFunction: (String) -> Unit,
    shell: ShizukuShell
) {
    fun checkModPath(path: String): String {
        return if (shell.checkDirExist(path)) path
        else if (shell.checkDirExist("/storage/emulated/0/$path")) "/storage/emulated/0/$path"
        else ""
    }

    if (modPath == null) {
        logFunction(context.getString(R.string.NO_MOD_DOC))
        logFunction("Null Mod Folder")
        return
    }
    var realModPath = checkModPath(modPath)
    if (realModPath.isEmpty()) {
        logFunction(context.getString(R.string.NO_MOD_DOC))
        return
    }

    if (realModPath.endsWith("/")) {
        realModPath = realModPath.substring(0, realModPath.length - 1);
    }
    val modList = shell.getSubDirs("/storage/emulated/0/$modPath")

    logFunction("Start")
    for ((switch, path) in switches) {
        if (!switch.isChecked) continue
        for (target in modList) {
            var dstDataPath = ""

            if (shell.checkDirExist("$realModPath/$target/__data")) {
                dstDataPath = "$realModPath/$target/__data"
            }
            else {
                val subTargets = shell.getSubDirs("$realModPath/$target")
                for (subTarget in subTargets) {
                    if (shell.checkDirExist("$realModPath/$target/$subTarget/__data")) {
                        dstDataPath = "$realModPath/$target/$subTarget/__data"
                        break
                    }
                }
            }
            if (dstDataPath.isEmpty()) {
                logFunction(String.format(context.getString(R.string.SRC_NO_DATA), target))
                continue
            }
            if (!shell.checkDirExist("$path/files/UnityCache/Shared/${target}")) {
                logFunction(String.format(context.getString(R.string.NO_DEST), target))
                continue
            }
            if (mode == 1) {
                logFunction(String.format(context.getString(R.string.COPY_TRY), target))
                val listDir = shell.getSubDirs("$path/files/UnityCache/Shared/${target}")
                if (listDir.isEmpty()) {
                    logFunction(String.format(context.getString(R.string.DEST_NO_DATA), target))
                    continue
                }
                if (listDir.size > 1) {
                    logFunction(String.format( context.getString(R.string.MORE_THAN_ONE), target))
                    continue
                }
                val gibberish = listDir[0]
                if (!shell.checkDirExist("$path/files/UnityCache/Shared/${target}/$gibberish")) {
                    logFunction(String.format(context.getString(R.string.NOT_DIRECTORY_2), gibberish, target))
                    continue
                }
                if (!shell.checkDirExist("$path/files/UnityCache/Shared/${target}/$gibberish/__data")) {
                    logFunction(String.format(context.getString(R.string.DEST_NO_DATA), target))
                    continue
                }

                shell.copyFile(dstDataPath, "$path/files/UnityCache/Shared/${target}/$gibberish/__data")
                logFunction(String.format(context.getString(R.string.COPY_DONE), target))
            } else if (mode == 2) {
                logFunction(String.format(context.getString(R.string.DELETE_START), target))
                shell.removeFolder("$path/files/UnityCache/Shared/${target}")
            }
        }
    }
    logFunction("Done")
}