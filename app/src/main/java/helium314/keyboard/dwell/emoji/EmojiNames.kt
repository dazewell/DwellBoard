// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import android.content.Context
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiNeutralVersion
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import java.util.Locale

/**
 * The name shown when an emoji is long-pressed. Upstream only asks the current language's emoji
 * dictionary for the exact key label, so an emoji is nameless when that language has no emoji
 * dictionary, or an older one that predates the emoji. This also asks the emoji dictionaries of the
 * other enabled languages, and tries the spelling without skin tone or variation selector.
 *
 * Only called on long-press, on the main thread, like every caller of [close].
 */
object EmojiNames {
    private var fallbacksExclude: Locale? = null
    private var fallbacks: List<Dictionary>? = null

    /** [primary] is the caller's already open dictionary for the current language, if it has one. */
    fun describe(context: Context, primary: SingleDictionaryFacilitator?, emoji: String): String? {
        if (!Settings.getValues().mShowEmojiDescriptions) return null
        val candidates = candidates(emoji, getEmojiNeutralVersion(emoji))
        primary?.let { dict -> lookUp(candidates) { dict.getWordProperty(it) }?.let { return it } }
        for (dict in fallbacks(context, primary?.mainLocale)) {
            lookUp(candidates) { dict.getWordProperty(it, false) }?.let { return it }
        }
        return null
    }

    fun close() {
        fallbacks?.forEach { it.close() }
        fallbacks = null
        fallbacksExclude = null
    }

    private inline fun lookUp(candidates: List<String>, property: (String) -> WordProperty?): String? {
        for (candidate in candidates) {
            val wordProperty = property(candidate) ?: continue
            if (wordProperty.mHasShortcuts) wordProperty.mShortcutTargets.firstOrNull()?.mWord?.let { return it }
        }
        return null
    }

    /**
     * Emoji dictionaries of the enabled languages, current one first, then in subtype order, leaving out
     * [exclude], the one the caller already has open. Kept open until [close] or a change of [exclude].
     */
    private fun fallbacks(context: Context, exclude: Locale?): List<Dictionary> {
        fallbacks?.takeIf { fallbacksExclude == exclude }?.let { return it }
        close()
        val current = RichInputMethodManager.getInstance().currentSubtype.locale
        val locales = DictionaryInfoUtils.getLocalesWithEmojiDicts(context).sortedBy { it != current }.distinct()
        val dicts = locales.filter { it != exclude }.mapNotNull { locale ->
            DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
                ?.let { DictionaryFactory.getDictionary(it, locale) }
        }
        fallbacks = dicts
        fallbacksExclude = exclude
        return dicts
    }

    /**
     * Spellings to look up, most specific first: the neutral version upstream used, the label itself,
     * then with the emoji variation selector toggled, then without skin tones, which names a toned
     * emoji after its base one in a dictionary that lacks the toned entry.
     */
    internal fun candidates(emoji: String, neutral: String): List<String> {
        val result = LinkedHashSet<String>()
        result.add(neutral)
        result.add(emoji)
        if (emoji.contains(VARIATION_SELECTOR)) {
            result.add(emoji.replace(VARIATION_SELECTOR, ""))
        } else if (emoji.codePointCount(0, emoji.length) == 1) {
            result.add(emoji + VARIATION_SELECTOR)
        }
        val bare = buildString {
            var i = 0
            while (i < emoji.length) {
                val cp = emoji.codePointAt(i)
                if (cp !in SKIN_TONES && cp != VARIATION_SELECTOR_CP) appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        if (bare.isNotEmpty()) result.add(bare)
        return result.toList()
    }

    private const val VARIATION_SELECTOR_CP = 0xFE0F
    private const val VARIATION_SELECTOR = "️"
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
}
