package com.altter.lomodinstaller

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import kotlin.reflect.KFunction0

class ShizukuShell(serviceCallback: KFunction0<Unit>? = null) {
    private var mUserService: IUserService? = null

    private fun ensureUserService(): Boolean {
        if (mUserService != null) {
            return true
        }
        val mUserServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                UserService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        Shizuku.bindUserService(mUserServiceArgs, mServiceConnection)
        return false
    }

    fun isReady(): Boolean {
        return isSupported() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isSupported(): Boolean {
        return Shizuku.pingBinder()
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            if (!iBinder.pingBinder()) {
                return
            }
            mUserService = IUserService.Stub.asInterface(iBinder)
            serviceCallback?.run { invoke() }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    fun runShizukuCommand(cmd: Array<String>): String? {
        if (ensureUserService()) {
            val res = mUserService?.runShellCommands(cmd)
            return res.toString()
        }
        return null
    }

    fun checkDirExist(path: String): Boolean {
        val res = runShizukuCommand(arrayOf("ls", "-d", path))
        return !res.isNullOrBlank() && !res.contains("No such file or directory") && !res.contains("ls:")
    }

    fun getSubDirs(path: String): List<String> {
        val res = runShizukuCommand(arrayOf("ls", path))
        Log.d("ShizukuShell", "getSubDirs($path) => ${res?.take(200)}")
        if (res.isNullOrBlank()) return emptyList()
        return res.split("\n").filter { s -> s.isNotEmpty() }
    }

    // Returns null on success, error message on failure
    fun copyFile(fromPath: String, toPath: String): String? {
        val lsSrc = runShizukuCommand(arrayOf("ls", fromPath))
        val res = runShizukuCommand(arrayOf("cp", fromPath, toPath))
        return when {
            res == null -> "service not ready"
            res.isBlank() -> null
            else -> "src=$fromPath ls=$lsSrc | dst=$toPath | cp=$res"
        }
    }

    fun removeFile(path: String): String {
        runShizukuCommand(arrayOf("rm", "-f", path))
        if (!checkDirExist(path)) return "Removed: ${path.substringAfterLast('/')}"
        // rm failed (permission denied on parent dir) — zero out the file instead
        val infoPath = path.substringBeforeLast('/') + "/__info"
        runShizukuCommand(arrayOf("cp", "/dev/null", infoPath))
        val result = runShizukuCommand(arrayOf("cp", "/dev/null", path)) ?: return "clear: service not ready"
        val errMsg = result.trim()
        return if (checkDirExist(path)) {
            "Cleared: ${path.substringAfterLast('/')}"
        } else {
            "clear failed: ${path.substringAfterLast('/')}${if (errMsg.isNotEmpty()) " ($errMsg)" else ""}"
        }
    }
}