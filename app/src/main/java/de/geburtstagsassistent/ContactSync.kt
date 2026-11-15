package de.geburtstagsassistent
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.database.sqlite.SQLiteDatabase

data class SyncReport(val scanned:Int,val added:Int,val updated:Int,val birthdays:Int,val anniversaries:Int,val invalidDates:Int,val dataRows:Int)

object Contacts {
    private data class Entry(val id:Long,val lookup:String,val name:String,
        var phone:String?=null,var birthday:String?=null,var anniversary:String?=null)

    fun sync(ctx:Context,store:Store):SyncReport {
        if(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Kontaktberechtigung fehlt. Bitte Zugriff auf Kontakte erlauben.")
        val resolver=ctx.contentResolver
        val rows=LinkedHashMap<Long,Entry>()
        val cols=arrayOf(ContactsContract.Contacts._ID,ContactsContract.Contacts.LOOKUP_KEY,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        resolver.query(ContactsContract.Contacts.CONTENT_URI,cols,null,null,null)?.use { c ->
            val id=c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookup=c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val name=c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while(c.moveToNext()){
                val n=c.getString(name)?.trim().orEmpty()
                if(n.isNotEmpty()){
                    val cid=c.getLong(id)
                    rows[cid]=Entry(cid,c.getString(lookup).orEmpty().ifBlank{"contact-$cid"},n)
                }
            }
        }?:throw IllegalStateException("Die Kontakte-App liefert keine Daten (Providerantwort null).")
        val dataCols=arrayOf(ContactsContract.Data.CONTACT_ID,ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,ContactsContract.Data.DATA2)
        val selection=ContactsContract.Data.MIMETYPE+"=? OR "+ContactsContract.Data.MIMETYPE+"=?"
        val args=arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        var bad=0
        var dataRows=0
        resolver.query(ContactsContract.Data.CONTENT_URI,dataCols,selection,args,null)?.use { c ->
            val contactId=c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mime=c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val value=c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val type=c.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            while(c.moveToNext()){
                dataRows++
                val e=rows[c.getLong(contactId)]?:continue
                val raw=c.getString(value)?:continue
                when(c.getString(mime)){
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        if(e.phone.isNullOrBlank())e.phone=raw
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val eventType=c.getInt(type)
                        if(eventType!=ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY &&
                           eventType!=ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY)continue
                        val parsed=Dates.contactDate(raw)
                        if(parsed==null){bad++;continue}
                        if(eventType==ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)e.birthday=parsed
                        else e.anniversary=parsed
                    }
                }
            }
        }?:throw IllegalStateException("Geburtstagsdaten konnten nicht aus den Kontakten gelesen werden.")
        val previous=store.people().filter{it.lookup!=null}.associateBy{it.lookup!!}
        val events=store.events().groupBy{it.a to it.type}
        val db=store.writableDatabase
        var added=0
        var updated=0
        var birthdays=0
        var anniversaries=0
        db.beginTransaction()
        try{
            for(e in rows.values){
                val prior=previous[e.lookup]
                val id=if(prior==null){
                    val v=ContentValues().apply{
                        put("name",e.name);put("phone",e.phone);put("lookup",e.lookup);put("birthday",e.birthday)
                    }
                    val newId=db.insertWithOnConflict("people",null,v,SQLiteDatabase.CONFLICT_IGNORE)
                    if(newId>0){added++;newId}
                    else db.query("people",arrayOf("id"),"lookup=?",arrayOf(e.lookup),null,null,null).use{
                        if(it.moveToFirst())it.getLong(0) else -1L
                    }
                }else{
                    val v=ContentValues().apply{
                        put("name",e.name);put("phone",e.phone?:prior.phone);put("birthday",e.birthday?:prior.birthday)
                    }
                    db.update("people",v,"id=?",arrayOf(prior.id.toString()))
                    updated++;prior.id
                }
                if(id<=0)continue
                if(e.birthday!=null){
                    birthdays++
                    upsertEvent(db,events[id to "BIRTHDAY"]?.firstOrNull(),"BIRTHDAY",id,e.birthday!!,"Geburtstag")
                }
                if(e.anniversary!=null){
                    anniversaries++
                    upsertEvent(db,events[id to "WEDDING"]?.firstOrNull(),"WEDDING",id,e.anniversary!!,"Hochzeitstag")
                }
            }
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        return SyncReport(rows.size,added,updated,birthdays,anniversaries,bad,dataRows)
    }

    private fun upsertEvent(db:SQLiteDatabase,old:Event?,type:String,person:Long,date:String,title:String){
        if(old==null){
            val v=ContentValues().apply{put("type",type);put("a",person);put("date",date);put("title",title)}
            db.insert("events",null,v)
        }else if(old.date!=date){
            db.update("events",ContentValues().apply{put("date",date)},"id=?",arrayOf(old.id.toString()))
        }
    }
}
