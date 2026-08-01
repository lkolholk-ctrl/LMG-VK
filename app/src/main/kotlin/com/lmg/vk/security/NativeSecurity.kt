package com.lmg.vk.security

/**
 * Нативная проверка безопасности (порт `com.liquidmusicglass.security.NativeSecurity`).
 *
 * Оригинал дергал liblmg_security.so; в LMG VK эта либа не портирована, поэтому
 * loadLibrary выполняется в try/catch, а все методы возвращают «безопасные»
 * значения — SecurityUtils при этом работает в Java-only режиме (root-пути,
 * build-проперти эмулятора). Любой вызов нативной части не может уронить приложение.
 */
object NativeSecurity {

    @Volatile
    private var libraryLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("lmg_security")
            libraryLoaded = true
        } catch (_: Throwable) {
            libraryLoaded = false
        }
    }

    /**
     * Bitmask проверок:
     * 0x00 = safe
     * 0x01 = debugger/ptrace active
     * 0x02 = Frida active (port/proc scan)
     * 0x04 = Xposed/LSPosed active
     * 0x08 = Emulator detected
     */
    fun nativeSecurityCheck(): Int {
        if (!libraryLoaded) return 0
        return try {
            realNativeSecurityCheck()
        } catch (_: Throwable) {
            0
        }
    }

    fun nativeVerifySignature(signatureBytes: ByteArray): Boolean {
        if (!libraryLoaded) return true
        return try {
            realNativeVerifySignature(signatureBytes)
        } catch (_: Throwable) {
            true
        }
    }

    fun nativeCheckIntegrity(apkPath: String): Boolean {
        if (!libraryLoaded) return true
        return try {
            realNativeCheckIntegrity(apkPath)
        } catch (_: Throwable) {
            true
        }
    }

    fun nativeCheckHooks(): Boolean {
        if (!libraryLoaded) return true
        return try {
            realNativeCheckHooks()
        } catch (_: Throwable) {
            true
        }
    }

    private external fun realNativeSecurityCheck(): Int
    private external fun realNativeVerifySignature(signatureBytes: ByteArray): Boolean
    private external fun realNativeCheckIntegrity(apkPath: String): Boolean
    private external fun realNativeCheckHooks(): Boolean
}
