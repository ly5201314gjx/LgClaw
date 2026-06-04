package com.lgclaw.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the pure helpers in [TerminalPackageManager] against a
 * real (but throwaway) on-disk site-packages directory. The runtime
 * `isPackageInstalled(File)` and `listInstalledPackages(File)` are
 * the same code paths called by the Android side, so a green test
 * here means a green app.
 */
class TerminalPackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fakeDistInfo(parent: File, name: String) {
        // We only need the directory name to exist; pip's resolver
        // and our `walkTopDown` use the file name, not the contents.
        File(parent, "$name.dist-info").mkdirs()
    }

    @Test
    fun `normalize lowercases and translates dashes`() {
        assertEquals("pillow", PythonPackageIndex.normalize("Pillow"))
        assertEquals("pillow", PythonPackageIndex.normalize("pillow"))
        assertEquals("pillow", PythonPackageIndex.normalize("PIL-LOW"))
        assertEquals("zope_interface", PythonPackageIndex.normalize("zope.interface"))
        assertEquals("requests", PythonPackageIndex.normalize("Requests "))
    }

    @Test
    fun `matchesDistInfo accepts the exact normalized name`() {
        assertTrue(PythonPackageIndex.matchesDistInfo("requests-2.32.3.dist-info", "requests"))
        assertTrue(PythonPackageIndex.matchesDistInfo("requests-2.32.3.dist-info", "REQUESTS"))
        assertTrue(PythonPackageIndex.matchesDistInfo("pillow-10.4.0.dist-info", "pillow"))
    }

    @Test
    fun `matchesDistInfo rejects unrelated distributions`() {
        assertFalse(PythonPackageIndex.matchesDistInfo("urllib3-2.2.3.dist-info", "requests"))
        assertFalse(PythonPackageIndex.matchesDistInfo("matplotlib-3.10.0.dist-info", "pyecharts"))
    }

    @Test
    fun `isPackageInstalled finds dist-info layout`() {
        val site = tempFolder.newFolder("site-packages")
        fakeDistInfo(site, "requests-2.32.3")
        fakeDistInfo(site, "pillow-10.4.0")
        fakeDistInfo(site, "pyecharts-2.0.6")

        assertTrue(TerminalPackageManager.isPackageInstalled(site, "requests"))
        assertTrue(TerminalPackageManager.isPackageInstalled(site, "Pillow"))
        assertTrue(TerminalPackageManager.isPackageInstalled(site, "pyecharts"))
    }

    @Test
    fun `isPackageInstalled returns false for missing packages`() {
        val site = tempFolder.newFolder("site-packages")
        fakeDistInfo(site, "requests-2.32.3")

        assertFalse(TerminalPackageManager.isPackageInstalled(site, "matplotlib"))
        assertFalse(TerminalPackageManager.isPackageInstalled(site, "pandas"))
    }

    @Test
    fun `isPackageInstalled handles missing site-packages`() {
        val missing = File(tempFolder.root, "does-not-exist")
        assertFalse(TerminalPackageManager.isPackageInstalled(missing, "requests"))
    }

    @Test
    fun `isPackageInstalled falls back to module layout when dist-info is missing`() {
        // Some single-file modules ship without a .dist-info; we still
        // want a positive answer when the .py file is on disk.
        val site = tempFolder.newFolder("site-packages")
        File(site, "my_module.py").writeText("x = 1\n")
        assertTrue(TerminalPackageManager.isPackageInstalled(site, "my_module"))
    }

    @Test
    fun `listInstalledPackages returns sorted names without dist-info suffix`() {
        val site = tempFolder.newFolder("site-packages")
        fakeDistInfo(site, "pyecharts-2.0.6")
        fakeDistInfo(site, "matplotlib-3.10.0")
        fakeDistInfo(site, "_internal_helper-1.0")
        fakeDistInfo(site, "requests-2.32.3")

        val installed = TerminalPackageManager.listInstalledPackages(site)
        assertEquals(
            listOf("matplotlib-3.10.0", "pyecharts-2.0.6", "requests-2.32.3"),
            installed
        )
    }

    @Test
    fun `listInstalledPackages returns empty for missing directory`() {
        val missing = File(tempFolder.root, "no-such-site-packages")
        assertTrue(TerminalPackageManager.listInstalledPackages(missing).isEmpty())
    }
}
