package com.altter.lomodinstaller

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
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
        val res = runShizukuCommand(arrayOf("ls", path))
        return res != null && !res.contains("No such file or directory")
    }

    fun getSubDirs(path: String): List<String> {
        val res = runShizukuCommand(arrayOf("ls", path))
        return res?.split("\n")?.filter { s -> s.isNotEmpty() } ?: emptyList()
    }

    fun copyFile(fromPath: String, toPath: String) {
        runShizukuCommand(arrayOf("cp", fromPath, toPath))
    }

    fun removeFolder(path: String) {
        val path2 = path.replace(" ", Regex.escapeReplacement("\\ "))
        runShizukuCommand(arrayOf("rm", "-r", path))
    }
}