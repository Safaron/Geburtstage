package de.geburtstagsassistent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun AddPersonDialog(onDismiss:()->Unit,onSave:(String,String,String,Boolean)->Unit){
    var name by remember{mutableStateOf("")}
    var phone by remember{mutableStateOf("")}
    var date by remember{mutableStateOf("")}
    var google by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Person hinzufügen")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(value=name,onValueChange={name=it},label={Text("Vorname und Name *")},singleLine=true)
                OutlinedTextField(value=phone,onValueChange={phone=it},label={Text("Telefon (optional)")},singleLine=true)
                OutlinedTextField(value=date,onValueChange={date=it},
                    label={Text("Geburtstag (optional)")},placeholder={Text("TT.MM.JJJJ oder TT.MM.")},singleLine=true)
                RowWithCheckbox(google,{google=it},"Auch in Google Kontakte speichern")
                Text("Bei Auswahl öffnet sich der Android-Kontakteditor. Bitte dort das Google-Konto wählen und das Speichern bestätigen.",
                    style=MaterialTheme.typography.bodySmall)
                if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={Button(onClick={
            if(name.isBlank()){error="Bitte einen Namen eingeben.";return@Button}
            if(date.isNotBlank()&&runCatching{Dates.parse(date)}.isFailure){error="Geburtstag bitte als TT.MM. oder TT.MM.JJJJ eingeben.";return@Button}
            onSave(name.trim(),phone.trim(),date.trim(),google)
        }){Text("Speichern")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}}
    )
}
@Composable
fun BirthdayDialog(person:Person,onDismiss:()->Unit,onSave:(String,Boolean)->Unit){
    val initial=person.birthday?.let{
        if(it.startsWith("--"))it.substring(5,7)+"."+it.substring(2,4)+"."
        else if(it.length==10)it.substring(8,10)+"."+it.substring(5,7)+"."+it.substring(0,4) else it
    }?:""
    var date by remember(person.id){mutableStateOf(initial)}
    var google by remember(person.id){mutableStateOf(true)}
    var error by remember(person.id){mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Geburtstag ergänzen")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(person.name,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(value=date,onValueChange={date=it},singleLine=true,
                    label={Text("Geburtsdatum")},placeholder={Text("TT.MM.JJJJ oder TT.MM.")})
                RowWithCheckbox(google,{google=it},"In Google Kontakte übernehmen")
                if(person.lookup!=null&&google)Text(
                    "Für diesen vorhandenen Kontakt öffnet sich die Kontaktbearbeitung. Prüfe dort das Datum und bestätige es.",
                    style=MaterialTheme.typography.bodySmall)
                if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={Button(onClick={
            if(runCatching{Dates.parse(date)}.isFailure){error="Datum ist ungültig.";return@Button}
            onSave(date,google)
        }){Text("Speichern")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}}
    )
}
@Composable private fun RowWithCheckbox(checked:Boolean,onChange:(Boolean)->Unit,text:String){
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Checkbox(checked=checked,onCheckedChange=onChange)
        Text(text,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun PersonDialog(person:Person,onDismiss:()->Unit,onBirthday:()->Unit,onMute:()->Unit,
    onIgnore:()->Unit,onEditContacts:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text(person.name)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("Wähle, was du bearbeiten möchtest.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onBirthday){Icon(Icons.Default.Cake,null);Spacer(Modifier.width(8.dp));Text("Geburtstag ändern")}
                TextButton(onClick=onMute){Icon(Icons.Default.NotificationsOff,null);Spacer(Modifier.width(8.dp));Text(if(person.muted)"Erinnerungen einschalten" else "Erinnerungen ausschalten")}
                TextButton(onClick=onEditContacts){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("In Android/Google Kontakte bearbeiten")}
                TextButton(onClick=onIgnore){Icon(Icons.Default.VisibilityOff,null);Spacer(Modifier.width(8.dp));Text(if(person.ignored)"In Fehlenden-Liste berücksichtigen" else "Fehlenden-Liste ignorieren")}
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Schließen")}}
    )
}
@Composable
fun OccasionDialog(event:Event,name:String,year:Int,onDismiss:()->Unit,onAction:(String)->Unit){
    val kind=if(event.type=="BIRTHDAY")"Geburtstag" else if(event.type=="WEDDING")"Hochzeitstag" else "Besonderer Tag"
    AlertDialog(onDismissRequest=onDismiss,title={Text(name)},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(kind+" · "+event.date+" · "+year,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={onAction("WHATSAPP")}){
                    Icon(Icons.Default.Send,null);Spacer(Modifier.width(8.dp));Text("WhatsApp-Text vorbereiten")
                }
                TextButton(onClick={onAction("COPY")}){
                    Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Glückwunschtext kopieren")
                }
                TextButton(onClick={onAction("DAY")}){
                    Icon(Icons.Default.CheckCircle,null);Spacer(Modifier.width(8.dp));Text("Glückwunsch erledigt")
                }
                TextButton(onClick={onAction("FOLLOW")}){
                    Icon(Icons.Default.DoneAll,null);Spacer(Modifier.width(8.dp));Text("Nach Ferien persönlich gratuliert")
                }
                HorizontalDivider()
                TextButton(onClick={onAction("MUTE")}){
                    Icon(Icons.Default.NotificationsOff,null);Spacer(Modifier.width(8.dp))
                    Text(if(event.enabled)"Erinnerungen für Ereignis ausschalten" else "Erinnerungen aktivieren")
                }
                TextButton(onClick={onAction("HOLIDAYS")}){
                    Icon(Icons.Default.Event,null);Spacer(Modifier.width(8.dp))
                    Text(if(event.follow)"Ferien-Nachholung ausschalten" else "Ferien-Nachholung aktivieren")
                }
                Text("WhatsApp verschickt niemals automatisch Nachrichten. Nach dem Senden bitte den Glückwunsch erledigen.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Schließen")}}
    )
}
@Composable
fun AddEventDialog(people:List<Person>,onDismiss:()->Unit,onSave:(String,Long,Long?,String,String)->Unit){
    var type by remember{mutableStateOf("WEDDING")}
    var first by remember{mutableLongStateOf(people.firstOrNull()?.id?:-1L)}
    var second by remember{mutableLongStateOf(-1L)}
    var date by remember{mutableStateOf("")}
    var title by remember{mutableStateOf("Hochzeitstag")}
    var error by remember{mutableStateOf<String?>(null)}
    if(people.isEmpty()){
        AlertDialog(onDismissRequest=onDismiss,title={Text("Keine Personen")},
            text={Text("Bitte zuerst eine Person anlegen.")},
            confirmButton={TextButton(onClick=onDismiss){Text("OK")}})
        return
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Jahrestag anlegen")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(selected=type=="WEDDING",onClick={type="WEDDING";title="Hochzeitstag"},
                        label={Text("Hochzeitstag")})
                    FilterChip(selected=type=="OTHER",onClick={type="OTHER";title="Jahrestag"},
                        label={Text("Anderer Tag")})
                }
                Text("Erste Person",style=MaterialTheme.typography.labelMedium)
                PersonDropdown(people,first,onSelect={first=it})
                Text("Zweite Person (optional)",style=MaterialTheme.typography.labelMedium)
                PersonDropdown(people,second,onSelect={second=it},optional=true)
                OutlinedTextField(value=date,onValueChange={date=it},label={Text("Datum *")},
                    placeholder={Text("TT.MM.JJJJ")},singleLine=true)
                OutlinedTextField(value=title,onValueChange={title=it},label={Text("Bezeichnung")},singleLine=true)
                if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={Button(onClick={
            if(first<0||first==second){error="Bitte gültige, verschiedene Personen auswählen.";return@Button}
            if(runCatching{Dates.parse(date)}.isFailure){error="Bitte gültiges Datum eingeben.";return@Button}
            onSave(type,first,second.takeIf{it>0},date,title)
        }){Text("Hinzufügen")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}}
    )
}
@Composable
private fun PersonDropdown(people:List<Person>,selected:Long,onSelect:(Long)->Unit,optional:Boolean=false){
    var expanded by remember{mutableStateOf(false)}
    val name=people.firstOrNull{it.id==selected}?.name ?:"Keine zweite Person"
    Box{
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
            Text(name,modifier=Modifier.weight(1f),maxLines=1)
            Icon(Icons.Default.ArrowDropDown,null)
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            if(optional)DropdownMenuItem(text={Text("Keine zweite Person")},onClick={onSelect(-1);expanded=false})
            people.forEach{p->
                DropdownMenuItem(text={Text(p.name)},onClick={onSelect(p.id);expanded=false})
            }
        }
    }
}
@Composable
fun DuplicatesDialog(matches:List<Pair<Person,Person>>,onDismiss:()->Unit,
    onMerge:(Person,Person)->Unit,onDismissPair:(Person,Person)->Unit){
    var selected by remember(matches){mutableIntStateOf(-1)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Doppelte Kontakte prüfen")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(matches.isEmpty())Text("Keine passenden doppelten Einträge erkannt.")
                matches.take(50).forEachIndexed{index,pair->
                    OutlinedButton(onClick={selected=index},modifier=Modifier.fillMaxWidth()){
                        Text(pair.first.name+" ↔ "+pair.second.name)
                    }
                }
                if(selected in matches.indices){
                    val(a,b)=matches[selected]
                    Text("Beide Einträge gehören wirklich zur selben Person? Nur die lokalen App-Einträge werden zusammengeführt.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(onClick={onMerge(a,b)}){Text("Bestätigt zusammenführen")}
                    OutlinedButton(onClick={onDismissPair(a,b)}){Text("Getrennt lassen")}
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Schließen")}}
    )
}
@Composable
fun FreeDayDialog(onDismiss:()->Unit,onSave:(String)->Unit){
    var date by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Schulfreien Tag eintragen")},
        text={
            Column{
                OutlinedTextField(value=date,onValueChange={date=it},label={Text("Datum TT.MM.JJJJ")},singleLine=true)
                if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
            }
        },
        confirmButton={Button(onClick={
            if(runCatching{LocalDate.parse(Dates.parse(date))}.isFailure){
                error="Bitte ein vollständiges Datum mit Jahr eingeben.";return@Button
            }
            onSave(date)
        }){Text("Speichern")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Abbrechen")}}
    )
}
