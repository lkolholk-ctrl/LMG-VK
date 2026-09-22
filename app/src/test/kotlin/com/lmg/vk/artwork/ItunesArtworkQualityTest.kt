package com.lmg.vk.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

class ItunesArtworkQualityTest {
    private val base = "Music114/v4/aa/bb/cc/asset/cover.rgb.jpg"

    @Test fun originalAssetPrecedesLargeRenditionAndApiFallback() {
        val url = "https://is1-ssl.mzstatic.com/image/thumb/$base/100x100bb.jpg"
        assertEquals(listOf("https://a5.mzstatic.com/us/r1000/0/$base",
            "https://is1-ssl.mzstatic.com/image/thumb/$base/10000x10000bb.jpg", url),
            ItunesArtworkQuality.urls(url))
    }

    @Test fun oldCached600PixelArtworkUpgradesWithoutAnotherSearch() {
        val url = "https://is1-ssl.mzstatic.com/image/thumb/$base/600x600bb.jpg"
        assertEquals("https://a5.mzstatic.com/us/r1000/0/$base", ItunesArtworkQuality.urls(url).first())
    }

    @Test fun originalAndUnrelatedUrlsAreNotRewritten() {
        for (url in listOf("https://a5.mzstatic.com/us/r1000/0/$base",
            "https://example.org/image/thumb/$base/100x100bb.jpg",
            "https://fake-mzstatic.com/image/thumb/$base/100x100bb.jpg")) {
            assertEquals(listOf(url), ItunesArtworkQuality.urls(url))
        }
    }
}
