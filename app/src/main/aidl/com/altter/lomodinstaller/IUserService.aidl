// IUserService.aidl
package com.altter.lomodinstaller;

// Declare any non-default types here with import statements

interface IUserService {
    void destroy() = 16777114;

    void exit() = 1;

    String runShellCommand(String command) = 0;
}