package helium314.keyboard.latin.dictionary.history

import android.content.Context
import java.util.Locale

// DwellBoard: PersonalizationHelper keeps the fork's dictionary cache, keyed by learning context as well as
// locale. Upstream's callers go through here, so this delegates rather than keeping a second cache.
object UserHistoryDictUtils {
    @JvmStatic
    fun getUserHistoryDictionary(context: Context?, locale: Locale): UserHistoryDictionary =
        PersonalizationHelper.getUserHistoryDictionary(context, locale)

    @JvmStatic
    fun removeAllUserHistoryDictionaries(context: Context) =
        PersonalizationHelper.removeAllUserHistoryDictionaries(context)
}
