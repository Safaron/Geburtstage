package de.geburtstagsassistent

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import java.time.*

object Reminders {
 private const val CHANNEL="birthdays"
 fun hours(ctx:Context):List<Int>{
  val raw=ctx.getSharedPreferences("prefs",Context.MODE_PRIVATE).getString("hours","8,13,18")?:"8,13,18"
  return raw.split(",").mapNotNull{it.trim().toIntOrNull()}.filter{it in 0..23}.distinct().sorted().ifEmpty{listOf(8,13,18)}
 }
 fun open(ctx:Context,db:Store,now:LocalDate=LocalDate.now()):List<Triple<Event,String,Int>>{
  val persons=db.people().associateBy{it.id}
  val extras=db.extraDays()
  val activated=runCatching{LocalDate.parse(ctx.getSharedPreferences("prefs",Context.MODE_PRIVATE).getString("activated",now.toString()))}.getOrDefault(now)
  val results=mutableListOf<Triple<Event,String,Int>>()
  db.events().filter{it.enabled&&persons[it.a]?.muted!=true}.forEach{e->
   for(year in (now.year-1)..now.year){
    val birth=runCatching{Dates.occurrence(e.date,year)}.getOrNull()?:continue
    if(birth.isAfter(now)||java.time.temporal.ChronoUnit.DAYS.between(birth,now)>366)continue
    if(!birth.isBefore(activated)&&!db.done(e.id,year,"DAY"))results.add(Triple(e,"DAY",year))
    val follow=if(e.follow)Dates.followup(birth,extras)else null
    if(follow!=null&&!follow.isAfter(now)&&!follow.isBefore(activated)&&!db.done(e.id,year,"FOLLOW"))results.add(Triple(e,"FOLLOW",year))
   }
  }
  return results
 }
 fun schedule(ctx:Context){
  val manager=ctx.getSystemService(AlarmManager::class.java)
  val times=hours(ctx)
  val now=LocalDateTime.now()
  val next=times.map{now.toLocalDate().atTime(it,0)}.firstOrNull{it.isAfter(now.plusSeconds(5))}
    ?:now.toLocalDate().plusDays(1).atTime(times.first(),0)
  val at=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val pi=PendingIntent.getBroadcast(ctx,101,Intent(ctx,ReminderReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)
 }
 fun show(ctx:Context){
  val db=Store(ctx)
  try{
   if(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)runCatching{Contacts.sync(ctx,db)}
   if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
   val manager=ctx.getSystemService(NotificationManager::class.java)
   manager.createNotificationChannel(NotificationChannel(CHANNEL,"Geburtstage & Jubiläen",NotificationManager.IMPORTANCE_HIGH))
   val persons=db.people().associateBy{it.id}
   for((e,kind,year) in open(ctx,db)){
    val name=persons[e.a]?.name?:e.title
    val label=if(kind=="FOLLOW")"🎒 Nach den Ferien gratulieren: $name" else if(e.type=="BIRTHDAY")"🎂 Achtung, Geburtstag: $name" else "💝 Besonderer Tag: $name"
    val pi=PendingIntent.getActivity(ctx,(e.id*10+(if(kind=="FOLLOW")1 else 0)).toInt(),Intent(ctx,MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val message=if(kind=="FOLLOW")"Diese Person hatte in den Schulferien einen besonderen Tag." else "Glückwunsch vorbereiten oder als erledigt markieren."
    val notification=Notification.Builder(ctx,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_my_calendar)
      .setContentTitle(label).setContentText(message).setAutoCancel(true).setContentIntent(pi)
      .setCategory(Notification.CATEGORY_REMINDER).build()
    manager.notify((e.id*10+(if(kind=="FOLLOW")1 else 0)).toInt(),notification)
   }
  }finally{db.close()}
 }
}
class ReminderReceiver:BroadcastReceiver(){
 override fun onReceive(ctx:Context,intent:Intent){
  val pending=goAsync()
  Thread{
   try{Reminders.show(ctx.applicationContext)}
   finally{Reminders.schedule(ctx.applicationContext);pending.finish()}
  }.start()
 }
}
class BootReceiver:BroadcastReceiver(){
 override fun onReceive(ctx:Context,intent:Intent){Reminders.schedule(ctx.applicationContext)}
}
