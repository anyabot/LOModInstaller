package com.altter.lomodinstaller

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.system.exitProcess

class UserService : IUserService.Stub {
    constructor()

    override fun destroy() {
        shellProcess?.destroy()
        exitProcess(0)
    }

    override fun exit() {
        exitProcess(0)
    }

    private var shellProcess: Process? = null
    private var shellIn: BufferedWriter? = null
    private var shellOut: BufferedReader? = null

    @Synchronized
    private fun newShell() {
        shellProcess?.destroy()
        val p = Runtime.getRuntime().exec("sh")
        shellProcess = p
        shellIn = BufferedWriter(OutputStreamWriter(p.outputStream))
        shellOut = BufferedReader(InputStreamReader(p.inputStream))
    }

    @Synchronized
    override fun runShellCommands(commands: Array<String>): String {
        if (shellProcess?.isAlive != true) newShell()
        val writer = shellIn ?: return ""
        val reader = shellOut ?: return ""

        val sentinel = "____END____"
        val cmd = commands.joinToString(" ") { arg ->
            if (arg.contains(" ") || arg.contains("'")) "\"${arg.replace("\"", "\\\"")}\"" else arg
        }
        writer.write("$cmd 2>&1; echo $sentinel\n")
        writer.flush()

        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (line == sentinel) break
            output.append(line).append("\n")
        }

        // cp into Android/data can disrupt the FUSE transport; restart shell after it completes
        if (commands.firstOrNull() == "cp") {
            Thread.sleep(500)
            newShell()
        }

        return output.toString()
    }
}
