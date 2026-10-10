/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.personalization;

import android.content.Context;
import helium314.keyboard.latin.utils.Log;

import androidx.annotation.NonNull;

import helium314.keyboard.latin.common.FileUtils;
import helium314.keyboard.latin.settings.LearningContexts;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helps handle and manage personalized dictionaries such as {@link UserHistoryDictionary}.
 */
public class PersonalizationHelper {
    private static final String TAG = PersonalizationHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    // keys consist of locale and learning context
    private static final ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangUserHistoryDictCache = new ConcurrentHashMap<>();

    @NonNull
    public static UserHistoryDictionary getUserHistoryDictionary(final Context context, final Locale locale) {
        return getUserHistoryDictionary(context, locale, LearningContexts.DEFAULT);
    }

    @NonNull
    public static UserHistoryDictionary getUserHistoryDictionary(final Context context, final Locale locale,
            @NonNull final String learningContext) {
        String lookupStr = UserHistoryDictionary.getUserHistoryDictName(locale, learningContext);
        synchronized (sLangUserHistoryDictCache) {
            if (sLangUserHistoryDictCache.containsKey(lookupStr)) {
                final SoftReference<UserHistoryDictionary> ref =
                        sLangUserHistoryDictCache.get(lookupStr);
                final UserHistoryDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Use cached UserHistoryDictionary with lookup: " + lookupStr);
                    }
                    dict.reloadDictionaryIfRequired();
                    return dict;
                }
            }
            final UserHistoryDictionary dict = new UserHistoryDictionary(context, locale, learningContext);
            sLangUserHistoryDictCache.put(lookupStr, new SoftReference<>(dict));
            return dict;
        }
    }

    public static void removeAllUserHistoryDictionaries(final Context context) {
        synchronized (sLangUserHistoryDictCache) {
            for (final ConcurrentHashMap.Entry<String, SoftReference<UserHistoryDictionary>> entry
                    : sLangUserHistoryDictCache.entrySet()) {
                if (entry.getValue() != null) {
                    final UserHistoryDictionary dict = entry.getValue().get();
                    if (dict != null) {
                        dict.clear();
                    }
                }
            }
            sLangUserHistoryDictCache.clear();
            // all user history dictionaries, including those of learning contexts, start with this name
            removeDictionaryFiles(context, UserHistoryDictionary.NAME);
        }
    }

    /** Removes the user history dictionaries of all languages for the given learning context. */
    public static void removeUserHistoryDictionaries(final Context context, @NonNull final String learningContext) {
        // the trailing "." makes sure we don't remove dictionaries of other contexts with a similar id
        final String filePrefix = UserHistoryDictionary.getContextName(learningContext) + ".";
        synchronized (sLangUserHistoryDictCache) {
            final ArrayList<String> keysToRemove = new ArrayList<>();
            for (final ConcurrentHashMap.Entry<String, SoftReference<UserHistoryDictionary>> entry
                    : sLangUserHistoryDictCache.entrySet()) {
                if (!entry.getKey().startsWith(filePrefix)) continue;
                keysToRemove.add(entry.getKey());
                if (entry.getValue() != null) {
                    final UserHistoryDictionary dict = entry.getValue().get();
                    if (dict != null) {
                        dict.clear();
                    }
                }
            }
            for (final String key : keysToRemove) sLangUserHistoryDictCache.remove(key);
            removeDictionaryFiles(context, filePrefix);
        }
    }

    private static void removeDictionaryFiles(final Context context, final String namePrefix) {
        final File filesDir = context.getFilesDir();
        if (filesDir == null) {
            Log.e(TAG, "context.getFilesDir() returned null.");
            return;
        }
        final boolean filesDeleted = FileUtils.deleteFilteredFiles(filesDir, new DictFilter(namePrefix));
        if (!filesDeleted) {
            Log.e(TAG, "Cannot remove dictionary files. filesDir: " + filesDir.getAbsolutePath()
                    + ", dictNamePrefix: " + namePrefix);
        }
    }

    private static class DictFilter implements FilenameFilter {
        private final String mName;

        DictFilter(final String name) {
            mName = name;
        }

        @Override
        public boolean accept(final File dir, final String name) {
            return name.startsWith(mName);
        }
    }
}
