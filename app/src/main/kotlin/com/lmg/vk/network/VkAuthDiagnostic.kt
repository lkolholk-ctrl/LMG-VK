package com.lmg.vk.network

/** Only protocol identifiers are shown; request credentials and session IDs never enter this text. */
internal fun authRejectionDiagnostic(error: String, type: String, grant: String): String {
    fun label(value: String): String = when {
        value.isBlank() -> "unspecified"
        value.length <= 64 && value.matches(Regex("[a-zA-Z0-9_-]+")) -> value
        else -> "unrecognized"
    }
    return "[oauth/token; error=${label(error)}; type=${label(type)}; grant=${label(grant)}]"
}
