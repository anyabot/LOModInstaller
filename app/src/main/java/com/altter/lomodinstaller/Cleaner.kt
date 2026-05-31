package com.altter.lomodinstaller

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Switch
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.io.FileUtils
import java.io.File

// SAF navigation helpers — used here and in MainActivity.buildPatchIO
fun findMatchedDoc(path: String, rootDoc: DocumentFile): DocumentFile? =
    rootDoc.findFile(path)?.findFile("files")?.findFile("UnityCache")?.findFile("Shared")

fun findMatchedDoc13(rootDoc: DocumentFile): DocumentFile? =
    rootDoc.findFile("files")?.findFile("UnityCache")?.findFile("Shared")

// SAF file I/O helpers
fun xcopy(context: Context, from: DocumentFile, to: DocumentFile): Boolean {
    val content = xread(context, from) ?: return false
    return xwrite(context, to.uri, content)
}

fun xwrite(context: Context, uri: Uri, content: ByteArray): Boolean {
    val file = DocumentFile.fromSingleUri(context, uri) ?: return false
    if (!file.canWrite()) return false
    val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: return false
    stream.write(content); stream.flush(); stream.close()
    return true
}

fun xread(context: Context, file: DocumentFile): ByteArray? {
    val stream = context.contentResolver.openInputStream(file.uri) ?: return null
    return stream.readBytes().also { stream.close() }
}

// ---- Abstract item ----

sealed class PatchItem {
    abstract val name: String
    data class FileItem(val file: File)       : PatchItem() { override val name get() = file.name }
    data class DocItem(val doc: DocumentFile) : PatchItem() { override val name get() = doc.name ?: "" }
    data class ShellItem(val path: String)    : PatchItem() { override val name get() = path.substringAfterLast('/') }
}

// ---- Mod source ----

interface ModSource {
    fun listTargets(): List<String>
    fun findSourceData(targetName: String): PatchItem?
}

class DocumentModSource(private val modDoc: DocumentFile) : ModSource {
    override fun listTargets() = modDoc.listFiles().mapNotNull { it.name }

    override fun findSourceData(targetName: String): PatchItem? {
        val target = modDoc.findFile(targetName) ?: return null
        target.findFile("__data")?.let { return PatchItem.DocItem(it) }
        for (sub in target.listFiles()) {
            if (!sub.isDirectory) continue
            sub.findFile("__data")?.let { return PatchItem.DocItem(it) }
        }
        return null
    }
}

class FileModSource(private val modPath: String, private val storages: List<String>) : ModSource {
    private val modDir: File? by lazy {
        val direct = File(modPath)
        if (direct.isAbsolute && direct.exists()) return@lazy direct
        storages.mapNotNull { s ->
            val root = if (s == "emulated") "/storage/emulated/0" else "/storage/$s"
            File("$root/$modPath").takeIf { it.exists() }
        }.firstOrNull()
    }

    override fun listTargets() =
        modDir?.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    override fun findSourceData(targetName: String): PatchItem? {
        val target = modDir?.listFiles()?.find { it.name == targetName && it.isDirectory } ?: return null
        File(target, "__data").takeIf { it.exists() }?.let { return PatchItem.FileItem(it) }
        target.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            File(sub, "__data").takeIf { it.exists() }?.let { return PatchItem.FileItem(it) }
        }
        return null
    }
}

class ShellModSource(private val modPath: String, private val shell: ShizukuShell) : ModSource {
    override fun listTargets() = shell.getSubDirs(modPath)

    override fun findSourceData(targetName: String): PatchItem? {
        val direct = "$modPath/$targetName/__data"
        if (shell.checkDirExist(direct)) return PatchItem.ShellItem(direct)
        for (sub in shell.getSubDirs("$modPath/$targetName")) {
            val subPath = "$modPath/$targetName/$sub/__data"
            if (shell.checkDirExist(subPath)) return PatchItem.ShellItem(subPath)
        }
        return null
    }
}

// ---- Patch platform ----

interface PatchPlatform {
    val switch: Switch
    fun findSharedTarget(targetName: String): PatchItem?
    fun children(dir: PatchItem): List<PatchItem>
    fun findDataFile(gibberish: PatchItem): PatchItem?
    fun copy(source: PatchItem, dest: PatchItem): Boolean
    fun delete(item: PatchItem): String
}

class DocumentPlatform(
    override val switch: Switch,
    private val context: Context,
    private val sharedDoc: DocumentFile
) : PatchPlatform {

    override fun findSharedTarget(targetName: String): PatchItem? {
        val uri = sharedDoc.uri
        val uriPath = uri.path ?: return null
        val tempId = uriPath.substringAfterLast("/document/") + "/" + targetName
        val tempUri = DocumentsContract.buildDocumentUriUsingTree(uri, tempId)
        val doc = DocumentFile.fromTreeUri(context, tempUri) ?: return null
        if (!doc.exists() || !doc.isDirectory) return null
        return PatchItem.DocItem(doc)
    }

    override fun children(dir: PatchItem) =
        (dir as PatchItem.DocItem).doc.listFiles().map { PatchItem.DocItem(it) }

    override fun findDataFile(gibberish: PatchItem): PatchItem? {
        val doc = (gibberish as PatchItem.DocItem).doc
        if (!doc.isDirectory) return null
        return doc.listFiles().find { it.name == "__data" }?.let { PatchItem.DocItem(it) }
    }

    override fun copy(source: PatchItem, dest: PatchItem) =
        xcopy(context, (source as PatchItem.DocItem).doc, (dest as PatchItem.DocItem).doc)

    override fun delete(item: PatchItem): String = try {
        DocumentsContract.deleteDocument(context.contentResolver, (item as PatchItem.DocItem).doc.uri)
        "Removed: ${item.name}"
    } catch (e: Exception) {
        "rm failed: ${item.name} (${e.message})"
    }
}

class FilePlatform(
    override val switch: Switch,
    private val packagePath: String,
    private val storages: List<String>
) : PatchPlatform {

    private fun resolve(relativePath: String): File? {
        val f = File(relativePath)
        if (f.isAbsolute && f.exists()) return f
        return storages.mapNotNull { s ->
            val root = if (s == "emulated") "/storage/emulated/0" else "/storage/$s"
            File("$root/$relativePath").takeIf { it.exists() }
        }.firstOrNull()
    }

    private val sharedBase get() = "$packagePath/files/UnityCache/Shared"

    override fun findSharedTarget(targetName: String): PatchItem? {
        val f = resolve("$sharedBase/$targetName") ?: return null
        return if (f.isDirectory) PatchItem.FileItem(f) else null
    }

    override fun children(dir: PatchItem) =
        (dir as PatchItem.FileItem).file.listFiles()?.map { PatchItem.FileItem(it) } ?: emptyList()

    override fun findDataFile(gibberish: PatchItem): PatchItem? {
        val f = (gibberish as PatchItem.FileItem).file
        if (!f.isDirectory) return null
        return File(f, "__data").takeIf { it.exists() }?.let { PatchItem.FileItem(it) }
    }

    override fun copy(source: PatchItem, dest: PatchItem): Boolean = try {
        FileUtils.copyFile((source as PatchItem.FileItem).file, (dest as PatchItem.FileItem).file)
        true
    } catch (e: Exception) { false }

    override fun delete(item: PatchItem): String {
        val f = (item as PatchItem.FileItem).file
        return if (f.delete()) "Removed: ${f.name}" else "rm failed: ${f.name}"
    }
}

class ShellPlatform(
    override val switch: Switch,
    private val gamePath: String,
    private val shell: ShizukuShell
) : PatchPlatform {

    private val sharedBase get() = "$gamePath/files/UnityCache/Shared"

    override fun findSharedTarget(targetName: String): PatchItem? {
        val path = "$sharedBase/$targetName"
        return if (shell.checkDirExist(path)) PatchItem.ShellItem(path) else null
    }

    override fun children(dir: PatchItem) =
        shell.getSubDirs((dir as PatchItem.ShellItem).path)
            .map { PatchItem.ShellItem("${dir.path}/$it") }

    override fun findDataFile(gibberish: PatchItem): PatchItem? {
        val path = "${(gibberish as PatchItem.ShellItem).path}/__data"
        return if (shell.checkDirExist(path)) PatchItem.ShellItem(path) else null
    }

    override fun copy(source: PatchItem, dest: PatchItem): Boolean {
        shell.copyFile((source as PatchItem.ShellItem).path, (dest as PatchItem.ShellItem).path)
        return true
    }

    override fun delete(item: PatchItem) = shell.removeFile((item as PatchItem.ShellItem).path)
}

// ---- Unified patcher ----

fun runPatcher(
    context: Context,
    platformMods: List<Pair<ModSource, PatchPlatform>>,
    mode: Int,
    logFunction: (String) -> Unit
) {
    logFunction("Start")

    for ((mod, platform) in platformMods) {
        if (!platform.switch.isChecked) continue
        val targets = mod.listTargets()
        if (targets.isEmpty()) continue
        for (targetName in targets) {
            val sourceData = mod.findSourceData(targetName)
            if (sourceData == null) {
                logFunction(String.format(context.getString(R.string.SRC_NO_DATA), targetName))
                continue
            }

            val sharedTarget = platform.findSharedTarget(targetName)
            if (sharedTarget == null) {
                logFunction(String.format(context.getString(R.string.NO_DEST), targetName))
                continue
            }

            val childList = platform.children(sharedTarget)
            if (childList.isEmpty()) {
                logFunction(String.format(context.getString(R.string.DEST_NO_DATA), targetName))
                continue
            }
            if (childList.size > 1) {
                logFunction(String.format(context.getString(R.string.MORE_THAN_ONE), targetName))
                continue
            }

            val destData = platform.findDataFile(childList[0])
            if (destData == null) {
                logFunction(String.format(context.getString(R.string.DEST_NO_DATA), targetName))
                continue
            }

            when (mode) {
                1 -> {
                    logFunction(String.format(context.getString(R.string.COPY_TRY), targetName))
                    if (platform.copy(sourceData, destData))
                        logFunction(String.format(context.getString(R.string.COPY_DONE), targetName))
                }
                2 -> {
                    logFunction(String.format(context.getString(R.string.DELETE_START), targetName))
                    logFunction(platform.delete(destData))
                }
            }
        }
    }

    logFunction("Done")
}
