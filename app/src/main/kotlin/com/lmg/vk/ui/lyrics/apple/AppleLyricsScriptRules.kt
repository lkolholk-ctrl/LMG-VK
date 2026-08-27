package com.lmg.vk.ui.lyrics.apple

import java.text.Bidi

object AppleLyricsScriptRules {
    private val noCoreStretchBlocks = setOf(
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.THAI,
        Character.UnicodeBlock.ARABIC,
        Character.UnicodeBlock.ARABIC_SUPPLEMENT,
        Character.UnicodeBlock.ARABIC_EXTENDED_A,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
        Character.UnicodeBlock.DEVANAGARI,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
    )

    /**
     * Conservative boundary for the recovered language branches: only the proven
     * Latin/default route receives core stretch. Supplementary code points are read
     * as code points rather than two unrelated UTF-16 chars.
     */
    fun allowsCoreStretch(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (Character.UnicodeBlock.of(codePoint) in noCoreStretchBlocks) return false
            index += Character.charCount(codePoint)
        }
        return true
    }

    fun isRtl(text: String): Boolean = text.isNotBlank() &&
        !Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
}
