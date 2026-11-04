package com.bloodmoon.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.bloodmoon.app.util.GrantResult
import com.bloodmoon.app.util.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

object ShizukuHelper {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return if (isShizukuAvailable()) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Official pattern from Shizuku's own docs. Pre-v11 Shizuku is explicitly unsupported
     * upstream (their README treats it as a dead end, not something to work around), so this
     * only implements the current (v11+) flow.
     */
    fun requestShizukuPermission(requestCode: Int) {
        if (!isShizukuAvailable() || hasShizukuPermission()) return
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Runs [command] with Shizuku's privilege level (shell over ADB, or root if Shizuku itself
     * is root-backed) via a short-lived UserService.
     *
     * This binds a fresh privileged process per call and tears it down immediately after -
     * simpler and safer to reason about than keeping one alive across the app's lifetime, at
     * the cost of a little latency. Fine for the occasional settings change this is used for.
     */
    suspend fun execShellCommand(context: Context, command: String): ShellResult {
        if (!isShizukuAvailable()) return ShellResult.Failed("Shizuku is not running")
        if (!hasShizukuPermission()) return ShellResult.Failed("Blood Moon does not have Shizuku permission")

        val result = withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<ShellResult> { cont ->
                val args = Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, UserService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("privileged")
                    .debuggable(false)
                    .version(1)

                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val outcome = try {
                                when {
                                    binder == null -> ShellResult.Failed("Null binder when connecting to the privileged service")
                                    !binder.pingBinder() -> ShellResult.Failed("Privileged service connected but did not respond (pingBinder failed)")
                                    else -> ShellResult.Output(IUserService.Stub.asInterface(binder).execShellCommand(command))
                                }
                            } catch (e: Exception) {
                                ShellResult.Failed("Exception while running command: ${e::class.java.simpleName}: ${e.message}")
                            } finally {
                                runCatching { Shizuku.unbindUserService(args, connection, true) }
                            }
                            if (cont.isActive) cont.resume(outcome)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {}
                }

                try {
                    Shizuku.bindUserService(args, connection)
                    // bindUserService's own return value isn't reliable across all Shizuku
                    // versions as a success signal, so we don't treat it as fatal by itself -
                    // the timeout below is what actually catches a bind that never calls back.
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(ShellResult.Failed("bindUserService threw an exception: ${e::class.java.simpleName}: ${e.message}"))
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                }
            }
        }

        return result ?: ShellResult.Failed("Timed out waiting for the Shizuku privileged service to connect (8s)")
    }

    /** Grants this app WRITE_SECURE_SETTINGS via Shizuku's shell-level privilege. */
    suspend fun grantWriteSecureSettingsPermission(context: Context): GrantResult {
        if (hasWriteSecureSettings(context)) return GrantResult.Granted

        val shellResult = execShellCommand(
            context, "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
        if (shellResult is ShellResult.Failed) {
            return GrantResult.Failed(shellResult.reason)
        }

        // pm grant ran without throwing, but this process's own PackageManager snapshot can be
        // stale (permission grants made by another process don't always invalidate a running
        // process's cached permission state) - re-check via a fresh PackageInfo query, and if
        // that STILL says no, it's very likely just the stale-cache case, not a real failure.
        if (hasWriteSecureSettings(context)) return GrantResult.Granted

        val freshlyGranted = try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            val index = info.requestedPermissions?.indexOf(android.Manifest.permission.WRITE_SECURE_SETTINGS) ?: -1
            index >= 0 && (info.requestedPermissionsFlags?.getOrNull(index)?.and(PackageInfoFlagGranted) ?: 0) != 0
        } catch (e: Exception) {
            false
        }

        return if (freshlyGranted) GrantResult.Granted else GrantResult.NeedsAppRestart
    }

    private const val PackageInfoFlagGranted = android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED

    private fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
