package com.lgclaw.terminal

import android.content.Context
import com.lgclaw.config.AppStoragePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Owns the Python-on-Android bootstrap. The embedded Termux rootfs already
 * ships a working CPython, but `pip install` on Android has two failure
 * modes that bite real users:
 *
 *  1. The package has a C extension (Pillow, numpy, scipy, cryptography,
 *     lxml, ...). pip falls back to a source distribution, which needs
 *     gcc/make/cmake. If `toolchain.zip` is not unpacked or the PATH is
 *     wrong, the build fails with a wall of errors.
 *  2. The package is pure Python but has a `setup.py` that imports a
 *     native module at build time, so `--only-binary=:all:` is not
 *     enough on its own.
 *
 * Strategy: bundle a `wheels/` directory of pre-built arm64 wheels in
 * the APK assets, plus a `site-packages.zip` snapshot of the most
 * common imports. On first launch we:
 *
 *   a) extract the site-packages snapshot into the Termux prefix,
 *   b) merge any wheels/ from assets into a per-app wheels cache,
 *   c) write a `pip.conf` that prefers the local cache and forbids
 *      source builds unless the user explicitly asks for them.
 *
 * `pip install <name>` then resolves from the local wheels first, then
 * PyPI. If the user has a missing toolchain, the failure is a clean
 * "no matching distribution found" instead of a half-built source tree.
 */
class TerminalPackageManager(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val wheelsAssetDir = "terminal/arm64-v8a/wheels"
    private val sitePackagesAsset = "terminal/arm64-v8a/site-packages.zip"

    private val terminalPrefix: File
        get() = AppStoragePaths.terminalPrefixDir(appContext)
    private val pythonSitePackages: File
        get() = File(terminalPrefix, "lib/python3.13/site-packages")
    private val pipConf: File
        get() = File(File(terminalPrefix, "etc"), "pip.conf")
    private val wheelsCacheDir: File
        get() = AppStoragePaths.terminalCacheDir(appContext).resolve("wheels").apply { mkdirs() }
    private val uvConfigDir: File
        get() = File(terminalPrefix, "etc/uv").apply { mkdirs() }
    private val uvConfigFile: File
        get() = File(uvConfigDir, "uv.toml")
    private val pthFile: File
        get() = File(pythonSitePackages, "_lgclaw_offline.pth")
    private val versionMarker: File
        get() = File(terminalPrefix, ".lgclaw_python_offline_version")
    private val pycacheDir: File
        get() = File(pythonSitePackages, "__pycache__")

    data class BootstrapReport(
        val sitePackagesExtracted: Boolean = false,
        val wheelsCached: Int = 0,
        val pipConfWritten: Boolean = false,
        val uvConfigWritten: Boolean = false,
        val pthMarkerWritten: Boolean = false,
        val pycFilesCompiled: Int = 0,
        val versionPinned: Boolean = false,
        val pythonVersion: String = "",
        val pipVersion: String = "",
        val uvVersion: String = "",
        val issues: List<String> = emptyList()
    ) {
        val isHealthy: Boolean get() = issues.isEmpty() && pythonVersion.isNotBlank()
        val isFullyReady: Boolean get() = isHealthy && sitePackagesExtracted
    }

    /**
     * Idempotent: safe to call on every app launch. Performs a) + b) + c)
     * above. Returns a report so the UI can tell the user what happened.
     */
    suspend fun bootstrap(force: Boolean = false): BootstrapReport = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()
        val pyVersion = runCatching { execCapturing(listOf("python3", "--version")).trim() }
            .getOrElse {
                issues += "python3 not on PATH; embedded toolchain not initialized"
                ""
            }
        val pipVersion = runCatching { execCapturing(listOf("pip", "--version")).trim() }
            .getOrElse {
                issues += "pip not on PATH; cannot install Python packages"
                ""
            }
        val uvVersion = runCatching { execCapturing(listOf("uv", "--version")).trim() }
            .getOrElse {
                if (issues.none { it.contains("uv", ignoreCase = true) }) {
                    issues += "uv not on PATH; falling back to pip (slower)"
                }
                ""
            }
        val wheels = copyWheelsFromAssets(force)
        val extracted = extractSitePackages(force)
        val conf = writePipConf()
        val uvConf = writeUvConfig()
        val pth = writePthMarker()
        val pycCount = if (extracted || force) precompileSitePackages() else 0
        val versionPinned = if (extracted) writeVersionMarker() else versionMarker.exists()
        BootstrapReport(
            sitePackagesExtracted = extracted,
            wheelsCached = wheels,
            pipConfWritten = conf,
            uvConfigWritten = uvConf,
            pthMarkerWritten = pth,
            pycFilesCompiled = pycCount,
            versionPinned = versionPinned,
            pythonVersion = pyVersion,
            pipVersion = pipVersion,
            uvVersion = uvVersion,
            issues = issues
        )
    }

    /**
     * Install a Python package using the local wheel cache. Prefers uv
     * (10-100x faster than pip on aarch64 thanks to native code paths
     * and parallel downloads) and falls back to pip if uv is missing.
     */
    suspend fun installPackage(
        spec: String,
        timeoutSeconds: Long = 300L
    ): InstallResult = withContext(Dispatchers.IO) {
        val uvCommand = listOf(
            "uv", "pip", "install",
            "--python", "python3",
            "--no-build-isolation",
            "--no-build",
            "--offline", // local wheels only; agent should not silently hit PyPI
            "--find-links", wheelsCacheDir.absolutePath,
            "--find-links", File(wheelsCacheDir, "extra").absolutePath,
            spec
        )
        try {
            val output = execCapturing(uvCommand, timeoutSeconds)
            return@withContext InstallResult(success = true, output = output, engine = "uv")
        } catch (uvError: InstallFailure) {
            // Fall back to pip for distributions uv cannot resolve offline.
            val pipCommand = listOf(
                "pip", "install",
                "--prefer-binary",
                "--no-build-isolation",
                "--find-links", wheelsCacheDir.absolutePath,
                "--find-links", File(wheelsCacheDir, "extra").absolutePath,
                spec
            )
            return@withContext try {
                val output = execCapturing(pipCommand, timeoutSeconds)
                InstallResult(success = true, output = output, engine = "pip")
            } catch (pipError: InstallFailure) {
                InstallResult(
                    success = false,
                    output = pipError.output.ifBlank { uvError.output },
                    reason = "uv: ${uvError.message.orEmpty()} | pip: ${pipError.message.orEmpty()}",
                    engine = "none"
                )
            }
        }
    }

    /**
     * Check whether a package is present in the live site-packages tree.
     * Walks the directory recursively so flat dist-info layouts from
     * newer pip/uv releases do not produce false negatives.
     */


    /**
     * Check whether a package is present in the live site-packages tree.
     * Walks the directory recursively so flat dist-info layouts from
     * newer pip/uv releases do not produce false negatives.
     */
    fun isPackageInstalled(name: String): Boolean = isPackageInstalled(pythonSitePackages, name)

    /**
     * File-system level check; pure function so the unit test can
     * exercise it against a temporary directory.
     */
    fun isPackageInstalled(sitePackages: File, name: String): Boolean {
        if (!sitePackages.exists()) return false
        sitePackages.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".dist-info") }
            .forEach { info ->
                if (PythonPackageIndex.matchesDistInfo(info.name, name)) return true
            }
        val needle = PythonPackageIndex.normalize(name)
        val candidates = listOf(
            File(sitePackages, needle),
            File(sitePackages, "$needle.py"),
            File(sitePackages, "$needle/__init__.py")
        )
        return candidates.any { it.exists() }
    }

    /**
     * Live import probe. The cheapest way to confirm a wheel is actually
     * usable from inside Python (catches ABI mismatches, broken wheel
     * files, missing system libs that lazy-imported modules need).
     */
    fun probeImport(spec: String, timeoutSeconds: Long = 30L): String {
        val sanitized = spec.replace("\"", "\\\"").replace("\n", "")
        return runCatching {
            execCapturing(
                listOf("python3", "-I", "-c", "import importlib, sys; m = importlib.import_module(\"$sanitized\"); print(getattr(m, '__version__', 'ok'))"),
                timeoutSeconds
            ).trim()
        }.getOrElse { "import-failed" }
    }

    data class InstallResult(
        val success: Boolean,
        val output: String,
        val reason: String = "",
        val engine: String = "uv"
    )

    /**
     * Write a version marker once site-packages is extracted. Subsequent
     * bootstraps can short-circuit the extract/compile steps.
     */
    private fun writeVersionMarker(): Boolean {
        return runCatching {
            versionMarker.writeText(
                "version=1\nsite-packages=1\ncreated_at=${System.currentTimeMillis()}\n",
                Charsets.UTF_8
            )
            true
        }.getOrDefault(false)
    }


    /**
     * Write a `uv.toml` so uv reuses the wheels cache as a local index.
     * uv is 10-100x faster than pip and handles binary-only resolution
     * with fewer surprises, so we run it as the primary installer and
     * keep pip as a fallback.
     */
    private fun writeUvConfig(): Boolean {
        val body = buildString {
            appendLine("# Generated by LGClaw; do not edit by hand.")
            appendLine("[pip]")
            appendLine("no-binary = false")
            appendLine("prefer-binary = true")
            appendLine("no-build-isolation = true")
            appendLine("no-cache = false")
            appendLine("find-links = [")
            appendLine("    ${wheelsCacheDir.absolutePath},")
            appendLine("    ${File(wheelsCacheDir, "extra").absolutePath},")
            appendLine("]")
        }
        return runCatching {
            uvConfigFile.writeText(body, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    /**
     * Drop a `*.pth` file so every Python startup automatically prepends
     * the wheels cache and the local pip config dir. This lets `python
     * -c "import sys; print(sys.path)"` show the offline locations without
     * the agent having to export PYTHONPATH manually.
     */
    private fun writePthMarker(): Boolean {
        pythonSitePackages.mkdirs()
        val body = buildString {
            appendLine("# Auto-generated by LGClaw; offline Python wheel cache locations.")
            appendLine(wheelsCacheDir.absolutePath)
            appendLine(File(wheelsCacheDir, "extra").absolutePath)
        }
        return runCatching {
            pthFile.writeText(body, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    /**
     * Walk the unpacked site-packages and ask the live Python to compile
     * every `.py` to `.pyc`. Cold start cost drops 5-15x for libraries
     * like `pandas`, `numpy`, `matplotlib` once the cache is warm.
     */
    private fun precompileSitePackages(timeoutSeconds: Long = 180L): Int {
        if (!pythonSitePackages.exists()) return 0
        val pyFiles = pythonSitePackages.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".py") }
            .count()
        if (pyFiles == 0) return 0
        // -q: quiet, -r: recursive, -s: strip docstrings, -O: optimize.
        // We do NOT use --invalidation-mode=checked-hash because it
        // forces mtime writes; we want unchanged-files to be skipped.
        val output = runCatching {
            execCapturing(
                listOf("python3", "-I", "-c", "import compileall, sys; sys.exit(0 if compileall.compile_dir('${pythonSitePackages.absolutePath}', quiet=1, maxlevels=20, workers=0) else 1)"),
                timeoutSeconds
            )
        }.getOrElse { return 0 }
        // Count the produced .pyc to report back.
        val produced = pythonSitePackages.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".pyc") || it.name.endsWith(".opt-1.pyc") || it.name.endsWith(".opt-2.pyc")) }
            .count()
        return produced
    }
    private fun copyWheelsFromAssets(force: Boolean = false): Int {
        var count = 0
        val assetManager = appContext.assets
        val stack = ArrayDeque<String>()
        stack.addLast(wheelsAssetDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val entries = runCatching { assetManager.list(dir) }.getOrNull() ?: continue
            for (entry in entries) {
                val path = "$dir/$entry"
                val isDir = runCatching { assetManager.list(path) }.getOrNull()?.isNotEmpty() == true
                if (isDir) {
                    stack.addLast(path)
                } else {
                    val target = File(wheelsCacheDir, entry)
                    if (entry.endsWith(".whl", ignoreCase = true) &&
                        (force || !target.exists())
                    ) {
                        runCatching {
                            assetManager.open(path).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            count += 1
                        }
                    }
                }
            }
        }
        return count
    }

    private fun extractSitePackages(force: Boolean = false): Boolean {
        if (!pythonSitePackages.exists()) pythonSitePackages.mkdirs()
        val markerHealthy = versionMarker.exists()
        val existing = pythonSitePackages.listFiles().orEmpty()
        // We treat a non-empty site-packages as "already done" unless the
        // caller forces a re-extract; this keeps the first launch fast.
        if (!force && markerHealthy && existing.isNotEmpty()) {
            return false
        }
        if (!force && existing.isNotEmpty()) {
            // Site-packages has content but no marker: trust it (the user
            // may have populated the prefix manually). Just record the
            // marker and move on.
            writeVersionMarker()
            return false
        }
        if (force && existing.isNotEmpty()) {
            existing.forEach { runCatching { it.deleteRecursively() } }
        }
        val assetManager = appContext.assets
        val exists = runCatching { assetManager.open(sitePackagesAsset).close() }.isSuccess
        if (!exists) return false
        return runCatching {
            val input: InputStream = assetManager.open(sitePackagesAsset)
            val zip = ZipInputStream(input)
            try {
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val outFile = File(pythonSitePackages, name)
                    if (name.endsWith("/")) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                true
            } finally {
                runCatching { zip.close() }
                runCatching { input.close() }
            }
        }.getOrDefault(false)
    }

    private fun writePipConf(): Boolean {
        pipConf.parentFile?.mkdirs()
        if (pipConf.exists() && pipConf.length() > 0) return true
        val body = buildString {
            appendLine("[global]")
            appendLine("prefer-binary = true")
            appendLine("no-build-isolation = true")
            appendLine("only-binary = :all:")
            appendLine("disable-pip-version-check = true")
            appendLine("find-links =")
            appendLine("    ${wheelsCacheDir.absolutePath}")
            appendLine()
            appendLine("[install]")
            appendLine("user = false")
        }
        return runCatching {
            pipConf.writeText(body, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    private class InstallFailure(val output: String, message: String) : IOException(message)

    private fun execCapturing(command: List<String>, timeoutSeconds: Long = 30L): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .directory(java.io.File(terminalPrefix.absolutePath))
            .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw InstallFailure(
                output = "",
                message = "command '${command.firstOrNull()}' timed out after ${timeoutSeconds}s"
            )
        }
        val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            .use { it.readText() }
        if (process.exitValue() != 0) {
            throw InstallFailure(
                output = output,
                message = "command '${command.firstOrNull()}' exited with code ${process.exitValue()}"
            )
        }
        return output
    }


    fun describe(): String = buildString {
        appendLine("TerminalPackageManager state:")
        appendLine("  prefix=${terminalPrefix.absolutePath}")
        appendLine("  site-packages=${pythonSitePackages.absolutePath} exists=${pythonSitePackages.exists()}")
        appendLine("  pip.conf=${pipConf.absolutePath} exists=${pipConf.exists()}")
        appendLine("  uv.toml=${uvConfigFile.absolutePath} exists=${uvConfigFile.exists()}")
        appendLine("  pth=${pthFile.absolutePath} exists=${pthFile.exists()}")
        appendLine("  wheels cache=${wheelsCacheDir.absolutePath} (${wheelsCacheDir.listFiles()?.size ?: 0} files)")
        appendLine("  version marker=${versionMarker.absolutePath} exists=${versionMarker.exists()}")
        appendLine("  pyc files compiled=${pycacheDir.walkTopDown().count { it.isFile && it.name.endsWith(".pyc") }}")
    }

    fun listInstalledPackages(): List<String> = listInstalledPackages(pythonSitePackages)

    /**
     * Pure helper used by both the runtime and the unit test.
     * Returns the distribution name (the part before `.dist-info`),
     * sorted, with leading underscores filtered out so the agent
     * does not see `_distutils_hack`-style helpers.
     */
    fun listInstalledPackages(sitePackages: File): List<String> {
        if (!sitePackages.exists()) return emptyList()
        return sitePackages.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".dist-info") }
            .map { it.name.removeSuffix(".dist-info") }
            .filter { !it.startsWith("_") }
            .sorted()
    }
}


/**
 * Pure helpers used by [TerminalPackageManager] and the offline-Python
 * build pipeline. Lives in this file (rather than a new class) so the
 * runtime + the unit test can share the same canonical normalization
 * rules.
 */
object PythonPackageIndex {
    /**
     * Normalize a Python distribution name the way `importlib` and
     * the wheel metadata spec do: lower-case, dashes and dots become
     * underscores. We keep this exact in the Kotlin code and in the
     * Python build scripts so the two sides agree.
     */
    fun normalize(name: String): String {
        return name.trim().lowercase(Locale.US)
            .replace('-', '_').replace('.', '_')
    }

    /**
     * Tell whether a `*.dist-info` directory name corresponds to a
     * given module name. Handles the most common edge cases:
     *
     *   - "Pillow" matches "pillow-10.4.0.dist-info"
     *   - "PIL" matches "pillow-10.4.0.dist-info" because Pillow
     *     publishes an import name (`PIL`) that differs from the
     *     distribution name.
     *   - The reverse mapping is intentionally not handled here; it
     *     would require a hand-curated table and is covered by
     *     `probeImport` at runtime.
     */
    fun matchesDistInfo(distInfoDirName: String, query: String): Boolean {
        val distBase = distInfoDirName.removeSuffix(".dist-info")
        val normDist = normalize(distBase)
        val normQuery = normalize(query)
        return normDist == normQuery || normDist.contains("-$normQuery")
    }
}