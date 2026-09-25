package com.lmg.vk.network

import com.lmg.vk.network.dto.RequestTokenResponse
import org.junit.Assert.*
import org.junit.Test

class VkAuthDiagnosticTest {
    @Test fun clientRejectionKeepsTheServerReasonAndPasswordFlow() {
        val body = """{"error":"invalid_client","error_description":"Too many attempts",
            "error_type":"too_many_attempts"}"""
        val parsed = VkJson.moshi.adapter(RequestTokenResponse.ClientError::class.java).fromJson(body)!!
        assertEquals("Too many attempts", parsed.errorDescription)
        assertEquals("[oauth/token; error=invalid_client; type=too_many_attempts; grant=phone_confirmation_sid]",
            authRejectionDiagnostic(parsed.error, parsed.errorType, "phone_confirmation_sid"))
    }

    @Test fun otherOAuthErrorsAlsoPreserveErrorTypeWhenPresent() {
        val adapter = VkJson.moshi.adapter(RequestTokenResponse.UnknownError::class.java)
        val parsed = adapter.fromJson("""{"error":"invalid_request","error_type":"invalid_session"}""")!!
        assertEquals("invalid_session", parsed.errorType)
        assertEquals("", adapter.fromJson("""{"error":"invalid_request"}""")!!.errorType)
    }

    @Test fun diagnosticsDoNotEchoUrlsOrArbitraryResponseText() {
        val diagnostic = authRejectionDiagnostic("https://example.com/?token=secret", "message\nsecret", "password")
        assertFalse(diagnostic.contains("secret"))
        assertFalse(diagnostic.contains("\n"))
        assertTrue(diagnostic.contains("grant=password"))
        assertTrue(authRejectionDiagnostic("", "", "password").contains("type=unspecified"))
    }
}
