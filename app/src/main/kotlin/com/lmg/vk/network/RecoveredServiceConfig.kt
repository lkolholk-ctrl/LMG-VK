package com.lmg.vk.network

/**
 * Константы независимых внешних сервисов, восстановленные из VK X 8.12.1.
 *
 * Они намеренно разделены: Android OAuth secret не относится к UMA, а ключи
 * UMA и Last.fm не являются учётными данными собственного бэкенда LMG VK.
 */
internal object RecoveredServiceConfig {
    const val VK_ANDROID_CLIENT_ID = "2274003"
    const val VK_ANDROID_CLIENT_SECRET = "hHbZxrka2uZ6jB1inYsH"

    const val VK_ANDROID_USER_AGENT = "VKAndroidApp/8.108-26257"
    const val VK_ANDROID_AUTH_USER_AGENT = "VKAndroidApp/8.165.1-48535"

    const val UMA_PACKAGE = "com.uma.musicvk"
    const val UMA_APP_ID = 6767438
    const val UMA_APP_SECRET = "ppBOmwQYYOMGulmaiPyK"
    const val UMA_DIGEST_HASH = "2D0D1nXbs2cX1/Q8wFkyv93NHts="

    const val LAST_FM_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    const val LAST_FM_API_KEY = "4085c85b9f48c43c0d86c4223bbd8458"
    const val LAST_FM_SHARED_SECRET = "c6fd658cbb3e890ad61f1c3f809cb14d"
}
