package com.neonlauncher.common.exception

open class LauncherException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(message: String) : this(message, null)
}

class AuthException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class ManifestException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class ModDownloadException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class HashMismatchException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class LaunchException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class ConfigException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class UpdateException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class JavaNotFoundException(message: String, cause: Throwable? = null) : LauncherException(message, cause)
class NetworkException(message: String, cause: Throwable? = null) : LauncherException(message, cause)