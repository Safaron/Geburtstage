# Geburtstags-Assistent (Android)

Native Android-App für Samsung-Smartphones: lokale Geburtstage und Hochzeitstage, Kontakteimport, Liste fehlender Geburtstage, Vorschläge zur Zusammenführung, WhatsApp-Ein-Klick-Vorbereitung und bayerische Feriennachholung.

## APK erstellen

GitHub: **Actions → Android APK erstellen → Run workflow**. Nach einem erfolgreichen Lauf unter **Artifacts → GeburtstagsAssistent-APK** herunterladen, ZIP entpacken und **app-debug.apk** auf dem Android-Smartphone öffnen. Die Android-Berechtigungen für Kontakte und Benachrichtigungen beim ersten Start erteilen.

Die APK ist eine Debug-Variante zur privaten Installation und keine veröffentlichte Play-Store-App. Es gibt **keinen automatischen WhatsApp-Versand**. Google-Kontaktdaten werden über den Android-Kontakteditor nach Bestätigung geschrieben; Änderungen an vorhandenen Kontakten müssen dort kontrolliert werden.

**Wichtig:** Die erfolgreiche APK-Kompilierung und tatsächliche Funktion auf einem Samsung-Gerät müssen im Workflow bzw. auf dem Gerät geprüft werden. Hinterlegte Schulferien reichen bis Sommer 2030, danach ist eine Aktualisierung erforderlich. Der Prototyp bietet noch keine automatische Cloud-Sicherung, keine vollständige historische Gratulationsübersicht und keine geräteübergreifende Synchronisierung der lokalen App-Daten.
