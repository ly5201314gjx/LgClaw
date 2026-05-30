package com.lgclaw.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolsTest {
    @Test
    fun parseDuckHtmlResultsExtractsRedirectUrlAndSnippet() {
        val html = """
            <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage">Example &amp; Title</a>
            <div class="result__snippet">A <b>useful</b> snippet.</div>
        """.trimIndent()

        val results = parseDuckHtmlResults(html, 5)

        assertEquals(1, results.size)
        assertEquals("Example & Title", results[0].title)
        assertEquals("https://example.com/page", results[0].url)
        assertEquals("A useful snippet.", results[0].snippet)
    }

    @Test
    fun parseDuckLiteResultsExtractsDirectLinks() {
        val html = """
            <a class="result-link" href="https://example.org/doc">Lite Result</a>
            <a class="result-link" href="https://example.org/doc">Lite Result</a>
        """.trimIndent()

        val results = parseDuckLiteResults(html, 5)

        assertEquals(1, results.size)
        assertEquals("Lite Result", results[0].title)
        assertEquals("https://example.org/doc", results[0].url)
    }


    @Test
    fun parseBingRssResultsExtractsItems() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <rss><channel>
                <item>
                    <title>Kotlin &amp; Android</title>
                    <link>https://example.com/kotlin</link>
                    <description>官方 <b>开发</b> 文档</description>
                </item>
            </channel></rss>
        """.trimIndent()

        val results = parseBingRssResults(xml, 5)

        assertEquals(1, results.size)
        assertEquals("Kotlin & Android", results[0].title)
        assertEquals("https://example.com/kotlin", results[0].url)
        assertEquals("官方 开发 文档", results[0].snippet)
    }
    @Test
    fun parseWikipediaResultsCreatesLanguageAwareUrl() {
        val json = """
            {"query":{"search":[{"title":"人工智能","snippet":"机器模拟智能"}]}}
        """.trimIndent()

        val results = parseWikipediaResults(json, "人工智能", 3)

        assertEquals(1, results.size)
        assertEquals("人工智能", results[0].title)
        assertTrue(results[0].url.startsWith("https://zh.wikipedia.org/wiki/"))
    }

    @Test
    fun parseStackExchangeResultsUsesTitleAndLink() {
        val json = """
            {"items":[{"title":"Kotlin &amp; Android","link":"https://stackoverflow.com/q/1","score":7,"answer_count":2}]}
        """.trimIndent()

        val results = parseStackExchangeResults(json, 5)

        assertEquals(1, results.size)
        assertEquals("Kotlin & Android", results[0].title)
        assertEquals("https://stackoverflow.com/q/1", results[0].url)
        assertEquals("评分 7，回答 2", results[0].snippet)
    }

    @Test
    fun buildBrowserSearchUrlSupportsCommonEngines() {
        assertTrue(buildBrowserSearchUrl("lgclaw", WebSearchEngine.DuckDuckGoHtml).contains("duckduckgo.com"))
        assertTrue(buildBrowserSearchUrl("lgclaw", WebSearchEngine.DuckDuckGoHtml, "google").contains("google.com/search"))
        assertTrue(buildBrowserSearchUrl("lgclaw", WebSearchEngine.DuckDuckGoHtml, "bing").contains("bing.com/search"))
        assertTrue(buildBrowserSearchUrl("lgclaw", WebSearchEngine.DuckDuckGoHtml, "baidu").contains("baidu.com/s"))
    }
}

