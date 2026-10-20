/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.personalization;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.inputmethod.latin.BinaryDictionary;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary;
import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.makedict.DictionaryHeader;
import helium314.keyboard.latin.settings.LearningContexts;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Locally gathers statistics about the words user types and various other signals like
 * auto-correction cancellation or manual picks. This allows the keyboard to adapt to the
 * typist over time.
 */
public class UserHistoryDictionary extends ExpandableBinaryDictionary {
    public static final String NAME = UserHistoryDictionary.class.getSimpleName();
    /** start of the dictionary name of a learning context, followed by its id */
    static final String CONTEXT_NAME_PREFIX = NAME + ".ctx-";

    UserHistoryDictionary(final Context context, final Locale locale, @NonNull final String learningContext) {
        super(context, getUserHistoryDictName(locale, learningContext), locale, Dictionary.TYPE_USER_HISTORY, null);
        if (mLocale != null && mLocale.toString().length() > 1) {
            reloadDictionaryIfRequired();
        }
    }

    /**
     * @returns the name of the dictionary files for the learning context, without locale.
     * This is the same as {@link #NAME} for the default context.
     */
    static String getContextName(@NonNull final String learningContext) {
        return LearningContexts.DEFAULT.equals(learningContext) ? NAME : CONTEXT_NAME_PREFIX + learningContext;
    }

    /**
     * @returns the name of the {@link UserHistoryDictionary} for the given locale and learning context.
     */
    static String getUserHistoryDictName(final Locale locale, @NonNull final String learningContext) {
        return getDictName(getContextName(learningContext), locale, null);
    }

    public static UserHistoryDictionary getDictionary(final Context context, final Locale locale,
            final File dictFile, final String dictNamePrefix, @NonNull final String learningContext) {
        return PersonalizationHelper.getUserHistoryDictionary(context, locale, learningContext);
    }

    /**
     * Add a word to the user history dictionary.
     *
     * @param userHistoryDictionary the user history dictionary
     * @param ngramContext the n-gram context
     * @param word the word the user inputted
     * @param isValid whether the word is valid or not
     * @param timestamp the timestamp when the word has been inputted
     */
    public static void addToDictionary(final ExpandableBinaryDictionary userHistoryDictionary,
            @NonNull final NgramContext ngramContext, final String word, final boolean isValid,
            final int timestamp) {
        if (word.length() > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH) {
            return;
        }
        userHistoryDictionary.updateEntriesForWord(ngramContext, word,
                isValid, 1 /* count */, timestamp);
    }

    @Override
    protected Map<String, String> getHeaderAttributeMap() {
        final Map<String, String> attributeMap = super.getHeaderAttributeMap();
        attributeMap.put(DictionaryHeader.USES_FORGETTING_CURVE_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.HAS_HISTORICAL_INFO_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        return attributeMap;
    }

    @Override
    protected void loadInitialContentsLocked() {
        // No initial contents.
    }

    @Override
    public boolean isValidWord(final String word) {
        // Strings out of this dictionary should not be considered existing words.
        return false;
    }
}
