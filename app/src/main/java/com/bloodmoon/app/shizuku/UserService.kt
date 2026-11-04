package com.bloodmoon.app.shizuku

import android.os.Process

/**
 * Runs inside the process Shizuku hosts (shell UID, or root UID if Shizuku itself is
 * root-backed) - NOT inside Blood Moon's normal app process. Deliberately does exactly one
 * thing (run a shell command) to keep the privileged surface area as small as possible.
 *
 * This class is instantiated by Shizuku via reflection from the class name passed to
 * Shizuku.UserServiceArgs - it does not need (and must not have) an entry in AndroidManifest.xml.
 */
class UserService : IUserService.Stub() {

    override fun execShellCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }
}
