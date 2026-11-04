package com.bloodmoon.app.util

/** Result of a privileged shell command: either its combined stdout/stderr, or a diagnostic reason it never ran. */
sealed class ShellResult {
    data class Output(val text: String) : ShellResult()
    data class Failed(val reason: String) : ShellResult()
}

/** Result of trying to grant a permission: distinguishes "actually failed" from "probably worked, needs app restart to see it". */
sealed class GrantResult {
    object Granted : GrantResult()
    object NeedsAppRestart : GrantResult()
    data class Failed(val reason: String) : GrantResult()
}
