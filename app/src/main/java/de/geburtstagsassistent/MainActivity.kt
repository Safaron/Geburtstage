package de.geburtstagsassistent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.Executors

data class ViewState(
    val people:List<Person> = emptyList(),
    val events:List<Event> = emptyList(),
    val tasks:List<Triple<Event,String,Int>> = emptyList(),
    val syncReport:SyncReport? = null,
    val error:String? = null,
    val lastSync:String? = null,
    val running:Boolean = false
)

data class AppActions(
    val sync:()->Unit,
    val savePerson:(String,String,String,Boolean)->Unit,
    val birthday:(Person,String,Boolean)->Unit,
    val mute:(Person)->Unit,
    val ignore:(Person)->Unit,
    val editInContacts:(Person?)->Unit,
    val event:(String,Long,Long?,String,String)->Unit,
    val eventAction:(Event,String,Int)->Unit,
    val merge:(Person,Person)->Unit,
    val dismiss:(Person,Person)->Unit,
    val hours:(String)->Unit,
    val freeDay:(String)->Unit
)

class MainActivity:ComponentActivity(){
    private lateinit var db:Store
    private val worker=Executors.newSingleThreadExecutor()
    private var state by mutableStateOf(ViewState())
    private var lastAttempt=0L
    private val prefs by lazy{getSharedPreferences("prefs",MODE_PRIVATE)}
    private val requestNotifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    private val requestContacts=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)startSync() else state=state.copy(error="Kontakte nicht freigegeben. Bitte die Berechtigung erteilen.")
        askNotifications()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        db=Store(this)
        if(!prefs.contains("activated"))prefs.edit().putString("activated",LocalDate.now().toString()).apply()
        state=ViewState(lastSync=prefs.getString("last_result",null))
        refresh()
        setContent {
            ModernApp(state,AppActions(
                sync={startSync(true)},
                savePerson={name,phone,birthday,saveGoogle->savePerson(name,phone,birthday,saveGoogle)},
                birthday={person,date,saveGoogle->saveBirthday(person,date,saveGoogle)},
                mute={person->db.updatePerson(person.copy(muted=!person.muted));refresh()},
                ignore={person->db.updatePerson(person.copy(ignored=!person.ignored));refresh()},
                editInContacts={person->editContact(person)},
                event={type,a,b,date,title->addOccasion(type,a,b,date,title)},
                eventAction={event,action,year->onEventAction(event,action,year)},
                merge={a,b->db.merge(a.id,b.id);refresh()},
                dismiss={a,b->db.dismiss(a.id,b.id);refresh()},
                hours={hours->saveHours(hours)},
                freeDay={value->addFreeDay(value)}
            ),prefs.getString("hours","8,13,18")?:"8,13,18")
        }
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
        else {
            startSync()
            askNotifications()
        }
    }
    override fun onResume(){
        super.onResume()
        if(::db.isInitialized && checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
           && System.currentTimeMillis()-lastAttempt>60_000L)startSync()
    }
    private fun askNotifications(){
        if(Build.VERSION.SDK_INT>=33 &&
           checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun refresh(){
        if(!::db.isInitialized)return
        runCatching{
            state=state.copy(
                people=db.people(),events=db.events(),tasks=Reminders.open(this,db)
            )
        }.onFailure{state=state.copy(error="Lokale Datenbank: "+(it.message?:it.javaClass.simpleName))}
    }
    private fun startSync(manual:Boolean=false){
        if(state.running)return
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
            state=state.copy(error="Kontaktberechtigung fehlt: Bitte Android-Kontaktzugriff erlauben.")
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        lastAttempt=System.currentTimeMillis()
        state=state.copy(running=true,error=null)
        worker.execute {
            val result=runCatching{Store(applicationContext).use{Contacts.sync(applicationContext,it)}}
            runOnUiThread {
                if(isFinishing||isDestroyed)return@runOnUiThread
                result.onSuccess { report->
                    val summary="Geprüft: "+report.scanned+" · neu: "+report.added+
                        " · Geburtstage: "+report.birthdays+" · Hochzeitstage: "+report.anniversaries
                    prefs.edit().putString("last_result",summary).apply()
                    state=state.copy(running=false,syncReport=report,lastSync=summary,error=null)
                    refresh()
                    if(report.scanned==0)state=state.copy(error="Android hat 0 sichtbare Kontakte geliefert. Bitte prüfen, ob in der Kontakte-App die gewünschten Konten eingeblendet sind.")
                }.onFailure { e ->
                    state=state.copy(running=false,error="Synchronisierung fehlgeschlagen: "+e.javaClass.simpleName+
                      " – "+(e.message?:"Keine weiteren Informationen"))
                }
            }
        }
    }
    private fun parse(raw:String):String?=try{if(raw.isBlank())null else Dates.parse(raw)}
        catch(e:Exception){show("Datum ungültig: "+(e.message?:""));null}
    private fun savePerson(name:String,phone:String,birthday:String,saveGoogle:Boolean){
        if(name.isBlank()){show("Bitte einen Namen eingeben");return}
        if(birthday.isNotBlank()&&parse(birthday)==null)return
        val date=if(birthday.isBlank())null else Dates.parse(birthday)
        val id=db.addPerson(name.trim(),phone.trim().ifEmpty{null},null,date)
        if(id>0&&date!=null)db.addEvent("BIRTHDAY",id,null,date,"Geburtstag")
        refresh()
        if(date!=null&&saveGoogle)editContact(db.person(id)) else show("Person lokal gespeichert")
    }
    private fun saveBirthday(person:Person,value:String,saveGoogle:Boolean){
        val date=parse(value)?:return
        db.updatePerson(person.copy(birthday=date))
        val e=db.events().firstOrNull{it.a==person.id&&it.type=="BIRTHDAY"}
        if(e==null)db.addEvent("BIRTHDAY",person.id,null,date,"Geburtstag")
        else db.change(e.copy(date=date))
        refresh()
        if(saveGoogle)editContact(db.person(person.id)) else show("Geburtstag lokal gespeichert")
    }
    private fun addOccasion(type:String,a:Long,b:Long?,value:String,title:String){
        val date=parse(value)?:return
        if(a==b){show("Bitte zwei unterschiedliche Personen wählen.");return}
        db.addEvent(type,a,b,date,title)
        refresh()
    }
    private fun editContact(person:Person?){
        try{
            val i=if(person?.lookup!=null){
                val lookup=ContactsContract.Contacts.getLookupUri(0,person.lookup)
                Intent(Intent.ACTION_EDIT,lookup)
            }else {
                Intent(ContactsContract.Intents.Insert.ACTION).apply{
                    type=ContactsContract.RawContacts.CONTENT_TYPE
                    if(person!=null){
                        putExtra(ContactsContract.Intents.Insert.NAME,person.name)
                        if(!person.phone.isNullOrEmpty())putExtra(ContactsContract.Intents.Insert.PHONE,person.phone)
                        if(!person.birthday.isNullOrEmpty()){
                            val cv=ContentValues().apply {
                                put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                                put(ContactsContract.CommonDataKinds.Event.TYPE,ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                                put(ContactsContract.CommonDataKinds.Event.START_DATE,person.birthday)
                            }
                            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA,arrayListOf(cv))
                        }
                    }
                }
            }
            startActivity(i)
            show("Bitte den Google-Kontakt im Android-Editor prüfen und Speichern bestätigen.")
        }catch(e:Exception){show("Kontakteditor nicht verfügbar: "+(e.message?:""))}
    }
    private fun onEventAction(e:Event,action:String,year:Int){
        when(action){
            "WHATSAPP"->{
                val p=db.person(e.a)
                val msg=Dates.greeting((p?.name?:e.title),e.type,e.date,year,year)
                val digits=p?.phone.orEmpty().filter{it.isDigit()}
                val normalized=when{
                    digits.startsWith("00")->digits.drop(2)
                    digits.startsWith("0")->"49"+digits.drop(1)
                    else->digits
                }
                val url="https://wa.me/"+normalized+"?text="+URLEncoder.encode(msg,"UTF-8")
                try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
                catch(ex:Exception){show("WhatsApp kann nicht geöffnet werden: "+ex.message)}
            }
            "COPY"->{
                val msg=Dates.greeting(db.person(e.a)?.name?:e.title,e.type,e.date,year,year)
                val manager=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("Glückwunsch",msg))
                show("Text kopiert")
            }
            "DAY"->db.complete(e.id,year,"DAY")
            "FOLLOW"->db.complete(e.id,year,"FOLLOW")
            "MUTE"->db.change(e.copy(enabled=!e.enabled))
            "HOLIDAYS"->db.change(e.copy(follow=!e.follow))
        }
        refresh()
    }
    private fun saveHours(raw:String){
        val hours=raw.split(",").mapNotNull{it.trim().toIntOrNull()}
        if(hours.isEmpty()||hours.any{it !in 0..23}){show("Uhrzeiten: 8,13,18");return}
        prefs.edit().putString("hours",hours.distinct().sorted().joinToString(",")).apply()
        Reminders.schedule(this)
        show("Erinnerungszeiten gespeichert")
    }
    private fun addFreeDay(raw:String){
        val date=try{Dates.parse(raw)}catch(e:Exception){show("Bitte TT.MM.JJJJ eingeben");return}
        if(date.startsWith("--")){show("Das Jahr ist erforderlich.");return}
        db.addExtra(LocalDate.parse(date));show("Schulfreier Tag gespeichert")
    }
    private fun show(msg:String)=Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
    override fun onDestroy(){worker.shutdown();if(::db.isInitialized)db.close();super.onDestroy()}
}
