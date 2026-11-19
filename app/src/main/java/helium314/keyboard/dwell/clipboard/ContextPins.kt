// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.clipboard

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.Log
import java.io.File

/**
 * Pinned clips belong to the learning context they were pinned in; unpinned history is shared by all.
 *
 * The context is stored in an extra column of the clipboard table, null for the default context. Pins of
 * other contexts are kept in [hidden] instead of the DAO's cache, so the cache stays the list the clipboard
 * view shows and its index based access is unchanged. A stored id that is no longer a context counts as
 * the default context, so pins are never lost to a broken or restored context list.
 *
 * [contexts] and [hidden] are only touched while holding the DAO's lock, the same one its writes use.
 */
object ContextPins {
    private const val TAG = "ContextPins"
    private const val TABLE = "CLIPBOARD"
    const val COLUMN = "DWELL_CONTEXT"

    @Volatile private var dao: ClipboardDao? = null
    private var db: SQLiteDatabase? = null
    private var prefs: SharedPreferences? = null
    private var columnOk = false

    /** row id -> context, only for pins of a context other than the default */
    private val contexts = HashMap<Long, String>()
    private val hidden = mutableListOf<ClipboardHistoryEntry>()

    /** Adds the column if missing. Returns whether the table has it, never throws. */
    fun ensureColumn(sqlite: SQLiteDatabase): Boolean = runCatching {
        val has = sqlite.rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
            val name = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(name) == COLUMN) found = true
            found
        }
        if (!has && !sqlite.isReadOnly) sqlite.execSQL("ALTER TABLE $TABLE ADD COLUMN $COLUMN TEXT")
        has || !sqlite.isReadOnly
    }.onFailure { Log.w(TAG, "no context column", it) }.getOrDefault(false)

    /** column to select when restoring a backup database, NULL if it can't have one */
    fun restoreColumn(sqlite: SQLiteDatabase) = if (ensureColumn(sqlite)) COLUMN else "NULL"

    /** Called once the DAO exists, after its file cleanup has seen every entry. */
    fun attach(clipboardDao: ClipboardDao, database: Database, sharedPrefs: SharedPreferences) = synchronized(clipboardDao) {
        db = database.writableDatabase
        prefs = sharedPrefs
        columnOk = ensureColumn(database.writableDatabase)
        contexts.clear()
        hidden.clear()
        if (columnOk) runCatching {
            database.readableDatabase.rawQuery(
                "SELECT ID, $COLUMN FROM $TABLE WHERE PINNED != 0 AND $COLUMN IS NOT NULL AND $COLUMN != ''", null
            ).use { while (it.moveToNext()) contexts[it.getLong(0)] = it.getString(1) }
        }.onFailure { Log.w(TAG, "can't read pin contexts", it) }
        show(clipboardDao)
        dao = clipboardDao
    }

    /** The learning context changed, show its pins. Does not create the DAO, it stays absent while locked. */
    @JvmStatic
    fun onContextChanged() {
        val d = dao ?: return
        synchronized(d) { show(d) }
    }

    // ---- hooks called from ClipboardDao, all under its lock ----

    /** Stores the context of a new row. Returns true if it is a pin of another context and must stay out of the cache. */
    fun onInserted(entry: ClipboardHistoryEntry, pinContext: String?): Boolean {
        val stored = pinContext?.takeIf { entry.isPinned && it.isNotEmpty() }
        if (stored == null) contexts.remove(entry.id) else contexts[entry.id] = stored // row ids can be reused
        if (!entry.isPinned || contextOf(stored, validIds()) == LearningContexts.current) return false
        hidden.add(entry)
        return true
    }

    /** value for the column of a new row, only set when needed so inserts work without the column */
    fun put(cv: ContentValues, pinned: Boolean, pinContext: String?) {
        if (columnOk && pinned && !pinContext.isNullOrEmpty()) cv.put(COLUMN, pinContext)
    }

    /** A visible entry was pinned or unpinned in the current context. */
    fun onToggled(entry: ClipboardHistoryEntry, cv: ContentValues) {
        val ctx = LearningContexts.current.takeIf { entry.isPinned && it != LearningContexts.DEFAULT }
        if (ctx == null) contexts.remove(entry.id) else contexts[entry.id] = ctx
        if (columnOk) cv.put(COLUMN, ctx)
    }

    fun onDeleted(entries: List<ClipboardHistoryEntry>) {
        val ids = entries.mapTo(HashSet()) { it.id }
        hidden.removeAll { it.id in ids }
        ids.forEach { contexts.remove(it) }
    }

    /** Clears all hidden pins, returns whether there were any. */
    fun onCleared(): Boolean {
        val had = hidden.isNotEmpty()
        hidden.clear()
        contexts.clear()
        return had
    }

    /** Clip files are named by content, so a visible entry and a hidden pin can share one. */
    fun fileInUse(cache: List<ClipboardHistoryEntry>, filename: String) =
        cache.any { it.filename == filename } || hidden.any { it.filename == filename }

    fun hiddenEntries(): List<ClipboardHistoryEntry> {
        val d = dao ?: return emptyList()
        return synchronized(d) { hidden.toList() }
    }

    // ---- learning context lifecycle, from settings ----

    /** Gives [toId] a copy of the pins of [fromId]. */
    fun copyContext(context: Context, fromId: String, toId: String) {
        val d = ClipboardDao.getInstance(context) ?: return
        synchronized(d) {
            val sqlite = db ?: return
            if (!columnOk || toId == LearningContexts.DEFAULT) return
            val valid = validIds()
            val sources = (d.cache + hidden).filter { it.isPinned && contextOf(contexts[it.id], valid) == fromId }
            runCatching {
                sources.forEach {
                    val cv = ContentValues(6)
                    cv.put("TIMESTAMP", it.timeStamp)
                    cv.put("PINNED", true)
                    cv.put("TEXT", it.text)
                    cv.put("FILE", it.filename)
                    cv.put("MIME_TYPE", it.mimeTypes?.joinToString("§"))
                    cv.put(COLUMN, toId)
                    val rowId = sqlite.insert(TABLE, null, cv)
                    if (rowId == -1L) return@forEach
                    contexts[rowId] = toId
                    hidden.add(ClipboardHistoryEntry(rowId, it.timeStamp, true, it.text, it.filename, it.mimeTypes))
                }
            }.onFailure { Log.w(TAG, "can't copy pins", it) }
            show(d)
        }
    }

    /** Deletes the pins of a deleted context. Matches the stored id: the context is already gone, so it would resolve to the default. */
    fun deleteContext(context: Context, id: String) {
        if (id == LearningContexts.DEFAULT) return
        val d = ClipboardDao.getInstance(context) ?: return
        synchronized(d) {
            val sqlite = db ?: return
            runCatching {
                sqlite.delete(TABLE, "PINNED != 0 AND $COLUMN = ?", arrayOf(id))
            }.onFailure { Log.w(TAG, "can't delete pins", it); return }
            val ids = contexts.filterValues { it == id }.keys.toHashSet()
            val gone = (hidden + d.cache).filter { it.id in ids }
            ids.forEach { contexts.remove(it) }
            hidden.removeAll { it.id in ids }
            if (d.cache.any { it.id in ids })
                replaceVisible(d, d.cache.filterNot { it.id in ids })
            gone.forEach { if (it.filename != null && !fileInUse(d.cache, it.filename)) File(ClipboardDao.clipFilesDir, it.filename).delete() }
            show(d)
        }
    }

    // ---- internals ----

    private fun validIds(): Set<String> =
        prefs?.let { p -> LearningContexts.getAll(p).mapTo(HashSet()) { it.id } } ?: emptySet()

    private fun show(d: ClipboardDao) {
        val (toHide, toShow) = partition(d.cache, hidden, contexts, validIds(), LearningContexts.current)
        if (toHide.isEmpty() && toShow.isEmpty()) return
        val hideIds = toHide.mapTo(HashSet()) { it.id }
        hidden.removeAll(toShow)
        hidden.addAll(toHide)
        replaceVisible(d, d.cache.filterNot { it.id in hideIds } + toShow)
    }

    private fun replaceVisible(d: ClipboardDao, entries: List<ClipboardHistoryEntry>) {
        val oldSize = d.cache.size
        d.cache.clear()
        d.cache.addAll(entries)
        d.cache.sort()
        val listener = d.listener ?: return
        // a visible clipboard view rebinds every row; inserting each at 0 keeps it scrolled to the top
        listener.onClipsRemoved(0, oldSize)
        repeat(d.cache.size) { listener.onClipInserted(0) }
    }

    /** the context a pin belongs to: its stored id while that context exists, the default otherwise */
    fun contextOf(stored: String?, validIds: Set<String>) =
        if (stored != null && stored in validIds) stored else LearningContexts.DEFAULT

    /** which visible pins belong to another context, and which hidden ones to [current] */
    fun partition(
        visible: List<ClipboardHistoryEntry>,
        hidden: List<ClipboardHistoryEntry>,
        contexts: Map<Long, String>,
        validIds: Set<String>,
        current: String,
    ): Pair<List<ClipboardHistoryEntry>, List<ClipboardHistoryEntry>> = Pair(
        visible.filter { it.isPinned && contextOf(contexts[it.id], validIds) != current },
        hidden.filter { contextOf(contexts[it.id], validIds) == current },
    )
}
