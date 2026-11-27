package com.fashionstudio.aipro.core.security

/**
 * Redacts potential API keys or Bearer tokens before any diagnostic message is displayed or logged.
 */
object SecuritySanitizer {
    private val geminiKeyRegex = Regex("""AIza[0-9A-Za-z\-_]{20,}""")
    private val bearerRegex = Regex("""Bearer\s+[A-Za-z0-9\-._~+/]+=*""", RegexOption.IGNORE_CASE)
    private val queryKeyRegex = Regex("""([?&]key=)[^&\s]+""", RegexOption.IGNORE_CASE)

    fun redact(input: String): String {
        return input
            .replace(geminiKeyRegex, "[REDACTED_API_KEY]")
            .replace(bearerRegex, "Bearer [REDACTED_TOKEN]")
            .replace(queryKeyRegex, "$1[REDACTED_KEY]")
    }
}
