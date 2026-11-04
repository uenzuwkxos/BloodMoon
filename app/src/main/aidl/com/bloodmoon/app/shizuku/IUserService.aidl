// Shizuku UserService contract. This runs in a separate process hosted by Shizuku, with the
// privilege level Shizuku itself was started with (shell over ADB, or root). It is intentionally
// tiny: one method to run a command, one to shut the privileged process down.
//
// NOTE: Shizuku requires the destroy() method to use exactly transaction id 16777114, and AIDL
// requires that if any method in the interface has an explicit id, all of them must.
package com.bloodmoon.app.shizuku;

interface IUserService {
    String execShellCommand(String command) = 1;
    void destroy() = 16777114;
}
