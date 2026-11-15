package de.geburtstagsassistent

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.*
import java.net.URLEncoder
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity:Activity(){
 private lateinit var db:Store
 private lateinit var main:LinearLayout
 private lateinit var screen:LinearLayout
 private var tab=0
 private val teal=Color.rgb(16,105,115)
 private val dateFormat=DateTimeFormatter.ofPattern("dd.MM.yyyy",Locale.GERMANY)
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  db=Store(this)
  if(!prefs().contains("activated"))prefs().edit().putString("activated",LocalDate.now().toString()).apply()
  main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(247,249,251))}
  setContentView(main)
  draw()
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),8)
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),9)
  Reminders.schedule(this)
 }
 override fun onResume(){super.onResume();if(::db.isInitialized)sync(false)}
 override fun onDestroy(){db.close();super.onDestroy()}
 private fun prefs()=getSharedPreferences("prefs",MODE_PRIVATE)
 private fun t(s:String,big:Boolean=false)=TextView(this).apply{text=s;textSize=if(big)20f else 15f;setTextColor(Color.rgb(35,53,65));setPadding(12,12,12,12)}
 private fun button(s:String,fn:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{fn()}}
 private fun field(hint:String,initial:String="")=EditText(this).apply{this.hint=hint;setText(initial);setSingleLine(true)}
 private fun panel(title:String,detail:String="",fn:(()->Unit)?=null){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(10,8,10,8);setBackgroundColor(Color.WHITE);elevation=2f}
  b.addView(t(title,true));if(detail.isNotBlank())b.addView(t(detail))
  if(fn!=null)b.setOnClickListener{fn()}
  screen.addView(b,LinearLayout.LayoutParams(-1,-2).apply{setMargins(9,7,9,7)})
 }
 private fun draw(){
  main.removeAllViews()
  main.addView(t("🎂 Geburtstags-Assistent",true).apply{setBackgroundColor(Color.rgb(213,238,237))})
  val scroll=ScrollView(this)
  screen=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(10,12,10,12)}
  scroll.addView(screen)
  main.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  when(tab){0->home();1->calendar();2->persons();3->openTasks();else->settings()}
  val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("Start","Kalender","Personen","Offen","Optionen").forEachIndexed{i,s->
   nav.addView(button(s){tab=i;draw()}.apply{textSize=10f;setTextColor(teal);setPadding(0,0,0,0)},LinearLayout.LayoutParams(0,65,1f))
  }
  main.addView(nav)
 }
 private fun sync(showToast:Boolean){
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return
  thread{
   val n=runCatching{Store(applicationContext).use{Contacts.sync(applicationContext,it)}}.getOrDefault(0)
   runOnUiThread{if(!isFinishing&&!isDestroyed){if(showToast)Toast.makeText(this,"$n neue Kontakte",Toast.LENGTH_SHORT).show();draw()}}
  }
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==9&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)sync(true)
 }
 private fun names()=db.people().associateBy{it.id}
 private fun display(e:Event):String{
  val ps=names()
  val a=ps[e.a]?.name?:e.title
  return if(e.b==null)a else a+" & "+(ps[e.b]?.name?:"?")
 }
 private fun due(e:Event):LocalDate{
  val now=LocalDate.now()
  val d=Dates.occurrence(e.date,now.year)
  return if(d.isBefore(now))Dates.occurrence(e.date,now.year+1) else d
 }
 private fun missing()=db.people().filter{it.birthday==null&&!it.ignored}
 private fun home(){
  screen.addView(button("＋ Person oder Geburtstag hinzufügen"){newPerson()})
  screen.addView(button("＋ Hochzeitstag / besonderen Tag hinzufügen"){newEvent()})
  panel("🔔 Offene Erinnerungen: "+Reminders.open(this,db).size,"Antippen zum Bearbeiten"){tab=3;draw()}
  panel("🧾 Fehlende Geburtstage: "+missing().size,"Kontakte ohne Geburtstag"){missingList()}
  db.events().filter{it.enabled}.sortedBy{due(it)}.take(25).forEach{e->
   val extra=Dates.years(e.date,due(e).year)?.let{" · $it Jahre"}?:""
   panel((if(e.type=="BIRTHDAY")"🎂 " else "💝 ")+display(e),dateFormat.format(due(e))+extra){detail(e)}
  }
 }
 private fun calendar(){
  screen.addView(t("Nächste Ereignisse",true))
  db.events().sortedBy{due(it)}.forEach{e->
   val day=due(e)
   val holiday=Dates.holidays.any{!day.isBefore(it.from)&&!day.isAfter(it.until)}
   panel(dateFormat.format(day)+" · "+display(e),(if(e.type=="WEDDING")"Hochzeitstag" else if(e.type=="BIRTHDAY")"Geburtstag" else e.title)+(if(holiday)" · Ferien" else "")){detail(e)}
  }
 }
 private fun persons(){
  screen.addView(button("＋ Person anlegen"){newPerson()})
  screen.addView(button("⟳ Kontakte synchronisieren"){sync(true)})
  screen.addView(button("🧾 Geburtstage fehlen ("+missing().size+")"){missingList()})
  screen.addView(button("🔗 Zusammenführung prüfen"){duplicates()})
  db.people().forEach{p->panel(p.name,(p.birthday?:"Kein Geburtstag")+(if(p.muted)" · Stumm" else "")){personDetail(p)}}
 }
 private fun personDetail(p:Person){
  val acts=listOf("Geburtstag ergänzen / ändern","Stumm schalten / aktivieren","In Android-Kontakten bearbeiten","Manuelle Verknüpfungen prüfen")
  AlertDialog.Builder(this).setTitle(p.name).setItems(acts.toTypedArray()){_,i->when(i){
   0->birthday(p)
   1->{db.updatePerson(p.copy(muted=!p.muted));draw()}
   2->contactEditor(p)
   3->duplicates()
  }}.show()
 }
 private fun missingList(){
  val entries=missing()
  if(entries.isEmpty()){message("Keine fehlenden Geburtstage gefunden.");return}
  AlertDialog.Builder(this).setTitle("Fehlende Geburtstage ("+entries.size+")").setItems(entries.map{it.name}.toTypedArray()){_,i->
   val p=entries[i];AlertDialog.Builder(this).setTitle(p.name).setItems(arrayOf("Geburtstag eintragen","Dauerhaft ignorieren","Kontakt öffnen")){_,which->
    when(which){0->birthday(p);1->{db.updatePerson(p.copy(ignored=true));draw()};2->contactEditor(p)}
   }.show()
  }.show()
 }
 private fun newPerson(){
  val name=field("Vorname und Name")
  val phone=field("Telefon (optional)")
  val date=field("Geburtstag TT.MM. oder TT.MM.JJJJ")
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,8,18,8);addView(name);addView(phone);addView(date)}
  AlertDialog.Builder(this).setTitle("Person hinzufügen").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern"){_,_->
   if(name.text.isBlank()){message("Bitte Namen angeben");return@setPositiveButton}
   val bd=if(date.text.isBlank())null else runCatching{Dates.parse(date.text.toString())}.getOrElse{message(it.message?:"Ungültiges Datum");return@setPositiveButton}
   val id=db.addPerson(name.text.toString().trim(),phone.text.toString().trim().ifBlank{null},null,bd)
   if(bd!=null&&id>0)db.addEvent("BIRTHDAY",id,null,bd,"")
   draw()
   if(bd!=null)askGoogle(name.text.toString().trim(),bd,phone.text.toString().trim())
  }.show()
 }
 private fun birthday(p:Person){
  val raw=p.birthday?.let{ if(it.startsWith("--"))it.substring(5,7)+"."+it.substring(2,4)+"." else it.substring(8,10)+"."+it.substring(5,7)+"."+it.substring(0,4) }?:""
  val f=field("TT.MM. oder TT.MM.JJJJ",raw)
  AlertDialog.Builder(this).setTitle("Geburtstag: "+p.name).setView(f).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern"){_,_->
   val bd=runCatching{Dates.parse(f.text.toString())}.getOrElse{message(it.message?:"Fehler");return@setPositiveButton}
   db.updatePerson(p.copy(birthday=bd))
   val old=db.events().firstOrNull{it.type=="BIRTHDAY"&&it.a==p.id}
   if(old==null)db.addEvent("BIRTHDAY",p.id,null,bd,"") else db.change(old.copy(date=bd))
   draw()
   askGoogle(p.name,bd,p.phone?:"",p)
  }.show()
 }
 private fun askGoogle(name:String,bd:String,phone:String,p:Person?=null){
  AlertDialog.Builder(this).setTitle("Auch in Google Kontakte speichern?")
   .setMessage("Standard: In der App und in Google Kontakte. Für Google öffnet sich der Android-Kontakteditor. Die Speicherung dort erfordert deine Bestätigung.")
   .setPositiveButton("Kontakteditor öffnen"){_,_->contactEditor(p,name,bd,phone)}
   .setNegativeButton("Nur App",null).show()
 }
 private fun contactEditor(p:Person?,name:String=p?.name?:"",bd:String=p?.birthday?:"",phone:String=p?.phone?:""){
  try{
   val intent=if(p?.lookup!=null){
    val uri=ContactsContract.Contacts.getLookupUri(0,p.lookup)
    Intent(Intent.ACTION_EDIT,uri).apply{putExtra("finishActivityOnSaveCompleted",true)}
   }else{
    Intent(ContactsContract.Intents.Insert.ACTION).apply{
     type=ContactsContract.RawContacts.CONTENT_TYPE
     putExtra(ContactsContract.Intents.Insert.NAME,name)
     if(phone.isNotBlank())putExtra(ContactsContract.Intents.Insert.PHONE,phone)
     if(bd.isNotBlank()){
      val value=ContentValues().apply{
       put(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
       put(ContactsContract.CommonDataKinds.Event.TYPE,ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
       put(ContactsContract.CommonDataKinds.Event.START_DATE,bd)
      }
      putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA,arrayListOf(value))
     }
    }
   }
   startActivity(intent)
   if(p?.lookup!=null)message("Bitte im Kontakteditor das Datum prüfen und speichern. Bestehende Google-Kontakte werden nicht still verändert.")
  }catch(e:Exception){message("Kontakteditor nicht verfügbar: "+e.message)}
 }
 private fun newEvent(){
  val people=db.people()
  if(people.isEmpty()){message("Bitte zuerst eine Person anlegen.");return}
  val types=arrayOf("Hochzeitstag","Weiterer besonderer Tag")
  AlertDialog.Builder(this).setTitle("Art des Ereignisses").setItems(types){_,kind->
   val first=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,people.map{it.name})}
   val second=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Kein zweiter Kontakt")+people.map{it.name})}
   val date=field("Datum TT.MM.JJJJ")
   val title=field("Titel (z. B. Hochzeitstag)",if(kind==0)"Hochzeitstag" else "")
   val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(15,8,15,8);addView(t("Person 1"));addView(first);addView(t("Partner (optional)"));addView(second);addView(date);addView(title)}
   AlertDialog.Builder(this).setTitle("Ereignis hinzufügen").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern"){_,_->
    val d=runCatching{Dates.parse(date.text.toString())}.getOrElse{message(it.message?:"Datum fehlt");return@setPositiveButton}
    val a=people[first.selectedItemPosition].id
    val b=if(second.selectedItemPosition>0)people[second.selectedItemPosition-1].id else null
    if(a==b){message("Bitte zwei unterschiedliche Personen wählen.");return@setPositiveButton}
    db.addEvent(if(kind==0)"WEDDING" else "OTHER",a,b,d,title.text.toString())
    draw()
   }.show()
  }.show()
 }
 private fun detail(e:Event){
  val year=LocalDate.now().year
  val occ=if(Dates.occurrence(e.date,year).isAfter(LocalDate.now()))year-1 else year
  val choices=arrayOf("💬 Glückwunsch per WhatsApp vorbereiten","📋 Text kopieren","✓ Gratulation erledigt","✓ Persönlich nach den Ferien gratuliert","Erinnerung ein-/ausschalten","Ferien-Nachholung ein-/ausschalten")
  AlertDialog.Builder(this).setTitle(display(e)+" · "+e.date).setItems(choices){_,i->when(i){
   0->whatsapp(e,occ)
   1->{val msg=Dates.greeting(display(e),e.type,e.date,occ,occ);val clip=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager;clip.setPrimaryClip(ClipData.newPlainText("Glückwunsch",msg));message("Text kopiert")}
   2->{db.complete(e.id,occ,"DAY");draw()}
   3->{db.complete(e.id,occ,"FOLLOW");draw()}
   4->{db.change(e.copy(enabled=!e.enabled));draw()}
   5->{db.change(e.copy(follow=!e.follow));draw()}
  }}.show()
 }
 private fun whatsapp(e:Event,occ:Int){
  val p=db.person(e.a);val msg=Dates.greeting(display(e),e.type,e.date,occ,occ)
  val digits=p?.phone?.filter{it.isDigit()}.orEmpty()
  val normalized=if(digits.startsWith("00"))digits.drop(2) else if(digits.startsWith("0"))"49"+digits.drop(1) else digits
  val url="https://wa.me/"+normalized+"?text="+URLEncoder.encode(msg,"UTF-8")
  try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(if(normalized.isNotEmpty())url else "https://wa.me/?text="+URLEncoder.encode(msg,"UTF-8"))))}
  catch(e:Exception){message("WhatsApp konnte nicht geöffnet werden: "+e.message)}
  message("Nach dem Versand bitte in der App 'Gratulation erledigt' markieren. Automatischer Versand findet nicht statt.")
 }
 private fun duplicates(){
  val ps=db.people();val candidates=mutableListOf<Pair<Person,Person>>()
  for(i in ps.indices)for(j in i+1 until ps.size){
   val a=ps[i];val b=ps[j]
   val sameName=a.name.trim().equals(b.name.trim(),true)
   val samePhone=a.phone?.filter{it.isDigit()}?.takeLast(9)?.let{n->n.length>=7&&n==b.phone?.filter{it.isDigit()}?.takeLast(9)}?:false
   if((samePhone||(sameName&&a.birthday!=null&&a.birthday==b.birthday))&&!db.dismissed(a.id,b.id))candidates.add(a to b)
  }
  if(candidates.isEmpty()){message("Keine eindeutigen Duplikatvorschläge");return}
  AlertDialog.Builder(this).setTitle("Mögliche Duplikate").setItems(candidates.map{it.first.name+" / "+it.second.name}.toTypedArray()){_,i->
   val(a,b)=candidates[i]
   AlertDialog.Builder(this).setTitle(a.name+" und "+b.name).setMessage("Die beiden Einträge wirklich innerhalb der App zusammenführen? Android-Kontakte bleiben unverändert.")
    .setPositiveButton("Zusammenführen"){_,_->db.merge(a.id,b.id);draw()}
    .setNegativeButton("Getrennt lassen"){_,_->db.dismiss(a.id,b.id)}
    .show()
  }.show()
 }
 private fun openTasks(){
  screen.addView(t("Offene Erinnerungen",true))
  val tasks=Reminders.open(this,db)
  if(tasks.isEmpty())panel("Alles erledigt 🎉")
  tasks.forEach{(e,kind,year)->panel((if(kind=="FOLLOW")"🎒 Nach Ferien: " else "🎂 ")+display(e),
   "Termin: "+dateFormat.format(if(kind=="FOLLOW")Dates.followup(Dates.occurrence(e.date,year),db.extraDays())?:Dates.occurrence(e.date,year) else Dates.occurrence(e.date,year))
   ){detail(e)}}
 }
 private fun settings(){
  screen.addView(t("Einstellungen",true))
  val hours=field("Erinnerungsstunden, z. B. 8,13,18",prefs().getString("hours","8,13,18")?:"8,13,18")
  screen.addView(hours)
  screen.addView(button("Erinnerungszeiten speichern"){
   val values=hours.text.toString().split(",").mapNotNull{it.trim().toIntOrNull()}
   if(values.isEmpty()||values.any{it !in 0..23})message("Stunden 0 bis 23 mit Komma trennen") else{
    prefs().edit().putString("hours",values.distinct().sorted().joinToString(",")).apply();Reminders.schedule(this);message("Gespeichert")
   }
  })
  screen.addView(button("Zusätzlichen schulfreien Tag eintragen"){
   val f=field("TT.MM.JJJJ")
   AlertDialog.Builder(this).setTitle("Schulfreier Tag").setView(f).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern"){_,_->
    runCatching{val d=Dates.parse(f.text.toString());db.addExtra(Dates.occurrence(d,d.take(4).toInt()));draw()}.onFailure{message("Bitte vollständiges Datum angeben")}
   }.show()
  })
  panel("Datenschutz","Kontakte verbleiben auf dem Gerät. Es gibt keinen automatischen WhatsApp-Versand.")
  panel("Ferienkalender","Bayerische Schulferien sind derzeit bis Sommer 2030 hinterlegt.")
 }
 private fun message(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
