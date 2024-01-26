package com.altter.lomodinstaller

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess


class UserService : IUserService.Stub {
    //Include an empty constructor.
    constructor() {
    }

    override fun destroy() {
        //Shizuku wants the service to be killed. Clean up and exit.
        exitProcess(0)
    }

    override fun exit() {
        exitProcess(0)
    }

    override fun runShellCommand(command: String?): String {
        var process: Process? = null
        val output = StringBuilder()
        try {
            process = Runtime.getRuntime().exec(command, null, null)
            val mInput = BufferedReader(InputStreamReader(process.inputStream))
            val mError = BufferedReader(InputStreamReader(process.errorStream))
            process.waitFor()
            var line: String?
            while (mInput.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (mError.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        } catch (ignored: Exception) {

        } finally {
            process?.destroy()
        }
        return output.toString()
    }
}
