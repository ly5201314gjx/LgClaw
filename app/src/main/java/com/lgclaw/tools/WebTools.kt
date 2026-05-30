package com.lgclaw.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.min

fun createWebToolSet(client: OkHttpClient, context: Context): List<Tool> {
    return listOf(
        WebSearchTool(client),
        BrowserSearchTool(context),
        WebFetchTool(client)
    )
}

internal enum class WebSearchEngine(val id: String, val labelZh: String) {
    Auto("auto", "自动"),
    DuckDuckGoHtml("duckduckgo_html", "DuckDuckGo"),
    DuckDuckGoLite("duckduckgo_lite", "DuckDuckGo 简版"),
    Mojeek("mojeek", "Mojeek"),
    BingRss("bing_rss", "Bing RSS"),
    Wikipedia("wikipedia", "维基百科"),
    StackExchange("stackexchange", "StackExchange"),
    Browser("browser", "手机浏览器搜索");

    companion object {
        fun from(raw: String?): WebSearchEngine {
            val clean = raw.orEmpty().trim().lowercase(Locale.US).replace('-', '_')
            return when (clean) {
                "", "auto", "自动" -> Auto
                "duckduckgo", "ddg", "duckduckgo_html" -> DuckDuckGoHtml
                "duckduckgo_lite", "ddg_lite", "lite", "简版" -> DuckDuckGoLite
                "mojeek" -> Mojeek
                "bing", "bing_rss", "rss", "必应" -> BingRss
                "wiki", "wikipedia", "维基", "维基百科" -> Wikipedia
                "stack", "stackoverflow", "stack_overflow", "stackexchange" -> StackExchange
                "browser", "phone_browser", "手机浏览器", "浏览器" -> Browser
                else -> Auto
            }
        }
    }
}

private class WebSearchTool(private val client: OkHttpClient) : Tool {
    override val name: String = "web_search"
    override val description: String = "搜索网页并返回标题、链接和摘要，支持多个免费搜索源和自动降级。"

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("[\"query\"]"))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "query":{"type":"string","description":"搜索关键词"},
                  "count":{"type":"integer","minimum":1,"maximum":10},
                  "engine":{"type":"string","enum":["auto","duckduckgo_html","duckduckgo_lite","mojeek","bing_rss","wikipedia","stackexchange","browser"],"description":"搜索源，默认 auto"},
                  "fallback":{"type":"boolean","description":"失败或无结果时是否自动降级，默认 true"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = Json.decodeFromString<Args>(argumentsJson)
        val query = args.query.trim()
        if (query.isBlank()) return@withContext error("搜索失败：关键词为空")
        val count = (args.count ?: DEFAULT_COUNT).coerceIn(1, 10)
        val engine = WebSearchEngine.from(args.engine)
        val fallback = args.fallback ?: true
        val attempts = mutableListOf<SearchAttempt>()

        for (candidate in searchPlan(engine, fallback)) {
            if (candidate == WebSearchEngine.Browser) {
                val item = SearchItem(
                    title = "在手机浏览器中搜索",
                    url = buildBrowserSearchUrl(query, WebSearchEngine.DuckDuckGoHtml),
                    snippet = "当前无界面搜索源未返回结果，可调用 browser_search 打开真实浏览器继续搜索。"
                )
                return@withContext searchResult(query, listOf(item), attempts, candidate, attempts.isNotEmpty())
            }
            val attempt = runCatching { searchEngine(candidate, query, count) }
                .getOrElse { SearchAttempt(candidate, emptyList(), it.message ?: it.javaClass.simpleName) }
            attempts += attempt
            if (attempt.items.isNotEmpty()) {
                return@withContext searchResult(query, attempt.items.take(count), attempts, candidate, attempts.size > 1)
            }
            if (!fallback) break
        }

        val failure = attempts.joinToString("；") { "${it.engine.labelZh}: ${it.error.ifBlank { "无结果" }}" }
        ToolResult(
            toolCallId = "",
            content = buildString {
                appendLine("搜索无结果：$query")
                appendLine("失败链路：${failure.ifBlank { "没有可用搜索源" }}")
                appendLine("可改用 browser_search 打开手机真实浏览器搜索。")
            }.trimEnd(),
            isError = false,
            metadata = buildJsonObject {
                put("query", query)
                put("source", "none")
                put("count", 0)
                put("fallback", attempts.size > 1)
                put("failure", failure)
            }
        )
    }

    private fun searchPlan(engine: WebSearchEngine, fallback: Boolean): List<WebSearchEngine> {
        if (!fallback && engine != WebSearchEngine.Auto) return listOf(engine)
        val fallbackChain = listOf(
            WebSearchEngine.BingRss,
            WebSearchEngine.DuckDuckGoHtml,
            WebSearchEngine.DuckDuckGoLite,
            WebSearchEngine.Mojeek,
            WebSearchEngine.Wikipedia,
            WebSearchEngine.StackExchange,
            WebSearchEngine.Browser
        )
        return when (engine) {
            WebSearchEngine.Auto -> fallbackChain
            WebSearchEngine.Browser -> listOf(WebSearchEngine.Browser)
            else -> listOf(engine) + fallbackChain.filter { it != engine }
        }
    }

    private fun searchEngine(engine: WebSearchEngine, query: String, count: Int): SearchAttempt {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = when (engine) {
            WebSearchEngine.DuckDuckGoHtml -> "https://duckduckgo.com/html/?q=$encoded"
            WebSearchEngine.DuckDuckGoLite -> "https://lite.duckduckgo.com/lite/?q=$encoded"
            WebSearchEngine.Mojeek -> "https://www.mojeek.com/search?q=$encoded"
            WebSearchEngine.BingRss -> "https://www.bing.com/search?format=rss&q=$encoded"
            WebSearchEngine.Wikipedia -> wikipediaSearchUrl(query, count)
            WebSearchEngine.StackExchange -> "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=$encoded&site=stackoverflow&pagesize=$count&filter=default"
            else -> return SearchAttempt(engine, emptyList(), "该搜索源只能通过浏览器打开")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.7,en;q=0.6")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return SearchAttempt(engine, emptyList(), "HTTP ${response.code}")
            val items = when (engine) {
                WebSearchEngine.DuckDuckGoHtml -> parseDuckHtmlResults(body, count)
                WebSearchEngine.DuckDuckGoLite -> parseDuckLiteResults(body, count)
                WebSearchEngine.Mojeek -> parseMojeekResults(body, count)
                WebSearchEngine.BingRss -> parseBingRssResults(body, count)
                WebSearchEngine.Wikipedia -> parseWikipediaResults(body, query, count)
                WebSearchEngine.StackExchange -> parseStackExchangeResults(body, count)
                else -> emptyList()
            }
            return SearchAttempt(engine, items, if (looksBlocked(body)) "页面可能被反爬或需要脚本渲染" else "")
        }
    }

    @Serializable
    private data class Args(
        val query: String,
        val count: Int? = null,
        val engine: String? = null,
        val fallback: Boolean? = null
    )
}

private class BrowserSearchTool(private val context: Context) : Tool {
    override val name: String = "browser_search"
    override val description: String = "调用手机真实浏览器搜索关键词，适合内置无界面搜索失败或需要查看动态网页时使用。"

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("[\"query\"]"))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "query":{"type":"string","description":"搜索关键词"},
                  "engine":{"type":"string","enum":["duckduckgo_html","duckduckgo_lite","mojeek","bing_rss","wikipedia","stackexchange","google","bing","baidu"],"description":"要打开的搜索引擎，默认 duckduckgo_html"}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.Main) {
        val args = Json.decodeFromString<Args>(argumentsJson)
        val query = args.query.trim()
        if (query.isBlank()) return@withContext error("浏览器搜索失败：关键词为空")
        val url = buildBrowserSearchUrl(query, WebSearchEngine.from(args.engine), args.engine)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return@withContext ToolResult(
                toolCallId = "",
                content = "浏览器搜索失败：手机上没有可处理网页链接的应用。\n目标链接：$url\n建议：安装或启用系统浏览器后重试。",
                isError = true,
                metadata = buildJsonObject {
                    put("opened", false)
                    put("url", url)
                    put("reason", "no_browser")
                }
            )
        }
        runCatching { context.startActivity(intent) }.fold(
            onSuccess = {
                ToolResult(
                    toolCallId = "",
                    content = "已打开手机浏览器搜索：$query\n$url",
                    isError = false,
                    metadata = buildJsonObject {
                        put("opened", true)
                        put("url", url)
                        put("engine", args.engine ?: "duckduckgo_html")
                    }
                )
            },
            onFailure = { t ->
                ToolResult(
                    toolCallId = "",
                    content = "浏览器搜索启动失败：${t.message ?: t.javaClass.simpleName}\n目标链接：$url",
                    isError = true,
                    metadata = buildJsonObject {
                        put("opened", false)
                        put("url", url)
                        put("reason", t.javaClass.simpleName)
                    }
                )
            }
        )
    }

    @Serializable
    private data class Args(val query: String, val engine: String? = null)
}

private class WebFetchTool(private val client: OkHttpClient) : Tool {
    override val name: String = "web_fetch"
    override val description: String = "抓取 URL 并提取可读正文，支持 text/markdown 输出。"

    override val jsonSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", Json.parseToJsonElement("[\"url\"]"))
        put(
            "properties",
            Json.parseToJsonElement(
                """
                {
                  "url":{"type":"string","description":"要抓取的 URL"},
                  "extractMode":{"type":"string","enum":["markdown","text"]},
                  "maxChars":{"type":"integer","minimum":100}
                }
                """.trimIndent()
            )
        )
    }

    override suspend fun run(argumentsJson: String): ToolResult = withContext(Dispatchers.IO) {
        val args = Json.decodeFromString<Args>(argumentsJson)
        val url = args.url.trim()
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || (parsed.scheme.lowercase(Locale.US) !in setOf("http", "https"))) {
            return@withContext error("网页抓取失败：只允许 http/https URL")
        }
        val extractMode = (args.extractMode ?: "markdown").lowercase(Locale.US)
        if (extractMode != "markdown" && extractMode != "text") {
            return@withContext error("网页抓取失败：extractMode 必须是 markdown 或 text")
        }
        val maxChars = (args.maxChars ?: DEFAULT_MAX_FETCH_CHARS).coerceIn(100, MAX_FETCH_CHARS)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.7,en;q=0.6")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use error("网页抓取失败：HTTP ${response.code}")
                val ctype = response.header("Content-Type").orEmpty()
                val finalUrl = response.request.url.toString()
                val extracted = normalize(
                    when {
                        ctype.contains("application/json", ignoreCase = true) -> prettyJsonOrRaw(body)
                        ctype.contains("text/html", ignoreCase = true) || looksLikeHtml(body) -> {
                            val title = extractTitle(body)
                            val content = if (extractMode == "markdown") htmlToMarkdown(body) else htmlToText(body)
                            if (title.isBlank()) content else "# $title\n\n$content"
                        }
                        else -> body
                    }
                )
                val truncated = extracted.length > maxChars
                val text = if (truncated) extracted.take(maxChars) + "\n...[已截断]" else extracted
                ToolResult(
                    toolCallId = "",
                    content = buildString {
                        appendLine("url=$url")
                        appendLine("finalUrl=$finalUrl")
                        appendLine("status=${response.code}")
                        appendLine("extractMode=$extractMode")
                        appendLine("truncated=$truncated")
                        appendLine("length=${min(extracted.length, maxChars)}")
                        appendLine()
                        appendLine(text.ifBlank { "(空内容)" })
                    }.trimEnd(),
                    isError = false,
                    metadata = buildJsonObject {
                        put("status", response.code)
                        put("final_url", finalUrl)
                        put("extract_mode", extractMode)
                        put("truncated", truncated)
                    }
                )
            }
        }.getOrElse { t -> error("网页抓取失败：${t.message ?: t.javaClass.simpleName}") }
    }

    private fun prettyJsonOrRaw(raw: String): String = runCatching { Json.parseToJsonElement(raw).toString() }.getOrElse { raw }

    private fun extractTitle(html: String): String {
        val m = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.getOrNull(1).orEmpty()
        return normalize(stripTags(m))
    }

    private fun htmlToText(html: String): String {
        val noScript = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        return normalize(stripTags(noScript))
    }

    private fun htmlToMarkdown(html: String): String {
        var text = html
        text = Regex("<a\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE).replace(text) { mr ->
            val href = mr.groupValues.getOrElse(1) { "" }
            val title = normalize(stripTags(mr.groupValues.getOrElse(2) { "" }))
            if (title.isBlank()) "" else "[$title]($href)"
        }
        text = Regex("<h([1-6])[^>]*>([\\s\\S]*?)</h\\1>", RegexOption.IGNORE_CASE).replace(text) { mr ->
            val level = mr.groupValues.getOrElse(1) { "1" }.toIntOrNull()?.coerceIn(1, 6) ?: 1
            val title = normalize(stripTags(mr.groupValues.getOrElse(2) { "" }))
            "\n${"#".repeat(level)} $title\n"
        }
        text = Regex("<li[^>]*>([\\s\\S]*?)</li>", RegexOption.IGNORE_CASE).replace(text) { mr -> "\n- ${normalize(stripTags(mr.groupValues.getOrElse(1) { "" }))}" }
        text = Regex("</(p|div|section|article)>", RegexOption.IGNORE_CASE).replace(text, "\n\n")
        text = Regex("<(br|hr)\\s*/?>", RegexOption.IGNORE_CASE).replace(text, "\n")
        return normalize(stripTags(text))
    }

    @Serializable
    private data class Args(val url: String, val extractMode: String? = null, val maxChars: Int? = null)
}

private data class SearchAttempt(val engine: WebSearchEngine, val items: List<SearchItem>, val error: String = "")

internal data class SearchItem(val title: String, val url: String, val snippet: String)

internal fun parseDuckHtmlResults(html: String, limit: Int): List<SearchItem> {
    val regex = Regex("""<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>([\s\S]*?)(?=<a[^>]*class=["'][^"']*result__a|$)""", RegexOption.IGNORE_CASE)
    val snippetRegex = Regex("""<(?:a|div)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div)>""", RegexOption.IGNORE_CASE)
    return regex.findAll(html).mapNotNull { m ->
        val title = normalize(stripTags(m.groupValues.getOrNull(2).orEmpty()))
        val url = normalizeDuckResultUrl(m.groupValues.getOrNull(1).orEmpty())
        if (title.isBlank() || url.isBlank()) return@mapNotNull null
        val snippet = normalize(stripTags(snippetRegex.find(m.groupValues.getOrNull(3).orEmpty())?.groupValues?.getOrNull(1).orEmpty()))
        SearchItem(title, url, snippet)
    }.take(limit).toList()
}

internal fun parseDuckLiteResults(html: String, limit: Int): List<SearchItem> {
    val linkRegex = Regex("""<a[^>]*class=["']result-link["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    val fallbackRegex = Regex("""<a[^>]*href=["'](/l/\?kh=-1&amp;uddg=[^"']+|https?://[^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    val regex = if (linkRegex.containsMatchIn(html)) linkRegex else fallbackRegex
    return regex.findAll(html).mapNotNull { m ->
        val title = normalize(stripTags(m.groupValues.getOrNull(2).orEmpty()))
        val url = normalizeDuckResultUrl(m.groupValues.getOrNull(1).orEmpty())
        if (title.isBlank() || url.isBlank() || title.equals("next page", true)) null else SearchItem(title, url, "")
    }.distinctBy { it.url }.take(limit).toList()
}

internal fun parseMojeekResults(html: String, limit: Int): List<SearchItem> {
    val blockRegex = Regex("""<li[^>]*class=["'][^"']*(?:result|results-standard__item)[^"']*["'][^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE)
    val linkRegex = Regex("""<a[^>]*href=["'](https?://[^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
    val snippetRegex = Regex("""<(?:p|div)[^>]*class=["'][^"']*(?:s|snippet|desc)[^"']*["'][^>]*>(.*?)</(?:p|div)>""", RegexOption.IGNORE_CASE)
    val blocks = blockRegex.findAll(html).map { it.groupValues[1] }.toList().ifEmpty { listOf(html) }
    return blocks.asSequence().mapNotNull { block ->
        val link = linkRegex.find(block) ?: return@mapNotNull null
        val title = normalize(stripTags(link.groupValues.getOrNull(2).orEmpty()))
        val url = htmlUnescape(link.groupValues.getOrNull(1).orEmpty())
        if (title.isBlank() || url.isBlank() || url.contains("mojeek.com", ignoreCase = true)) return@mapNotNull null
        val snippet = normalize(stripTags(snippetRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()))
        SearchItem(title, url, snippet)
    }.distinctBy { it.url }.take(limit).toList()
}


internal fun parseBingRssResults(raw: String, limit: Int): List<SearchItem> {
    val itemRegex = Regex("""<item\b[^>]*>([\s\S]*?)</item>""", RegexOption.IGNORE_CASE)
    fun tag(block: String, name: String): String {
        val match = Regex("""<$name\b[^>]*>([\s\S]*?)</$name>""", RegexOption.IGNORE_CASE).find(block)
        return htmlUnescape(match?.groupValues?.getOrNull(1).orEmpty()).trim()
    }
    return itemRegex.findAll(raw).mapNotNull { match ->
        val block = match.groupValues.getOrNull(1).orEmpty()
        val title = normalize(stripTags(tag(block, "title")))
        val link = tag(block, "link")
        val snippet = normalize(stripTags(tag(block, "description")))
        if (title.isBlank() || !link.startsWith("http", ignoreCase = true)) null else SearchItem(title, link, snippet)
    }.distinctBy { it.url }.take(limit).toList()
}
internal fun parseWikipediaResults(raw: String, query: String, limit: Int): List<SearchItem> = runCatching {
    val root = Json.parseToJsonElement(raw).jsonObject
    val search = root["query"]?.jsonObject?.get("search")?.jsonArray ?: JsonArray(emptyList())
    search.mapNotNull { item: JsonElement ->
        val obj = item.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val snippet = normalize(stripTags(obj["snippet"]?.jsonPrimitive?.content.orEmpty()))
        val url = "https://${wikipediaHost(query)}/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")}" 
        SearchItem(title, url, snippet)
    }.take(limit)
}.getOrDefault(emptyList())

internal fun parseStackExchangeResults(raw: String, limit: Int): List<SearchItem> = runCatching {
    val root = Json.parseToJsonElement(raw).jsonObject
    val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
    items.mapNotNull { item ->
        val obj = item.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content?.let(::htmlUnescape)?.trim().orEmpty()
        val url = obj["link"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank() || url.isBlank()) return@mapNotNull null
        val score = obj["score"]?.jsonPrimitive?.content?.trim().orEmpty()
        val answers = obj["answer_count"]?.jsonPrimitive?.content?.trim().orEmpty()
        SearchItem(title, url, "评分 $score，回答 $answers")
    }.take(limit)
}.getOrDefault(emptyList())

internal fun buildBrowserSearchUrl(query: String, engine: WebSearchEngine, rawEngine: String? = null): String {
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    return when (rawEngine.orEmpty().trim().lowercase(Locale.US).replace('-', '_')) {
        "google" -> "https://www.google.com/search?q=$encoded"
        "bing" -> "https://www.bing.com/search?q=$encoded"
        "baidu", "百度" -> "https://www.baidu.com/s?wd=$encoded"
        else -> when (engine) {
            WebSearchEngine.DuckDuckGoLite -> "https://lite.duckduckgo.com/lite/?q=$encoded"
            WebSearchEngine.Mojeek -> "https://www.mojeek.com/search?q=$encoded"
            WebSearchEngine.BingRss -> "https://www.bing.com/search?q=$encoded"
            WebSearchEngine.Wikipedia -> "https://${wikipediaHost(query)}/w/index.php?search=$encoded"
            WebSearchEngine.StackExchange -> "https://stackexchange.com/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }
}

private fun searchResult(query: String, items: List<SearchItem>, attempts: List<SearchAttempt>, source: WebSearchEngine, fallbackUsed: Boolean): ToolResult {
    val attemptText = attempts.joinToString(" -> ") { attempt ->
        if (attempt.items.isNotEmpty()) "${attempt.engine.labelZh}(${attempt.items.size})" else "${attempt.engine.labelZh}(失败:${attempt.error.ifBlank { "无结果" }})"
    }
    return ToolResult(
        toolCallId = "",
        content = buildString {
            appendLine("搜索：$query")
            appendLine("来源：${source.labelZh}")
            appendLine("降级：${if (fallbackUsed) "是" else "否"}")
            if (attemptText.isNotBlank()) appendLine("链路：$attemptText")
            appendLine()
            items.forEachIndexed { index, item ->
                appendLine("${index + 1}. ${item.title}")
                appendLine("   ${item.url}")
                if (item.snippet.isNotBlank()) appendLine("   ${item.snippet}")
            }
        }.trimEnd(),
        isError = false,
        metadata = buildJsonObject {
            put("query", query)
            put("source", source.id)
            put("source_label", source.labelZh)
            put("count", items.size)
            put("fallback", fallbackUsed)
            put("attempts", attemptText)
        }
    )
}

private fun wikipediaSearchUrl(query: String, count: Int): String {
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "https://${wikipediaHost(query)}/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=$count"
}

private fun wikipediaHost(query: String): String {
    return if (query.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) "zh.wikipedia.org" else "en.wikipedia.org"
}

private fun normalizeDuckResultUrl(rawUrl: String): String {
    if (rawUrl.isBlank()) return ""
    val decodedRaw = htmlUnescape(rawUrl)
    val url = when {
        decodedRaw.startsWith("//") -> "https:$decodedRaw"
        decodedRaw.startsWith("/l/") -> "https://duckduckgo.com$decodedRaw"
        else -> decodedRaw
    }
    val parsed = url.toHttpUrlOrNull() ?: return url
    val uddg = parsed.queryParameter("uddg")
    return if (!uddg.isNullOrBlank()) runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(uddg) else url
}

private fun looksBlocked(text: String): Boolean {
    val head = text.take(600).lowercase(Locale.US)
    return "captcha" in head || "unusual traffic" in head || "enable javascript" in head || "机器人" in head
}

private fun looksLikeHtml(text: String): Boolean {
    val head = text.trimStart().take(200).lowercase(Locale.US)
    return head.startsWith("<!doctype html") || head.startsWith("<html")
}

private fun stripTags(text: String): String = text.replace(Regex("<[^>]+>"), " ").let(::htmlUnescape)

private fun normalize(text: String): String = text
    .replace(Regex("[ \t]+"), " ")
    .replace("\r", "\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

private fun htmlUnescape(text: String): String = text
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'")
    .replace("&#x2F;", "/")

private fun error(content: String): ToolResult = ToolResult(toolCallId = "", content = content, isError = true)

private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; LGClaw) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
private const val DEFAULT_COUNT = 5
private const val DEFAULT_MAX_FETCH_CHARS = 50_000
private const val MAX_FETCH_CHARS = 200_000



