package local.qqmusic.relay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal object RelayLogs {
    data class Entry(val id: Long, val time: Long, val level: String, val target: String, val event: String)

    val changes = MutableStateFlow(0)
    private val writer = Executors.newSingleThreadExecutor()
    private var database: LogDatabase? = null

    @Synchronized
    private fun database(context: Context): LogDatabase =
        database ?: LogDatabase(context.applicationContext).also { database = it }

    fun record(context: Context, level: String, target: RelayTarget?, event: String) {
        val time = System.currentTimeMillis()
        val application = context.applicationContext
        val targetName = target?.let { "${it.label} · ${it.packageName}" } ?: "Relay"
        Log.println(if (level == "Error") Log.WARN else Log.INFO, "MediaServiceRelay", "$targetName: $event")
        writer.execute {
            try {
                val db = database(application).writableDatabase
                db.insertOrThrow("events", null, ContentValues().apply {
                    put("time", time)
                    put("level", level)
                    put("target", targetName)
                    put("event", event)
                })
                db.execSQL("DELETE FROM events WHERE id <= (SELECT MAX(id) FROM events) - 500")
                changes.value++
            } catch (failure: Exception) {
                Log.w("MediaServiceRelay", "Could not save relay log", failure)
            }
        }
    }

    fun query(context: Context, filter: String): List<Entry> {
        val needle = filter.trim()
        return database(context).readableDatabase.rawQuery(
            """SELECT id,time,level,target,event FROM events
                WHERE instr(lower(target || ' ' || level || ' ' || event), lower(?)) > 0 OR ? = ''
                ORDER BY id DESC LIMIT 500""", arrayOf(needle, needle)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Entry(cursor.getLong(0), cursor.getLong(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4)))
            }
        }
    }

    suspend fun clear(context: Context): Boolean = suspendCancellableCoroutine { continuation ->
        writer.execute {
            val cleared = runCatching {
                database(context).writableDatabase.delete("events", null, null)
                changes.value++
            }.isSuccess
            continuation.resume(cleared)
        }
    }

    fun export(context: Context, destination: Uri) {
        val entries = query(context, "").asReversed()
        val timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
        val output = checkNotNull(context.contentResolver.openOutputStream(destination, "wt"))
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("Media Service Relay connection logs")
            entries.forEach { entry ->
                writer.appendLine("${timestamp.format(Instant.ofEpochMilli(entry.time))}" +
                    " [${entry.level}] ${entry.target}: ${entry.event}")
            }
        }
    }

    private class LogDatabase(context: Context) : SQLiteOpenHelper(context, "relay_events.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL,
                level TEXT NOT NULL, target TEXT NOT NULL, event TEXT NOT NULL
            )""")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
