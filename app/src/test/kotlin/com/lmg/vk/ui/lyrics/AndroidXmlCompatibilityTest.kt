package com.lmg.vk.ui.lyrics

import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.apple.AppleTtmlParser
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class AndroidXmlCompatibilityTest {
    @Test fun creditsSurviveAndroidFactoryFeatureRestrictions() {
        val property = "javax.xml.parsers.DocumentBuilderFactory"
        val original = System.getProperty(property)
        AndroidFeatureRestrictedFactory.delegate = DocumentBuilderFactory.newInstance()
        try {
            System.setProperty(property, AndroidFeatureRestrictedFactory::class.java.name)
            val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata>
                <songwriter>Marshall Mathers</songwriter><songwriter>Luis Resto</songwriter>
                </metadata></head><body><div><p begin="1s" end="2s">
                <span begin="1s" end="2s">Hello</span></p></div></body></tt>"""
            val result = AccompanistLyricsAdapter.convert(LyricsContent.RawTtml(xml, "apple_ttml", "Apple TTML"))
            assertEquals(listOf("Marshall Mathers", "Luis Resto"), result.songwriters)
            assertEquals(1, result.synced.lines.size)
            assertEquals(1000, result.synced.lines.single().start)
            assertEquals(result.songwriters, AppleTtmlParser.parse(xml)!!.songwriters.map { it.name })
        } finally {
            if (original == null) System.clearProperty(property) else System.setProperty(property, original)
        }
    }

    @Test fun documentTypesRemainRejected() {
        for (declaration in listOf(
            """<!DOCTYPE tt [<!ENTITY writer "injected">]>""",
            """<!DOCTYPE tt [<!ENTITY writer SYSTEM "file:///never-read">]>""",
            """<!DOCTYPE tt SYSTEM "https://example.invalid/never-fetch">""",
        )) {
            val xml = """$declaration<tt><head><metadata><songwriter>&writer;</songwriter></metadata></head></tt>"""
            assertTrue(AppleTtmlParser.readSongwriters(xml).isEmpty())
            assertNull(AppleTtmlParser.parse(xml))
        }
    }
}

class AndroidFeatureRestrictedFactory : DocumentBuilderFactory() {
    override fun newDocumentBuilder(): DocumentBuilder {
        delegate.isNamespaceAware = isNamespaceAware
        delegate.isValidating = isValidating
        return delegate.newDocumentBuilder()
    }

    override fun setAttribute(name: String, value: Any) = delegate.setAttribute(name, value)
    override fun getAttribute(name: String): Any = delegate.getAttribute(name)
    override fun setFeature(name: String, value: Boolean) {
        when (name) {
            "http://xml.org/sax/features/namespaces" -> isNamespaceAware = value
            "http://xml.org/sax/features/validation" -> isValidating = value
            else -> throw ParserConfigurationException(name)
        }
    }
    override fun getFeature(name: String): Boolean = when (name) {
        "http://xml.org/sax/features/namespaces" -> isNamespaceAware
        "http://xml.org/sax/features/validation" -> isValidating
        else -> throw ParserConfigurationException(name)
    }

    companion object {
        lateinit var delegate: DocumentBuilderFactory
    }
}
