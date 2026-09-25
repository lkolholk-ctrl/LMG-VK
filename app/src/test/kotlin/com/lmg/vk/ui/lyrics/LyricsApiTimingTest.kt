package com.lmg.vk.ui.lyrics

import com.lmg.vk.engine.lyrics.LyricsContent
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.waitingIntervals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** API timing snapshots from 2026-09-06; lyric text is replaced with placeholders. */
class LyricsApiTimingTest {
    @Test fun antidoteKeepsEveryApiLineAndWordTimestamp() = checkTiming("antidote", 20, 12_230)

    @Test fun whatIsLoveKeepsEveryApiLineAndWordTimestamp() = checkTiming("what-is-love", 73, 500)

    private fun checkTiming(name: String, count: Int, firstStart: Int) {
        val raw = javaClass.getResource("/lyrics/$name-timing.ttml")!!.readText()
        val parsed = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(raw, "apple_ttml", "Apple"))
        val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(raw.byteInputStream())
        val paragraphs = dom.getElementsByTagName("p")
        assertTrue(parsed.isSynced)
        assertEquals(count, parsed.synced.lines.size)
        assertEquals(firstStart, parsed.synced.lines.first().start)
        val vocals = parsed.synced.lines.flatMap { line ->
            listOf(line) + (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines.orEmpty()
        }
        waitingIntervals(parsed.synced.lines).forEach { gap ->
            assertTrue("$name waiting dots overlap a vocal", vocals.none { it.start < gap.endMs && it.end > gap.startMs })
        }
        for (index in 0 until paragraphs.length) {
            val source = paragraphs.item(index) as Element
            val line = parsed.synced.lines[index] as KaraokeLine
            assertEquals("$name line $index begin", time(source.getAttribute("begin")), line.start)
            assertEquals("$name line $index end", time(source.getAttribute("end")), line.end)
            val spans = (0 until source.childNodes.length).mapNotNull { source.childNodes.item(it) as? Element }
                .filter { it.tagName == "span" && it.hasAttribute("begin") && it.hasAttribute("end") &&
                    it.getAttribute("ttm:role") !in listOf("x-bg", "x-translation") }
            assertEquals("$name line $index words", spans.size, line.syllables.size)
            spans.forEachIndexed { wordIndex, span ->
                assertEquals(time(span.getAttribute("begin")), line.syllables[wordIndex].start)
                assertEquals(time(span.getAttribute("end")), line.syllables[wordIndex].end)
            }
        }
    }

    private fun time(value: String): Int = value.split(':').fold(0.0) { total, part -> total * 60 + part.toDouble() }
        .times(1000).let { kotlin.math.round(it).toInt() }
}
