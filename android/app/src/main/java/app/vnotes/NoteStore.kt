package app.vnotes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID

data class Note(val id: String, val text: String, val created: Long, val updated: Long, val deleted: Long?) {
    fun json() = JSONObject().put("id",id).put("text",text).put("createdAt",created).put("updatedAt",updated).put("deletedAt",deleted ?: JSONObject.NULL)
}
class NoteStore(context: Context) : SQLiteOpenHelper(context,"vnotes.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY,text TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,deleted INTEGER)") }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("Unsupported database upgrade") }
    fun all(): List<Note> = readableDatabase.rawQuery("SELECT id,text,created,updated,deleted FROM notes ORDER BY created DESC,rowid DESC",null).use { c -> buildList {
        while(c.moveToNext()) add(Note(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3),if(c.isNull(4))null else c.getLong(4)))
    } }
    fun save(note: Note) {
        val v=ContentValues().apply {put("id",note.id);put("text",note.text);put("created",note.created);put("updated",note.updated);if(note.deleted==null)putNull("deleted")else put("deleted",note.deleted)}
        check(writableDatabase.insertWithOnConflict("notes",null,v,SQLiteDatabase.CONFLICT_REPLACE)!=-1L) {"Could not save note"}
    }
    fun add(text: String): Note {val now=System.currentTimeMillis();return Note(UUID.randomUUID().toString(),text,now,now,null).also(::save)}
    fun erase(id: String) {writableDatabase.delete("notes","id=?",arrayOf(id))}
    fun emptyDone() {writableDatabase.delete("notes","deleted IS NOT NULL",null)}
    fun merge(notes: List<Note>): Int {
        val existing=all().map{it.id}.toSet();val fresh=notes.filter{it.id !in existing};val db=writableDatabase
        db.beginTransaction();try{fresh.forEach(::save);db.setTransactionSuccessful()}finally{db.endTransaction()};return fresh.size
    }
    companion object {
        fun parseBackup(text: String): List<Note> {
            require(text.length<=10_000_000){"Backup exceeds 10 MB"};val json=JSONObject(text)
            require(json.optString("format")=="vnotes" && json.optInt("version")==1){"Unsupported backup format"}
            val rows=json.getJSONArray("notes");require(rows.length()<=50000){"Too many notes"};val ids=HashSet<String>()
            return (0 until rows.length()).map {i -> val n=rows.getJSONObject(i)
                fun stamp(key: String):Long {val v=n.getLong(key);require(v>0 && v<8640000000000000L){"Invalid timestamp"};return v}
                val id=n.getString("id");val body=n.getString("text");require(id.isNotBlank()&&ids.add(id)&&body.isNotBlank()&&body.length<=20000){"Invalid or duplicate note"}
                Note(id,body,stamp("createdAt"),stamp("updatedAt"),if(n.isNull("deletedAt"))null else stamp("deletedAt"))
            }
        }
    }
}

data class PendingAudio(val audio: FloatArray, val target: String?, val id:String=UUID.randomUUID().toString())
class RetryStore(context: Context) {
    private val file=AtomicFile(File(context.filesDir,"pending-audio.bin"))
    fun save(pending: PendingAudio) {
        val stream=file.startWrite()
        try {val out=DataOutputStream(BufferedOutputStream(stream));out.writeInt(0x564E4131);out.writeUTF(pending.id);out.writeUTF(pending.target ?: "");out.writeInt(pending.audio.size);pending.audio.forEach{out.writeFloat(it)};out.flush();file.finishWrite(stream)}
        catch(e:Throwable){file.failWrite(stream);throw e}
    }
    fun load(): PendingAudio? {
        if(!file.baseFile.exists())return null
        return DataInputStream(file.openRead()).use { input -> require(input.readInt()==0x564E4131){"Unsupported saved audio"};val noteId=input.readUTF();val id=input.readUTF();val size=input.readInt();require(size in 1..AudioCapture.RATE*120){"Invalid saved audio"};PendingAudio(FloatArray(size){input.readFloat()},id.ifEmpty{null},noteId) }
    }
    fun clear()=file.delete()
}
