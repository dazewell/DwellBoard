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
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary;
import helium314.keyboard.latin.utils.ExecutorUtils;
import helium314.keyboard.latin.settings.LearningContexts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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

    /**
     * Copies the user history dictionaries of all languages from one learning context to another, in the background.
     * Dictionaries that are loaded are flushed first, so that what was learned recently is copied too.
     */
    public static void copyUserHistoryDictionaries(final Context context, @NonNull final String fromContext,
            @NonNull final String toContext) {
        final String fromName = UserHistoryDictionary.getContextName(fromContext);
        final String toName = UserHistoryDictionary.getContextName(toContext);
        final boolean fromDefault = LearningContexts.DEFAULT.equals(fromContext);
        final File filesDir = context.getFilesDir();
        if (filesDir == null) return;
        final HashSet<String> dictNames = new HashSet<>(); // names without file extension
        synchronized (sLangUserHistoryDictCache) {
            for (final String key : sLangUserHistoryDictCache.keySet()) dictNames.add(key);
        }
        final File[] files = filesDir.listFiles();
        if (files != null) {
            for (final File file : files) {
                final String name = file.getName();
                if (name.endsWith(".dict")) dictNames.add(name.substring(0, name.length() - ".dict".length()));
            }
        }
        for (final String dictName : dictNames) {
            // the default context's name is a prefix of the names of all other contexts
            if (!dictName.startsWith(fromName + ".")
                    || (fromDefault && dictName.startsWith(UserHistoryDictionary.CONTEXT_NAME_PREFIX))) continue;
            final String targetName = toName + dictName.substring(fromName.length());
            final File source = ExpandableBinaryDictionary.getDictFile(context, dictName, null);
            final File target = ExpandableBinaryDictionary.getDictFile(context, targetName, null);
            final Runnable copy = () -> copyDictionary(source, target);
            final SoftReference<UserHistoryDictionary> ref = sLangUserHistoryDictCache.get(dictName);
            final UserHistoryDictionary dict = ref == null ? null : ref.get();
            if (dict != null) dict.flushThen(copy);
            else ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(copy);
        }
    }

    /**
     * A dictionary is a directory with files that are named like the directory, so the files need new names.
     * It is copied to a temporary directory first, so a half-copied dictionary is never loaded.
     */
    private static void copyDictionary(final File source, final File target) {
        if (!source.isDirectory()) return;
        final File tmp = new File(target.getPath() + ".copy");
        try {
            FileUtils.deleteRecursively(tmp);
            final File[] children = source.listFiles();
            if (children == null) return;
            for (final File child : children) {
                final File out = new File(tmp, target.getName() + child.getName().substring(source.getName().length()));
                try (final FileInputStream in = new FileInputStream(child)) {
                    FileUtils.copyStreamToNewFile(in, out);
                }
            }
            FileUtils.deleteRecursively(target);
            if (!tmp.renameTo(target)) Log.e(TAG, "Cannot move copied dictionary: " + target.getName());
        } catch (final IOException e) {
            Log.e(TAG, "Cannot copy dictionary of learning context: " + source.getName(), e);
        } finally {
            FileUtils.deleteRecursively(tmp);
        }
    }

    /**
     * Empties the user history dictionaries of all languages for the given (non-default) learning context.
     * Unlike {@link #removeUserHistoryDictionaries} the loaded dictionaries stay cached, as the context may be in use.
     */
    public static void clearUserHistoryDictionaries(final Context context, @NonNull final String learningContext) {
        if (LearningContexts.DEFAULT.equals(learningContext)) return;
        final String filePrefix = UserHistoryDictionary.getContextName(learningContext) + ".";
        synchronized (sLangUserHistoryDictCache) {
            for (final ConcurrentHashMap.Entry<String, SoftReference<UserHistoryDictionary>> entry
                    : sLangUserHistoryDictCache.entrySet()) {
                if (!entry.getKey().startsWith(filePrefix) || entry.getValue() == null) continue;
                final UserHistoryDictionary dict = entry.getValue().get();
                if (dict != null) dict.clear();
            }
            removeDictionaryFiles(context, filePrefix);
        }
    }

    /**
     * Removes dictionary files of learning contexts that don't exist any more, e.g. because a dictionary that was still
     * held when its context got deleted has written itself to file again.
     */
    public static void removeDictionariesOfDeletedContexts(final Context context, @NonNull final Set<String> existingContextIds) {
        final File filesDir = context.getFilesDir();
        final File[] files = filesDir == null ? null : filesDir.listFiles();
        if (files == null) return;
        for (final File file : files) {
            final String name = file.getName();
            if (!name.startsWith(UserHistoryDictionary.CONTEXT_NAME_PREFIX)) continue;
            final String idAndLocale = name.substring(UserHistoryDictionary.CONTEXT_NAME_PREFIX.length());
            final int end = idAndLocale.indexOf('.');
            if (existingContextIds.contains(end < 0 ? idAndLocale : idAndLocale.substring(0, end))) continue;
            if (!FileUtils.deleteRecursively(file))
                Log.e(TAG, "Cannot remove dictionary of deleted learning context: " + name);
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
