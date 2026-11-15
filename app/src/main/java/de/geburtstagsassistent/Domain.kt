package de.geburtstagsassistent
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.*
import android.provider.ContactsContract
import java.time.*
import java.util.Locale

data class Person(val id:Long,val name:String,val phone:String?,val lookup:String?,val birthday:String?,val muted:Boolean=false,val ignored:Boolean=false)
data class Event(val id:Long,val type:String,val a:Long,val b:Long?,val date:String,val title:String,val enabled:Boolean=true,val follow:Boolean=true)
class Store(ctx:Context):SQLiteOpenHelper(ctx,"birthdays.db",null,1){
 override fun onCreate(d:SQLiteDatabase){
 d.execSQL("CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,lookup TEXT UNIQUE,birthday TEXT,muted INTEGER DEFAULT 0,ignored INTEGER DEFAULT 0)")
 d.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,a INTEGER,b INTEGER,date TEXT,title TEXT,enabled INTEGER DEFAULT 1,follow INTEGER DEFAULT 1)")
 d.execSQL("CREATE TABLE done(id INTEGER,yr INTEGER,kind TEXT,PRIMARY KEY(id,yr,kind))")
 d.execSQL("CREATE TABLE dismissed(a INTEGER,b INTEGER,PRIMARY KEY(a,b))")
 d.execSQL("CREATE TABLE school_free(day TEXT PRIMARY KEY)")
 }
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){error("Schema migration required")}
 fun people():List<Person> = readableDatabase.rawQuery("SELECT * FROM people ORDER BY name COLLATE NOCASE",null).use{c->buildList{while(c.moveToNext())add(Person(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5)!=0,c.getInt(6)!=0))}}
 fun events():List<Event> = readableDatabase.rawQuery("SELECT * FROM events",null).use{c->buildList{while(c.moveToNext())add(Event(c.getLong(0),c.getString(1),c.getLong(2),if(c.isNull(3))null else c.getLong(3),c.getString(4),c.getString(5),c.getInt(6)!=0,c.getInt(7)!=0))}}
 fun person(id:Long)=people().firstOrNull{it.id==id}
 fun addPerson(name:String,phone:String?,lookup:String?,birthday:String?):Long {
  val v=ContentValues().apply{put("name",name);put("phone",phone);put("lookup",lookup);put("birthday",birthday)}
  return writableDatabase.insertWithOnConflict("people",null,v,SQLiteDatabase.CONFLICT_IGNORE)
 }
 fun updatePerson(p:Person){
  val v=ContentValues().apply{put("name",p.name);put("phone",p.phone);put("lookup",p.lookup);put("birthday",p.birthday);put("muted",if(p.muted)1 else 0);put("ignored",if(p.ignored)1 else 0)}
  writableDatabase.update("people",v,"id=?",arrayOf(p.id.toString()))
 }
 fun addEvent(type:String,a:Long,b:Long?,date:String,title:String):Long{
  val v=ContentValues().apply{put("type",type);put("a",a);if(b!=null)put("b",b);put("date",date);put("title",title)}
  return writableDatabase.insert("events",null,v)
 }
 fun change(e:Event){
  val v=ContentValues().apply{put("date",e.date);put("title",e.title);put("enabled",if(e.enabled)1 else 0);put("follow",if(e.follow)1 else 0)}
  writableDatabase.update("events",v,"id=?",arrayOf(e.id.toString()))
 }
 fun done(id:Long,year:Int,kind:String)= readableDatabase.rawQuery("SELECT 1 FROM done WHERE id=? AND yr=? AND kind=?",arrayOf("$id","$year",kind)).use{it.moveToFirst()}
 fun complete(id:Long,year:Int,kind:String){val v=ContentValues().apply{put("id",id);put("yr",year);put("kind",kind)};writableDatabase.insertWithOnConflict("done",null,v,SQLiteDatabase.CONFLICT_IGNORE)}
 fun merge(a:Long,b:Long){
  if(a==b)return
  val x=person(a)?:return;val y=person(b)?:return
  writableDatabase.beginTransaction()
  try{
   val existingBirthday=events().firstOrNull{it.type=="BIRTHDAY"&&it.a==a}
   val otherBirthday=events().firstOrNull{it.type=="BIRTHDAY"&&it.a==b}
   if(existingBirthday!=null&&otherBirthday!=null){writableDatabase.delete("events","id=?",arrayOf(otherBirthday.id.toString()))}
   updatePerson(x.copy(phone=x.phone?:y.phone,lookup=x.lookup?:y.lookup,birthday=x.birthday?:y.birthday))
   writableDatabase.execSQL("UPDATE events SET a=? WHERE a=?",arrayOf(a,b))
   writableDatabase.execSQL("UPDATE events SET b=? WHERE b=?",arrayOf(a,b))
   writableDatabase.delete("people","id=?",arrayOf("$b"))
   writableDatabase.setTransactionSuccessful()
  }finally{writableDatabase.endTransaction()}
 }
 fun dismissed(a:Long,b:Long)=readableDatabase.rawQuery("SELECT 1 FROM dismissed WHERE a=? AND b=?",arrayOf("$a","$b")).use{it.moveToFirst()}
 fun dismiss(a:Long,b:Long){writableDatabase.execSQL("INSERT OR IGNORE INTO dismissed VALUES(?,?)",arrayOf(a,b))}
 fun extraDays():Set<LocalDate> = readableDatabase.rawQuery("SELECT day FROM school_free",null).use{c->buildSet{while(c.moveToNext())add(LocalDate.parse(c.getString(0))) }}
 fun addExtra(day:LocalDate){writableDatabase.execSQL("INSERT OR IGNORE INTO school_free VALUES(?)",arrayOf(day.toString()))}
}
object Dates{
 data class Break(val from:LocalDate,val until:LocalDate)
 private val encoded=listOf(
 "2026-08-03/2026-09-14","2026-11-02/2026-11-06","2026-12-24/2027-01-08",
 "2027-02-08/2027-02-12","2027-03-22/2027-04-02","2027-05-18/2027-05-28","2027-08-02/2027-09-13","2027-11-02/2027-11-05","2027-12-24/2028-01-07",
 "2028-02-28/2028-03-03","2028-04-10/2028-04-21","2028-06-06/2028-06-16","2028-07-31/2028-09-11","2028-10-30/2028-11-03","2028-12-23/2029-01-05",
 "2029-02-12/2029-02-16","2029-03-26/2029-04-06","2029-05-22/2029-06-01","2029-07-30/2029-09-10","2029-10-29/2029-11-02","2029-12-24/2030-01-04",
 "2030-03-04/2030-03-08","2030-04-15/2030-04-26","2030-06-11/2030-06-21","2030-07-29/2030-09-09")
 val holidays=encoded.map{val s=it.split("/");Break(LocalDate.parse(s[0]),LocalDate.parse(s[1]))}
 fun parse(s:String):String{
  val m=Regex("^(\\d{1,2})\\.(\\d{1,2})\\.?\\s*(\\d{4})?$").matchEntire(s.trim())?:error("Bitte TT.MM. oder TT.MM.JJJJ")
  val d=m.groupValues[1].toInt();val mo=m.groupValues[2].toInt();val y=m.groupValues[3].toIntOrNull()
  LocalDate.of(y?:2000,mo,d)
  return if(y==null)"--%02d-%02d".format(Locale.ROOT,mo,d) else "%04d-%02d-%02d".format(Locale.ROOT,y,mo,d)
 }
 fun contactDate(s:String):String?=runCatching {
  when{
   s.startsWith("--") -> { val m=s.substring(2,4).toInt();val d=s.substring(5,7).toInt();parse("$d.$m.") }
   Regex("\\d{4}-\\d\\d-\\d\\d").matches(s) -> {LocalDate.parse(s);s}
   else -> parse(s)
  }
 }.getOrNull()
 fun occurrence(s:String,y:Int):LocalDate{
  val m=if(s.startsWith("--"))s.substring(2,4).toInt() else s.substring(5,7).toInt()
  val d=s.takeLast(2).toInt()
  return if(m==2&&d==29&&!Year.isLeap(y.toLong()))LocalDate.of(y,2,28) else LocalDate.of(y,m,d)
 }
 fun years(s:String,y:Int):Int?=if(s.startsWith("--"))null else y-s.take(4).toInt()
 private fun easter(y:Int):LocalDate {
  val a=y%19;val b=y/100;val c=y%100;val d=b/4;val e=b%4;val f=(b+8)/25;val g=(b-f+1)/3
  val h=(19*a+b-d-g+15)%30;val i=c/4;val k=c%4;val l=(32+2*e+2*i-h-k)%7;val mm=(a+11*h+22*l)/451;val t=h+l-7*mm+114
  return LocalDate.of(y,t/31,t%31+1)
 }
 private fun publicHoliday(d:LocalDate):Boolean{
  val e=easter(d.year)
  return (d.monthValue to d.dayOfMonth) in setOf(1 to 1,1 to 6,5 to 1,10 to 3,11 to 1,12 to 25,12 to 26)
   || d==e.minusDays(2)||d==e.plusDays(1)||d==e.plusDays(39)||d==e.plusDays(50)||d==e.plusDays(60)
 }
 fun isSchool(d:LocalDate,extra:Set<LocalDate>)=d.dayOfWeek.value<6&&!publicHoliday(d)&&d !in extra&&holidays.none{!d.isBefore(it.from)&&!d.isAfter(it.until)}
 fun followup(d:LocalDate,extra:Set<LocalDate>):LocalDate?{
  val h=holidays.firstOrNull{!d.isBefore(it.from)&&!d.isAfter(it.until)}
  if(h==null&&d !in extra)return null
  var next=(h?.until?:d).plusDays(1)
  repeat(45){if(isSchool(next,extra))return next;next=next.plusDays(1)}
  return null
 }
 fun greeting(name:String,type:String,date:String,yr:Int,version:Int=0):String{
  val first=name.substringBefore(" ").ifBlank{"dir"}
  val years=years(date,yr)
  if(type=="WEDDING")return "Alles Liebe zum Hochzeitstag! 💕 Auf viele glückliche gemeinsame Jahre! 🥂"
  if(type!="BIRTHDAY")return "Alles Gute zu eurem besonderen Tag! 🎉"
  val starts=listOf("Liebe/r $first,","Hallo $first,","Herzlichen Glückwunsch, $first!","Alles Gute, $first!")
  val ends=listOf("Viel Glück, Gesundheit und Freude!","Ich wünsche dir einen wunderschönen Tag!","Lass dich heute feiern!","Auf ein Jahr voller schöner Erlebnisse!","Bleib gesund und glücklich!")
  return starts[Math.floorMod(version,starts.size)]+" Alles Gute"+(years?.let{" zum $it. Geburtstag"}?:" zum Geburtstag")+"! 🎂 "+ends[Math.floorMod(version/starts.size,ends.size)]
 }
}
