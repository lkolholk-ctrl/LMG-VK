package com.mocharealm.accompanist.lyrics.core.parser

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.test.Test
import kotlin.test.assertEquals

class TTMLXmlTextTest {
    private fun parse(body: String, head: String = "") = TTMLParser().parse(
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal">$head<body><div>$body</div></body></tt>"""
    )

    @Test fun nestedWordTextKeepsDocumentOrderAndTiming() {
        val line = parse("""<p begin="1s" end="4s"><span begin="1s" end="2s">H<span>ell</span>o</span> <span begin="2.5s" end="4s">wo<span>rl</span>d!</span></p>""")
            .lines.single() as KaraokeLine
        assertEquals(listOf("Hello ", "world!"), line.syllables.map { it.content })
        assertEquals(listOf(1000 to 2000, 2500 to 4000), line.syllables.map { it.start to it.end })
    }

    @Test fun mixedPlainTextKeepsDocumentOrder() {
        val line = parse("""<p begin="1s" end="4s">Before <span>middle <span>nested</span></span> after</p>""")
            .lines.single() as SyncedLine
        assertEquals("Before middle nested after", line.content)
    }

    @Test fun entitiesDecodeOnceIncludingSupplementaryUnicode() {
        val text = "Don&#39;t &amp;lt; &#x1F3B5; &#127925; &quot;yes&quot; &apos;ok&apos;"
        val line = parse("""<p begin="1s" end="4s"><span begin="1s" end="4s">$text</span></p>""")
            .lines.single() as KaraokeLine
        assertEquals("Don't &lt; 🎵 🎵 \"yes\" 'ok'", line.syllables.single().content)
    }

    @Test fun malformedEntitiesRemainReadable() {
        val text = "&#0; &#xD800; &#x110000; &#9999999999999; &unknown; &#xZZ;"
        val line = parse("""<p begin="1s" end="4s">$text</p>""").lines.single() as SyncedLine
        assertEquals(text, line.content)
    }

    @Test fun cdataRemainsLiteralAndInOrder() {
        val line = parse("""<p begin="1s" end="4s"><span begin="1s" end="4s">A<![CDATA[<B>&amp;]]><span>C</span>D</span></p>""")
            .lines.single() as KaraokeLine
        assertEquals("A<B>&amp;CD", line.syllables.single().content)
    }

    @Test fun unTimedPunctuationDoesNotCreateExtraSyllables() {
        val line = parse("""<p begin="1s" end="4s">“<span begin="1s" end="2s">Hello</span>, <span begin="3s" end="4s">world</span>!”</p>""")
            .lines.single() as KaraokeLine
        assertEquals(listOf("“Hello, ", "world!”"), line.syllables.map { it.content })
        assertEquals(listOf(1000 to 2000, 3000 to 4000), line.syllables.map { it.start to it.end })
    }

    @Test fun translationAndPhoneticsKeepNestedTextSeparateFromVocals() {
        val line = parse("""<p begin="1s" end="4s"><span begin="1s" end="4s">Hello</span><span ttm:role="x-translation">Пр<span>ив</span>ет &amp;lt;</span><span ttm:role="x-roman" begin="1s" end="4s">h<span>el</span>o</span></p>""")
            .lines.single() as KaraokeLine
        assertEquals("Hello", line.syllables.single().content)
        assertEquals("Привет &lt;", line.translation)
        assertEquals("helo", line.phonetic)
    }

    @Test fun encodedKeysAndQuotedAngleBracketsKeepMetadataAttached() {
        val head = """<head><metadata><iTunesMetadata><translations><translation><text for="A&amp;B&gt;C">Be<span>fore</span> after<span ttm:role="x-bg">E<span>cho</span></span></text></translation></translations></iTunesMetadata></metadata></head>"""
        val line = parse("""<p begin="1s" end="4s" itunes:key="A&amp;B>C"><span begin="1s" end="2s">Main</span><span ttm:role="x-bg"><span begin="3s" end="4s">Background</span></span></p>""", head)
            .lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals("Before after", line.translation)
        assertEquals("Echo", line.accompanimentLines!!.single().translation)
    }
}
