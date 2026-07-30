package com.lmg.vk.jni

/**
 * Восстановленный JNI-мост (v8.12.1).
 *
 * Обфусцированные имена x00/x01/x02 заменены восстановленными
 * (соответствие установлено эмуляцией RegisterNatives + анализом вызовов
 * в Kotlin-слое).
 */
object LmgNative {

    init {
        System.loadLibrary("lmg")
    }

    /** x00() — эндпоинты VK, client_id/secret (base64), User-Agent'ы клиентов. */
    @JvmStatic
    external fun getVkApiData(): BundleNativeClass

    /** x01() — окружение бэкенда LMG VK (ui.lmg.app, api.lmg.app, ключи лицензии). */
    @JvmStatic
    external fun getLmgEnvironment(): BundleNativeClass

    /**
     * x02(input) — обёртка silent-авторизации:
     * base64( input XOR "THETRUTHLIES" ), результат в ad[0] (параметр "code").
     */
    @JvmStatic
    external fun getSilentAuthorizationEnvironment(input: String): BundleNativeClass
}

/** Нативный контейнер: Object[] ad + add(idx, obj). */
open class BundleNativeClass(size: Int) {
    @JvmField
    var ad: Array<Any?> = arrayOfNulls(size)

    fun add(idx: Int, obj: Any) {
        ad[idx] = obj
    }
}
