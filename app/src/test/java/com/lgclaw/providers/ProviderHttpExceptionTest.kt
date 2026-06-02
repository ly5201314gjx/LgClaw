package com.lgclaw.providers

import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHttpExceptionTest {
    @Test
    fun `http 401 can try next endpoint candidate`() {
        val error = ProviderHttpException(
            providerLabel = "Custom API",
            statusCode = 401,
            responseBody = """{"error":{"message":"Authentication Error"}}"""
        )

        assertTrue(error.isRetryableCandidateFailure)
    }

    @Test
    fun `query engine 401 is treated as authentication recovery failure`() {
        val error = ProviderHttpException(
            providerLabel = "Custom API",
            statusCode = 401,
            responseBody = """{"error":{"message":"Authentication Error, Client is not connected to the query engine, you must call connect() before attempting to query data.","type":"auth_error"}}"""
        )

        assertTrue(error.isAuthenticationRecoveryFailure)
    }
}
