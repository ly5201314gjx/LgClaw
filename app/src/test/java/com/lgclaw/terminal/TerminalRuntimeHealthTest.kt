package com.lgclaw.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TerminalRuntimeHealthTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `bash reports missing readline before command execution`() {
        val prefix = tempFolder.newFolder("usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "lib").mkdirs()
        File(prefix, "bin/bash").writeText("#!/bin/sh\n")

        val report = TerminalRuntimeHealth.inspect(prefix)

        assertEquals(listOf("libreadline.so.8"), report.missingLibraries)
        assertTrue(report.errorMessage.contains("libreadline.so.8"))
    }

    @Test
    fun `bash is healthy when readline exists`() {
        val prefix = tempFolder.newFolder("usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "lib").mkdirs()
        File(prefix, "bin/bash").writeText("#!/bin/sh\n")
        File(prefix, "lib/libreadline.so.8").writeText("")

        val report = TerminalRuntimeHealth.inspect(prefix)

        assertTrue(report.missingLibraries.isEmpty())
        assertEquals("", report.errorMessage)
    }
}
