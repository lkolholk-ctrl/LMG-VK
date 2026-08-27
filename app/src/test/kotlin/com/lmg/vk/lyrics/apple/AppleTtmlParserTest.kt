package com.lmg.vk.lyrics.apple

import com.lmg.vk.engine.lyrics.apple.AppleAgentType
import com.lmg.vk.engine.lyrics.apple.ApplePieceRole
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleTtmlParserTest {

    @Test
    fun parsesVariousTimeFormats() {
        assertEquals(27395L, AppleTtmlParser.parseAppleTime("27.395"))
        assertEquals(27395L, AppleTtmlParser.parseAppleTime("27.395s"))
        assertEquals(27395L, AppleTtmlParser.parseAppleTime("27395ms"))
        assertEquals(141228L, AppleTtmlParser.parseAppleTime("2:21.228"))
        assertEquals(141228L, AppleTtmlParser.parseAppleTime("00:02:21.228"))
        assertEquals(3723456L, AppleTtmlParser.parseAppleTime("01:02:03.456"))
    }

    @Test
    fun parsesCompleteAppleTtml() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/metadata"
                xml:lang="en-US"
                itunes:timing="Word">
              <head>
                <metadata>
                  <ttm:agent xml:id="v1" type="person">
                    <ttm:name>Vocalist One</ttm:name>
                  </ttm:agent>
                  <ttm:agent xml:id="v2" type="person">
                    <ttm:name>Vocalist Two</ttm:name>
                  </ttm:agent>
                  <songwriter>John Doe</songwriter>
                  <songwriter>Jane Smith</songwriter>
                </metadata>
              </head>
              <body dur="03:45.000">
                <div begin="00:10.000" end="00:30.000" itunes:songPart="Verse 1" ttm:agent="v1">
                  <p xml:id="p1" begin="00:10.500" end="00:15.000" itunes:key="line_1">
                    <span begin="00:10.500" end="00:11.000">Hello </span>
                    <span begin="00:11.000" end="00:12.500">world</span>
                    <span ttm:role="x-bg" begin="00:12.600" end="00:14.000"> (echo)</span>
                    <span ttm:role="x-translation" xml:lang="ru">
                      <span begin="00:10.500" end="00:12.500">Привет мир</span>
                    </span>
                  </p>
                  <p xml:id="p2" begin="00:15.500" end="00:20.000" ttm:agent="v2">
                    <span begin="00:15.500" end="00:15.500">Fast</span>
                    <span begin="00:15.600" end="00:17.000"> singing</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val doc = AppleTtmlParser.parse(ttml)
        assertNotNull(doc)
        assertEquals(AppleTimingType.WORD, doc!!.timing)
        assertEquals("en-US", doc.language)
        assertEquals(2, doc.songwriters.size)
        assertEquals("John Doe", doc.songwriters[0].name)
        assertEquals("Jane Smith", doc.songwriters[1].name)

        assertEquals(2, doc.agents.size)
        assertEquals(AppleAgentType.PERSON, doc.agents["v1"]?.type)
        assertEquals("Vocalist One", doc.agents["v1"]?.name)

        assertEquals(1, doc.sections.size)
        val sec = doc.sections[0]
        assertEquals("Verse 1", sec.songPart)
        assertEquals(2, sec.lines.size)

        val line1 = sec.lines[0]
        assertEquals("p1", line1.id)
        assertEquals("line_1", line1.key)
        assertEquals("v1", line1.agentId)
        assertEquals(10500L, line1.beginMs)
        assertEquals(15000L, line1.endMs)

        // Check main pieces
        val mainText = line1.main.joinToString("") { it.text }
        assertEquals("Hello world", mainText)
        assertEquals(2, line1.main.size)
        assertEquals(10500L, line1.main[0].beginMs)
        assertEquals(11000L, line1.main[0].endMs)

        // Check background pieces
        assertEquals(1, line1.background.size)
        assertEquals(" (echo)", line1.background[0].text)
        assertEquals(ApplePieceRole.BACKGROUND, line1.background[0].role)

        // Check translation
        assertEquals(1, line1.translation.size)
        assertEquals("Привет мир", line1.translation[0].text)

        // Check zero duration normalization on line2
        val line2 = sec.lines[1]
        assertEquals("v2", line2.agentId)
        val fastPiece = line2.main[0]
        assertEquals("Fast", fastPiece.text)
        assertEquals(15500L, fastPiece.beginMs)
        assertTrue("Zero duration must be normalized to at least +1ms", fastPiece.endMs > fastPiece.beginMs)
    }

    @Test
    fun generatedLineIdsStayUniqueAcrossDivisions() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <body dur="10.000">
                <div><p begin="1.000" end="2.000"><span begin="1.000" end="2.000">One</span></p></div>
                <div><p begin="3.000" end="4.000"><span begin="3.000" end="4.000">Two</span></p></div>
              </body>
            </tt>
        """.trimIndent()

        val lines = AppleTtmlParser.parse(ttml)!!.allLines
        assertEquals(lines.size, lines.map { it.id }.distinct().size)
    }
}
