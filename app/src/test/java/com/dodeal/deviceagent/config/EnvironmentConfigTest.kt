package com.dodeal.deviceagent.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * STAGING logs full request and response bodies — bearer token on every call, password on login.
 * So the question "what happens when the environment name is missing or wrong?" is a privacy
 * question, not a configuration detail, and it must answer PRODUCTION.
 */
class EnvironmentConfigTest {

    @Test
    fun `an unknown environment falls back to the quiet one`() {
        assertEquals(Environment.PRODUCTION, EnvironmentConfig.fromName(null))
        assertEquals(Environment.PRODUCTION, EnvironmentConfig.fromName(""))
        assertEquals(Environment.PRODUCTION, EnvironmentConfig.fromName("stagng"))
    }

    @Test
    fun `a named environment is still honoured, whatever its case`() {
        assertEquals(Environment.STAGING, EnvironmentConfig.fromName("STAGING"))
        assertEquals(Environment.STAGING, EnvironmentConfig.fromName("staging"))
        assertEquals(Environment.PRODUCTION, EnvironmentConfig.fromName("production"))
    }

    @Test
    fun `production never logs bodies`() {
        val production = EnvironmentConfig.forEnvironment(Environment.PRODUCTION)

        assertEquals(LoggingLevel.BASIC, production.loggingLevel)
    }
}
