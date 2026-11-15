package de.geburtstagsassistent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink=Color(0xFF17334A)
private val Ocean=Color(0xFF146D79)
private val TealSoft=Color(0xFFE4F4F1)
private val Background=Color(0xFFF5F7FB)
private val Peach=Color(0xFFFFE9D8)
private val Amethyst=Color(0xFFEEE7FF)
private val Muted=Color(0xFF637688)
private val dayFormat=DateTimeFormatter.ofPattern("EEE, dd. MMM",Locale.GERMAN)

private fun next(e:Event):LocalDate{
    val today=LocalDate.now()
    val current=Dates.occurrence(e.date,today.year)
    return if(current.isBefore(today))Dates.occurrence(e.date,today.year+1) else current
}
private fun personName(e:Event,people:List<Person>):String{
    val byId=people.associateBy{it.id}
    val first=byId[e.a]?.name?:e.title
    return if(e.b!=null)first+" & "+(byId[e.b]?.name?:"Unbekannt") else first
}
private fun pairs(people:List<Person>):List<Pair<Person,Person>>{
    val byPhone=mutableMapOf<String,Person>()
    val byName=mutableMapOf<String,Person>()
    val result=linkedSetOf<Pair<Person,Person>>()
    people.forEach{p->
        val phone=p.phone.orEmpty().filter{it.isDigit()}.takeLast(9).takeIf{it.length>=7}
        val name=(p.name.trim().lowercase(Locale.ROOT)+"|"+(p.birthday?:"")).takeIf{p.birthday!=null}
        val match=phone?.let{byPhone[it]}?:name?.let{byName[it]}
        if(match!=null&&match.id!=p.id)result.add(match to p)
        if(phone!=null)byPhone.putIfAbsent(phone,p)
        if(name!=null)byName.putIfAbsent(name,p)
    }
    return result.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernApp(state:ViewState,actions:AppActions,storedHours:String){
    val scheme=lightColorScheme(
        primary=Ocean,onPrimary=Color.White,
        secondary=Ink,onSecondary=Color.White,
        background=Background,onBackground=Ink,
        surface=Color.White,onSurface=Ink,
        surfaceVariant=TealSoft,onSurfaceVariant=Ink
    )
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var addPerson by remember{mutableStateOf(false)}
    var addEvent by remember{mutableStateOf(false)}
    var selectedPerson by remember{mutableStateOf<Person?>(null)}
    var birthdayPerson by remember{mutableStateOf<Person?>(null)}
    var selectedEvent by remember{mutableStateOf<Event?>(null)}
    var selectedYear by remember{mutableIntStateOf(LocalDate.now().year)}
    var showDuplicates by remember{mutableStateOf(false)}
    var showFreeDay by remember{mutableStateOf(false)}

    MaterialTheme(colorScheme=scheme,typography=Typography()) {
        Scaffold(
            containerColor=Background,
            topBar={
                Surface(color=Background){
                    Row(
                        Modifier.fillMaxWidth().padding(start=20.dp,end=16.dp,top=14.dp,bottom=10.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ){
                        Column(Modifier.weight(1f)){
                            Text("GEBURTSTAGS-ASSISTENT",style=MaterialTheme.typography.labelSmall,
                                color=Ocean,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                            Text(listOf("Übersicht","Kalender","Personen","Offen","Einstellungen")[tab],
                                style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Ink)
                        }
                        FilledTonalIconButton(onClick=actions.sync,enabled=!state.running){
                            if(state.running)CircularProgressIndicator(modifier=Modifier.size(20.dp),strokeWidth=2.dp)
                            else Icon(Icons.Default.Sync,contentDescription="Kontakte jetzt synchronisieren")
                        }
                    }
                }
            },
            bottomBar={
                NavigationBar(containerColor=Color.White,tonalElevation=4.dp){
                    val items=listOf(
                        Triple("Start",Icons.Default.Home,0),
                        Triple("Kalender",Icons.Default.DateRange,1),
                        Triple("Personen",Icons.Default.People,2),
                        Triple("Offen",Icons.Default.Notifications,3),
                        Triple("Optionen",Icons.Default.Settings,4)
                    )
                    items.forEach{(label,icon,index)->
                        NavigationBarItem(selected=tab==index,onClick={tab=index},
                            icon={Icon(icon,contentDescription=label)},
                            label={Text(label,fontSize=10.sp,maxLines=1)},
                            alwaysShowLabel=true)
                    }
                }
            },
            floatingActionButton={
                if(tab in listOf(0,1,2))
                    ExtendedFloatingActionButton(
                        text={Text("Hinzufügen",fontWeight=FontWeight.SemiBold)},
                        icon={Icon(Icons.Default.Add,null)},
                        onClick={addPerson=true},
                        containerColor=Ocean,contentColor=Color.White
                    )
            }
        ){padding->
            Box(Modifier.fillMaxSize().padding(padding)){
                when(tab){
                    0->Overview(state,onNewEvent={addEvent=true},onEvent={e->
                        selectedEvent=e;selectedYear=next(e).year
                    },onMissing={tab=2},onOpen={tab=3},onSync=actions.sync)
                    1->EventCalendar(state,onEvent={e->selectedEvent=e;selectedYear=next(e).year})
                    2->PeopleScreen(state,onPerson={selectedPerson=it},onMissing={birthdayPerson=it},
                        onDuplicates={showDuplicates=true},onSync=actions.sync)
                    3->OpenScreen(state,onEvent={e,year->selectedEvent=e;selectedYear=year})
                    else->OptionsScreen(state,storedHours,onHours=actions.hours,onFreeDay={showFreeDay=true},onSync=actions.sync)
                }
            }
        }
        if(addPerson)AddPersonDialog(
            onDismiss={addPerson=false},
            onSave={name,phone,date,google-> actions.savePerson(name,phone,date,google);addPerson=false}
        )
        if(addEvent)AddEventDialog(state.people,onDismiss={addEvent=false},
            onSave={type,a,b,date,title->actions.event(type,a,b,date,title);addEvent=false})
        selectedPerson?.let{p->
            PersonDialog(p,onDismiss={selectedPerson=null},
                onBirthday={selectedPerson=null;birthdayPerson=p},
                onMute={actions.mute(p);selectedPerson=null},
                onIgnore={actions.ignore(p);selectedPerson=null},
                onEditContacts={actions.editInContacts(p);selectedPerson=null})
        }
        birthdayPerson?.let{p->BirthdayDialog(p,onDismiss={birthdayPerson=null},
            onSave={date,google->actions.birthday(p,date,google);birthdayPerson=null})}
        selectedEvent?.let{e->OccasionDialog(e,personName(e,state.people),selectedYear,
            onDismiss={selectedEvent=null},
            onAction={action->actions.eventAction(e,action,selectedYear);selectedEvent=null})}
        if(showDuplicates)DuplicatesDialog(pairs(state.people),
            onDismiss={showDuplicates=false},
            onMerge={a,b->actions.merge(a,b);showDuplicates=false},
            onDismissPair={a,b->actions.dismiss(a,b);showDuplicates=false})
        if(showFreeDay)FreeDayDialog(onDismiss={showFreeDay=false},
            onSave={date->actions.freeDay(date);showFreeDay=false})
    }
}

@Composable private fun SectionTitle(title:String,caption:String?=null){
    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp)){
        Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink)
        if(caption!=null)Text(caption,style=MaterialTheme.typography.bodySmall,color=Muted)
    }
}
@Composable private fun Tile(title:String,description:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    tint:Color=Ocean,onClick:()->Unit){
    ElevatedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.elevatedCardColors(containerColor=Color.White),
        elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha=.1f)),
                contentAlignment=Alignment.Center){
                Icon(icon,null,tint=tint)
            }
            Column(Modifier.weight(1f).padding(start=14.dp)){
                Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(description,style=MaterialTheme.typography.bodySmall,color=Muted,maxLines=2)
            }
            Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
@Composable private fun Stat(label:String,value:Int,color:Color,modifier:Modifier=Modifier){
    Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=color){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(value.toString(),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Ink)
            Text(label,style=MaterialTheme.typography.labelSmall,color=Ink)
        }
    }
}
@Composable private fun SyncBanner(state:ViewState,onSync:()->Unit){
    if(state.error!=null){
        Surface(color=Color(0xFFFFEEEE),shape=RoundedCornerShape(18.dp)){
            Column(Modifier.fillMaxWidth().padding(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.WarningAmber,null,tint=Color(0xFFB03534))
                    Spacer(Modifier.width(8.dp))
                    Text("Kontaktabgleich prüfen",fontWeight=FontWeight.Bold,color=Color(0xFF9D2928))
                }
                Spacer(Modifier.height(6.dp))
                Text(state.error,style=MaterialTheme.typography.bodySmall,color=Ink)
                TextButton(onClick=onSync){Text("Erneut versuchen")}
            }
        }
    }else if(state.running){
        Surface(color=TealSoft,shape=RoundedCornerShape(18.dp)){
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Kontakte werden eingelesen …",style=MaterialTheme.typography.bodyMedium)
            }
        }
    }else if(state.syncReport!=null){
        val r=state.syncReport
        Surface(color=TealSoft,shape=RoundedCornerShape(18.dp)){
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.CheckCircle,null,tint=Ocean)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text("Kontaktabgleich abgeschlossen",fontWeight=FontWeight.SemiBold)
                    Text(r.scanned.toString()+" Kontakte · "+r.added+" neu · "+r.birthdays+" Geburtstage",
                        style=MaterialTheme.typography.bodySmall,color=Muted)
                    if(r.invalidDates>0)Text(r.invalidDates.toString()+" Datumsangaben nicht erkannt",style=MaterialTheme.typography.bodySmall,color=Color(0xFF9D2928))
                }
            }
        }
    }else if(state.lastSync!=null){
        Surface(color=TealSoft,shape=RoundedCornerShape(18.dp)){
            Text(state.lastSync,Modifier.fillMaxWidth().padding(14.dp),style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun OccasionTile(e:Event,people:List<Person>,onClick:()->Unit){
    val date=next(e)
    val isBirthday=e.type=="BIRTHDAY"
    ElevatedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(21.dp),
        colors=CardDefaults.elevatedCardColors(containerColor=Color.White),
        elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)){
        Row(Modifier.padding(horizontal=14.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
            Surface(color=if(isBirthday)Peach else Amethyst,shape=RoundedCornerShape(15.dp)){
                Column(Modifier.width(58.dp).padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(date.dayOfMonth.toString(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Ink)
                    Text(date.month.name.take(3).lowercase().replaceFirstChar{it.uppercase()},
                        style=MaterialTheme.typography.labelSmall,color=Muted)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)){
                Text(personName(e,people),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                val age=Dates.years(e.date,date.year)
                Text((if(isBirthday)"🎂 Geburtstag" else "💝 "+(e.title.ifBlank{"Jahrestag"}))+
                    (if(age!=null&&age>=0)" · "+age+" Jahre" else ""),
                    style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
@Composable private fun Overview(state:ViewState,onNewEvent:()->Unit,onEvent:(Event)->Unit,
    onMissing:()->Unit,onOpen:()->Unit,onSync:()->Unit){
    val missing=state.people.count{it.birthday==null&&!it.ignored}
    val upcoming=state.events.filter{it.enabled}.sortedBy{next(it)}.take(12)
    LazyColumn(contentPadding=PaddingValues(start=17.dp,end=17.dp,bottom=105.dp,top=4.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Surface(color=Ocean,shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(23.dp)){
                    Text("Alles im Blick ✨",color=Color.White,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Geburtstage, Hochzeitstage und Glückwünsche – genau dann, wenn es wichtig ist.",
                        style=MaterialTheme.typography.bodyMedium,color=Color.White.copy(alpha=.9f))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick=onNewEvent,
                        colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Ocean),
                        shape=RoundedCornerShape(16.dp)){
                        Icon(Icons.Default.Favorite,null);Spacer(Modifier.width(8.dp))
                        Text("Jahrestag hinzufügen")
                    }
                }
            }
        }
        item{SyncBanner(state,onSync)}
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Stat("Personen",state.people.size,TealSoft,Modifier.weight(1f))
                Stat("Ohne Geburtstag",missing,Peach,Modifier.weight(1f))
                Stat("Offen",state.tasks.size,Amethyst,Modifier.weight(1f))
            }
        }
        item{Tile("Fehlende Geburtstage","Bei "+missing+" Kontakten fehlt das Datum",Icons.Default.PersonSearch,onClick=onMissing)}
        item{Tile("Offene Glückwünsche","Noch "+state.tasks.size+" Erinnerungen zu erledigen",Icons.Default.NotificationsActive,onClick=onOpen)}
        item{SectionTitle("Demnächst","Die nächsten besonderen Tage")}
        if(upcoming.isEmpty())item{EmptyState("Noch keine Termine","Starte einen Kontaktabgleich oder lege einen Geburtstag an.")}
        items(upcoming,key={it.id}){e->OccasionTile(e,state.people){onEvent(e)}}
    }
}
@Composable private fun EventCalendar(state:ViewState,onEvent:(Event)->Unit){
    val upcoming=state.events.sortedBy{next(it)}
    LazyColumn(contentPadding=PaddingValues(horizontal=17.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)){
        item{SectionTitle("Dein Kalender","Alle Geburtstage und Jahrestage, nach Datum sortiert")}
        if(upcoming.isEmpty())item{EmptyState("Noch keine Ereignisse","Mit dem Plus-Button neue Personen hinzufügen.")}
        items(upcoming,key={it.id}){e->OccasionTile(e,state.people){onEvent(e)}}
        item{Spacer(Modifier.height(100.dp))}
    }
}
@Composable private fun PeopleScreen(state:ViewState,onPerson:(Person)->Unit,onMissing:(Person)->Unit,
    onDuplicates:()->Unit,onSync:()->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    var missingOnly by rememberSaveable{mutableStateOf(false)}
    val filtered=state.people.filter{p->
        (!missingOnly || (p.birthday==null&&!p.ignored))&&
        (query.isBlank()||p.name.contains(query,true)||p.phone?.contains(query)==true)
    }
    LazyColumn(contentPadding=PaddingValues(horizontal=17.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{SyncBanner(state,onSync)}
        item{OutlinedTextField(value=query,onValueChange={query=it},
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            leadingIcon={Icon(Icons.Default.Search,null)},
            placeholder={Text("Nach Namen oder Nummer suchen")},
            shape=RoundedCornerShape(18.dp))}
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                FilterChip(selected=!missingOnly,onClick={missingOnly=false},label={Text("Alle ("+state.people.size+")")})
                FilterChip(selected=missingOnly,onClick={missingOnly=true},
                    label={Text("Geburtstag fehlt ("+state.people.count{it.birthday==null&&!it.ignored}+")")})
            }
        }
        item{
            OutlinedButton(onClick=onDuplicates,shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Default.Link,null)
                Spacer(Modifier.width(8.dp))
                Text("Mögliche Dubletten prüfen")
            }
        }
        if(filtered.isEmpty())item{EmptyState("Keine Personen gefunden",
            if(state.people.isEmpty())"Kontakte synchronisieren, um sie hier anzuzeigen." else "Versuche einen anderen Filter.")}
        items(filtered,key={it.id}){p->
            val missing=p.birthday==null
            Tile(p.name,if(missing)"Geburtstag fehlt · Tippen zum Ergänzen"
                else "Geburtstag: "+readableDate(p.birthday!!)+(if(p.muted)" · Stumm" else ""),
                if(missing)Icons.Default.PersonAddAlt else Icons.Default.Person,
                if(missing)Color(0xFFBC7041) else Ocean,
                onClick={if(missing)onMissing(p) else onPerson(p)})
        }
        item{Spacer(Modifier.height(100.dp))}
    }
}
@Composable private fun OpenScreen(state:ViewState,onEvent:(Event,Int)->Unit){
    LazyColumn(contentPadding=PaddingValues(17.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
        item{SectionTitle("Noch zu erledigen","Mit einem Tipp kannst du gratulieren oder erledigt markieren")}
        if(state.tasks.isEmpty())item{EmptyState("Alles erledigt 🎉","Momentan stehen keine Nachfass-Erinnerungen an.")}
        items(state.tasks,key={it.first.id.toString()+"-"+it.second+"-"+it.third}){(e,kind,year)->
            Tile(
                (if(kind=="FOLLOW")"🎒 Nach den Ferien: " else "🎂 ")+personName(e,state.people),
                (if(kind=="FOLLOW")"Persönliche Gratulation" else "Glückwunsch senden")+
                    " · "+readableDate(e.date),Icons.Default.Notifications,
                if(kind=="FOLLOW")Color(0xFF8B65BB) else Ocean
            ){onEvent(e,year)}
        }
    }
}
@Composable private fun OptionsScreen(state:ViewState,initialHours:String,
    onHours:(String)->Unit,onFreeDay:()->Unit,onSync:()->Unit){
    var hours by rememberSaveable{mutableStateOf(initialHours)}
    LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{SectionTitle("Benachrichtigungen","Wiederholen, bis du den Glückwunsch erledigt hast")}
        item{
            ElevatedCard(shape=RoundedCornerShape(22.dp),colors=CardDefaults.elevatedCardColors(containerColor=Color.White)){
                Column(Modifier.fillMaxWidth().padding(17.dp)){
                    Text("Erinnerungszeiten",fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value=hours,onValueChange={hours=it},modifier=Modifier.fillMaxWidth(),
                        label={Text("Stunden, durch Komma getrennt")},
                        supportingText={Text("Beispiel: 8,13,18")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick={onHours(hours)},shape=RoundedCornerShape(14.dp)){Text("Zeiten speichern")}
                }
            }
        }
        item{SectionTitle("Kontakte & Ferien")}
        item{Tile("Kontakte neu einlesen","Alle sichtbaren Android-, Google- und Samsung-Kontakte",
            Icons.Default.Sync,onClick=onSync)}
        item{Tile("Schulfreien Tag ergänzen","Zusätzliche Tage außerhalb der bayerischen Schulferien",
            Icons.Default.Event,onClick=onFreeDay)}
        item{SyncBanner(state,onSync)}
        item{SectionTitle("Datenschutz")}
        item{
            Surface(shape=RoundedCornerShape(20.dp),color=TealSoft){
                Column(Modifier.padding(18.dp)){
                    Text("Deine Daten bleiben auf dem Gerät",fontWeight=FontWeight.SemiBold)
                    Text("Kein automatischer WhatsApp-Versand. Änderungen an Google-Kontakten erfolgen nur nach Bestätigung im Android-Kontakteditor.",
                        style=MaterialTheme.typography.bodySmall,color=Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Ferienstand: bis Sommer 2030. Danach ist ein Update erforderlich.",
                        style=MaterialTheme.typography.bodySmall,color=Muted)
                }
            }
        }
        item{Spacer(Modifier.height(100.dp))}
    }
}
@Composable private fun EmptyState(title:String,description:String){
    Column(Modifier.fillMaxWidth().padding(vertical=36.dp,horizontal=18.dp),
        horizontalAlignment=Alignment.CenterHorizontally){
        Icon(Icons.Default.AutoAwesome,null,tint=Ocean,modifier=Modifier.size(38.dp))
        Spacer(Modifier.height(12.dp))
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(description,style=MaterialTheme.typography.bodySmall,color=Muted)
    }
}
private fun readableDate(raw:String):String{
    return if(raw.startsWith("--"))raw.substring(5,7)+"."+raw.substring(2,4)+"."
    else if(raw.length==10)raw.substring(8,10)+"."+raw.substring(5,7)+"."+raw.substring(0,4)
    else raw
}
