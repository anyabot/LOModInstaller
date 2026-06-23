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
    private val cachedTargets: List<String> by lazy { shell.getSubDirs(modPath) }
    private val cachedSourceData: Map<String, PatchItem?> by lazy {
        cachedTargets.associateWith { targetName ->
            val direct = "$modPath/$targetName/__data"
            if (shell.checkDirExist(direct)) return@associateWith PatchItem.ShellItem(direct)
            for (sub in shell.getSubDirs("$modPath/$targetName")) {
                val subPath = "$modPath/$targetName/$sub/__data"
                if (shell.checkDirExist(subPath)) return@associateWith PatchItem.ShellItem(subPath)
            }
            null
        }
    }

    override fun listTargets() = cachedTargets
    override fun findSourceData(targetName: String) = cachedSourceData[targetName]
}

// ---- Patch platform ----

interface PatchPlatform {
    val switch: Switch
    val name: String
    fun findSharedTarget(targetName: String): PatchItem?
    fun children(dir: PatchItem): List<PatchItem>
    fun findDataFile(gibberish: PatchItem): PatchItem?
    // Find the __data file inside targetDir by looking for any subfolder containing both __data and __info
    fun findDestData(targetDir: PatchItem): PatchItem? {
        val subs = children(targetDir)
        // prefer subfolder containing __info (valid Unity cache entry), fall back to any with __data
        val withInfo = subs.firstOrNull { sub -> findDataFile(sub) != null && hasInfoFile(sub) }
        val withData = withInfo ?: subs.firstOrNull { sub -> findDataFile(sub) != null }
        return withData?.let { findDataFile(it) }
    }
    fun hasInfoFile(dir: PatchItem): Boolean
    // Returns null on success, error message on failure
    fun copy(source: PatchItem, dest: PatchItem): String?
    fun delete(item: PatchItem): String
}

class DocumentPlatform(
    override val switch: Switch,
    override val name: String,
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

    override fun hasInfoFile(dir: PatchItem) =
        (dir as PatchItem.DocItem).doc.listFiles().any { it.name == "__info" }

    override fun copy(source: PatchItem, dest: PatchItem): String? =
        if (xcopy(context, (source as PatchItem.DocItem).doc, (dest as PatchItem.DocItem).doc)) null else "write failed"

    override fun delete(item: PatchItem): String {
        val doc = (item as PatchItem.DocItem).doc
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, doc.uri)
            "Removed: ${item.name}"
        } catch (e: Exception) {
            doc.parentFile?.findFile("__info")?.let { xwrite(context, it.uri, ByteArray(0)) }
            if (xwrite(context, doc.uri, ByteArray(0)))
                "Cleared: ${item.name}"
            else
                "clear failed: ${item.name} (${e.message})"
        }
    }
}

class FilePlatform(
    override val switch: Switch,
    override val name: String,
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

    override fun hasInfoFile(dir: PatchItem) =
        File((dir as PatchItem.FileItem).file, "__info").exists()

    override fun copy(source: PatchItem, dest: PatchItem): String? = try {
        FileUtils.copyFile((source as PatchItem.FileItem).file, (dest as PatchItem.FileItem).file)
        null
    } catch (e: Exception) { e.message ?: "unknown error" }

    override fun delete(item: PatchItem): String {
        val f = (item as PatchItem.FileItem).file
        if (f.delete()) return "Removed: ${f.name}"
        f.parentFile?.resolve("__info")?.takeIf { it.exists() }?.writeBytes(ByteArray(0))
        return try {
            f.writeBytes(ByteArray(0))
            "Cleared: ${f.name}"
        } catch (e: Exception) {
            "clear failed: ${f.name} (${e.message})"
        }
    }
}

class ShellPlatform(
    override val switch: Switch,
    override val name: String,
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

    override fun hasInfoFile(dir: PatchItem) =
        shell.checkDirExist("${(dir as PatchItem.ShellItem).path}/__info")

    override fun copy(source: PatchItem, dest: PatchItem): String? =
        shell.copyFile((source as PatchItem.ShellItem).path, (dest as PatchItem.ShellItem).path)

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

        // Phase 1: resolve source and destination for this platform before any copy/delete
        val targets = mod.listTargets()
        logFunction("--- ${platform.name} | targets: ${targets.size} [${targets.joinToString()}] ---")
        if (targets.isEmpty()) continue

        data class PatchJob(val targetName: String, val sourceData: PatchItem, val destData: PatchItem)
        val jobs = mutableListOf<PatchJob>()

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
            val destData = platform.findDestData(sharedTarget)
            if (destData == null) {
                logFunction(String.format(context.getString(R.string.DEST_NO_DATA), targetName))
                continue
            }
            jobs.add(PatchJob(targetName, sourceData, destData))
        }

        // Phase 2: execute copies/deletes for this platform
        for (job in jobs) {
            when (mode) {
                1 -> {
                    logFunction(String.format(context.getString(R.string.COPY_TRY), job.targetName))
                    val srcPath = if (job.sourceData is PatchItem.ShellItem) job.sourceData.path else job.sourceData.name
                    val dstPath = if (job.destData is PatchItem.ShellItem) job.destData.path else job.destData.name
                    logFunction("  src: $srcPath")
                    logFunction("  dst: $dstPath")
                    val err = platform.copy(job.sourceData, job.destData)
                    if (err == null)
                        logFunction(String.format(context.getString(R.string.COPY_DONE), job.targetName))
                    else
                        logFunction(String.format(context.getString(R.string.COPY_FAIL), job.targetName, err))
                }
                2 -> {
                    logFunction(String.format(context.getString(R.string.DELETE_START), job.targetName))
                    logFunction(platform.delete(job.destData))
                }
            }
        }
    }

    logFunction("Done")
}
