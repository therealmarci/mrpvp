# Changelog

Alle wichtigen Änderungen an MRpvp. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config, Menüs und
> Permissions noch ändern.

## [Unveröffentlicht]

### Behoben & geändert
- **Kit fehlt in `/duel`, ohne dass man den Grund sieht:** Ein Kit, das in keine eingeschaltete Arena passt (z.B. Arena „nur Elytra“, aber keine Elytra im Kit, oder die Arena lässt nur bestimmte Kits zu), war im Admin-Menü sichtbar, in `/duel` aber nicht wählbar. Die Kit-Liste im Admin-Menü zeigt solche Kits jetzt rot mit „In keiner aktiven Arena spielbar - fehlt in /duel“ (neuer Text `gui-kitlist-unplayable`). Der Web-Kit-Editor (MRadmin) zeigt bei jeder Arena, warum sie das Kit nicht nimmt.

## [0.9.9] - 2026-10-29

### Neu
- **Duelle auf der eigenen Spielerseite (MRadmin, `/web`):** Mit MRadmin sieht jeder Spieler im Browser seine Duell-Statistik (Runden, Siege, Niederlagen, Siegquote, K/D, Serie, Trefferquote) und seine letzten Runden mit Gegner, Kit, Ergebnis und Dauer. Auf der Statistik-Seite stehen dazu die Ranglisten **meiste Siege**, **beste Siegquote** (ab `stats.min-games-for-ratio` Runden) und **längste Siegesserie**. Braucht `mrpvp.use` und MRadmin mit Modul-API 1.3.0. Texte unter `self-*` in den Sprachdateien.

### Behoben & geändert
- **Support steuert laufende Duelle:** `mrpvp.admin.stop` und `mrpvp.admin.pause` stecken jetzt in `mrpvp.groups.support` (vorher Staff) - Support kann Duelle abbrechen, einen Sieger bestimmen und pausieren. Spieler sperren bleibt bei Staff.
- **Arena-Welt nicht mehr auf der Webkarte:** Ist squaremap installiert, schaltet MRpvp seine Arena-Welt dort ab (`world-settings."minecraft:mrpvp_arenas".map.enabled: false` in `plugins/squaremap/config.yml`) und lädt squaremap dafür einmal neu - nur wenn dort noch nichts eingetragen ist. Abschaltbar mit `arena-world.hide-on-squaremap: false` (config-version 11).
- **Kit-Editor im Spiel komplett neu:** Ein Kit öffnet jetzt ein symmetrisches Kit-Menü (Inventar bearbeiten, Regeln, Name, Beschreibung, Symbol, Inhalt leeren, Permission, Zurück / Speichern / Löschen) mit Hinweis auf ungespeicherte Änderungen. **„Inventar bearbeiten“** legt dein eigenes Inventar sicher weg (auch das vom Admin-Modus), du bekommst Kreativ und das Kit **direkt in dein Inventar** - bearbeiten wie gewohnt. Fertig, „Symbol = Item in der Hand“ und Abbrechen gehen über Chat-Knöpfe oder `/duel`; danach bekommst du dein eigenes Inventar zurück und speicherst im Kit-Menü. Droppen, Aufheben und Kisten sind dabei gesperrt, nichts kann herausgeschmuggelt werden. Vorher musste man Items aus dem eigenen Inventar ins Kit kopieren - im Admin-Modus ging das praktisch nicht.
- **Duell-Menü (/duel) neu geordnet und symmetrisch:** links „Spielen“ (Herausfordern, Einladungen), in der Mitte „Meine Runden“, rechts „Rang“ (Statistik, Bestenliste), Schließen unten mittig. Der Admin-Knopf sitzt oben mittig und ist für Spieler einfach Glas - das Menü bleibt für alle symmetrisch. In einer Runde stehen dort „Aufgeben“ und die laufende Runde, im Admin-Modus von MRadmin ein Hinweis, dass dort keine Duelle gehen.
- **Admin-Menü neu geordnet und symmetrisch:** oben der Status, links „Überwachen“ (Live, Prüfen, Auffälligkeiten), rechts „Spieler“ (alle Runden, Spieler, neu: **Sperren** direkt), darunter Kits und Arenen, unten Zurück / Standard wiederherstellen / Schließen. Bereiche ohne Berechtigung bleiben als graues Gitter „Dafür fehlt dir die Berechtigung“ an ihrem Platz.
- **Admin-Modus (MRadmin):** `/duel` lässt sich öffnen und alles einstellen, aber wer im Admin-Modus ist, steht nicht mehr in der Spielerauswahl und kann nicht eingeladen werden („kann gerade nicht eingeladen werden“); selbst einladen geht dort auch nicht (mit Hinweis `/mradmin mode off`).
- **Live-Ansicht im Web ohne Verzögerung:** Die Rundenansicht der MRadmin-Weboberfläche bekommt Treffer, Schüsse, Perlen usw. sofort (bisher erst, wenn MRpvp das Kampf-Log alle 5 Sekunden gespeichert hatte). Jeder Eintrag des Kampf-Logs kommt mit seinem Ort in der Arena, damit die Mini-Karte ihn dort zeigen kann.
- **Arenen gemeinsam bauen (Entwürfe „im Bau“):** Neue Arenen und Änderungen an bestehenden entstehen jetzt als **Entwurf**. Mehrere aus dem Team können gleichzeitig daran bauen: In der Arenen-Liste (Admin-Menü → Arenen) stehen neue Arenen als „im Bau“ mit Anzeige, wer gerade baut und wer zuletzt daran war - Klick = mitbauen, Shift+Rechtsklick = verwerfen. Bei bestehenden Arenen zeigt die Liste „Änderungen im Bau“, im Arena-Menü wird „Bearbeiten“ dann zu **Mitbauen**. Ein Entwurf ist nicht spielbar, nicht wählbar und erscheint nicht im Web; bei bestehenden Arenen bleibt bis dahin die alte Version spielbar. Im Editor (grüner Farbstoff, Slot 9) gibt es jetzt **Veröffentlichen** (neue Version, alle Bauenden verlassen den Editor), **Verlassen** (der Entwurf bleibt im Bau, auch über Neustarts - gespeichert in `plugins/MRpvp/arena-drafts.yml`) und **Entwurf verwerfen**. Beim Veröffentlichen gelten „aktiv“ und die Zahl der Kopien so, wie sie inzwischen im Arena-Menü eingestellt sind. Wer eine Arena löscht, löscht ihren Entwurf mit. Permission wie bisher `mrpvp.admin.arena`. Vorher konnte immer nur einer eine Arena bearbeiten, und wer den Editor verließ, verlor einen nicht gespeicherten Stand.

## [0.4.0] - 2026-10-28

> [!WARNING]
> **Neue Permission-Gruppen:** Das Paket `mrpvp.admin` gibt es nicht mehr. Stattdessen gibt es in allen MRtechnology-Plugins dieselben Gruppen `<plugin>.player` und `<plugin>.groups.support|staff|admin`. Außerdem sind Duelle nicht mehr automatisch für alle Spieler da: `/lp group default permission set mrpvp.player true`. Admin-Gruppen umstellen, z.B. `/lp group admin permission unset mrpvp.admin` und `/lp group admin permission set mrpvp.groups.admin true`.

### Neu
- **Permission-Gruppen:** `mrpvp.groups.support` (Admin-Menü, Runden ansehen, prüfen und werten, zusehen, Spieler-Verwaltung), `mrpvp.groups.staff` (+ Kits und Arenen bearbeiten, Duelle abbrechen, Sieger bestimmen, pausieren, sperren) und `mrpvp.groups.admin` (alles). Jede Gruppe enthält die darunter und `mrpvp.player`.
- **Knopf im MRadmin-Dashboard:** öffnet das Admin-Menü jetzt direkt über die Modul-API.
- **Neue Kit-Regeln:** festes Zeitlimit (`minutes`), Herzen (`max-hearts`, z.B. 20 statt 10), Effekte für die ganze Runde (`effects: { speed: 2 }`) und eigene Cooldowns für Items (`cooldowns: { ender_pearl: 15 }` - die Wartezeit nach dem Benutzen zeigt das Spiel am Item). Was ein Kit fest vorgibt, ist beim Herausfordern ausgegraut. Die Herzen hängen an einem Wert, der nie gespeichert wird - nach der Runde (auch nach einem Absturz) hat jeder wieder seine normalen Herzen.
- **Kits exportieren und importieren:** In der Kit-Liste (Admin-Menü → Kits) schreibt **Alle Kits exportieren** eine Datei nach `plugins/MRpvp/exports/`; **Kits importieren** liest Dateien aus `plugins/MRpvp/imports/` mit Vorschau pro Kit (neu, vorhanden → als `<id>_2` anlegen oder ersetzen, fehlende Arenen, nicht lesbare Items). Im Web (MRadmin → Duelle → Kits) gibt es Export mit Auswahl und Import mit Arena-Zuordnung. Gleiches Dateiformat (JSON) im Spiel und im Web. Permission `mrpvp.admin.kits`.
- **Alle Kit-Regeln im Kit-Editor im Spiel:** Neuer Knopf **Regeln** (Admin-Menü → Kits → Kit): Schaden, Treffer bis zum Sieg, Regeneration und Hunger (Spieler wählen / immer an / immer aus), braucht Bau-Arena, Zeitlimit, Herzen und auf eigenen Seiten die Arenen, Effekte (Stufe per Klick) und Cooldowns (für die Items des Kits). Bisher ließen sich die Regeln nur in `kits.yml` ändern. Kits ohne Items lassen sich jetzt auch im Spiel speichern, wenn sie eigene Regeln haben (z.B. Sumo).
- **Kit-Editor in der MRadmin-Weboberfläche:** Unter Duelle → Kits stehen alle Kits mit den Item-Bildern aus dem Spiel. Dort lassen sie sich sortieren (die Reihenfolge im Spiel), kopieren, neu anlegen, umbenennen und löschen. Im Editor: Inventar wie im Spiel - Klick auf einen Platz öffnet den Item-Editor von MRadmin (Anzahl, Name, Beschreibung, Verzauberungen, Tränke, Farbe, Rüstungsbesatz …), Plätze tauschen, Name und Beschreibung auf Deutsch und Englisch, Symbol, Permission und **alle Kit-Regeln** inklusive der Arenen, auf die ein Kit festgelegt ist, Regeneration und Hunger. Permission `mrpvp.admin.kits` (wie der Kit-Editor im Spiel). Braucht MRadmin mit Item-Editor (Modul-API 2).
- **Inventare im Web mit Bildern:** Die Inventare bei laufenden und beendeten Runden zeigt MRadmin jetzt mit den Item-Bildern und dem Tooltip wie im Spiel.

### Entfernt
- **Paket `mrpvp.admin`:** ersetzt durch `mrpvp.groups.admin`.

### Behoben & geändert
- **Spielerseite schont den Server:** Statistik und letzte Runden eines Spielers werden für die Spielerseite 30 Sekunden zwischengespeichert, statt bei jedem Nachladen die Datenbank abzufragen (der Server-Thread wartet dabei).
- **Spielerseite: letzte Runden als Karten:** Auf der Spielerseite (MRadmin, `/web`) ist jede der letzten Runden jetzt eine Karte in der Kachel „Deine letzten Runden“ - mit Gegner, Zeitpunkt, Kit, Dauer und Schaden, farbig nach Ergebnis (Sieg grün, Niederlage rot, Unentschieden gelb, abgebrochen grau), bis zu 100 Runden in Seiten zum Blättern. Ein Klick öffnet die Runde, gegliedert in zwei Kacheln: „Überblick“ (wie sie endete, Arena, Kit, Runde) und „Werte im Detail“ mit - du violett, der Gegner gelb - für beide Seiten Schaden, Treffer, Leben am Ende, Pfeile, Totems und Enderperlen. Braucht MRadmin mit Modul-API 5, sonst bleibt es bei der Tabelle. Der Hinweis „Duelle startest du im Spiel mit /duel“ ist weg.
- **Kachel auf der Spielerseite:** Die Duell-Kachel auf der Startseite der Spielerseite (MRadmin, `/web`) zeigt jetzt Siege groß, dazu Runden, Siegquote und K/D.
- **`/mrpvp version` nur noch mit Permission:** `mrpvp.version` (in `mrpvp.player`) - bisher durfte das jeder.
- **Duelle nur noch mit Permission:** `mrpvp.player` und `mrpvp.use` sind nicht mehr automatisch für alle - vergib `mrpvp.player` an deine Spieler-Gruppe.

## [0.3.0] - 2026-10-27

### Neu
- **Duelle live steuern:** Ein Klick auf ein Duell in Admin-Menü → Live öffnet seine Steuerung: zuschauen (über der Arena oder direkt bei einem der Spieler), **pausieren/fortsetzen** (beide eingefroren, kein Schaden, die Zeit steht - neue Permission `mrpvp.admin.pause`), **Sieger bestimmen** (das Duell endet, der Spieler gewinnt das Match, die Runde zählt als Sieg ohne Kill/Tod) und abbrechen (`mrpvp.admin.stop`). Bisher: Klick = zuschauen, Shift-Klick = abbrechen.
- **Runden nachträglich aus der Wertung nehmen:** In den Details einer Runde nimmt `mrpvp.admin.review` eine gewertete Runde aus der Statistik (mit optionalem Grund) und kann sie später wieder werten - auch verworfene Runden aus der Prüfung. Die Statistik beider Spieler wird neu berechnet; Konsole und MRadmins Discord-Admin-Log bekommen einen Eintrag. Geht auch in der MRadmin-Weboberfläche.
- **Alle Server auf die Sprache des Spielers:** Bestehende Configs mit fest eingestellter Sprache (`language: de`/`en`) werden auf `language: auto` umgestellt - jeder Spieler bekommt die Sprache seines Minecraft-Clients, wie bei MRsleep, MRchunks und MRadmin.
- **Server-Sprache (`server-language`):** Texte ohne einzelnen Empfänger wie die Discord-Meldungen zu auffälligen Runden (über MRadmin) kommen in dieser Sprache (Standard `en`).
- **Automatische Umstellung:** Bestehende Configs bekommen `language: auto`; eine bisher fest eingestellte Sprache wird zur `server-language`, damit z.B. die Discord-Logs eines deutschen Servers deutsch bleiben.
- **Kit-Namen und -Beschreibungen pro Sprache:** In `kits.yml` geht jetzt `name: { de: "...", en: "..." }` (ebenso `description`), ein einzelner Text gilt weiter für alle Sprachen. Die Standard-Kits sind deutsch und englisch - bestehende Standard-Kits mit den unveränderten deutschen Texten werden beim Start automatisch ergänzt, eigene Texte bleiben. Der Kit-Editor ändert Name und Beschreibung in der Sprache des Admins, die anderen Sprachen bleiben erhalten.
- **Schnellauswahl für Duell-Sperren (`gui.ban-durations`):** Drei Dauern (Standard `1d`, `7d`, `permanent`), die auf der Spielerseite der MRadmin-Weboberfläche als Knöpfe erscheinen - wie bei MRsleep und MRchunks. Bestehende Configs bekommen die Einstellung automatisch (config-version 8).

### Behoben & geändert
- **Anti-Farming zu empfindlich:** Schon zweimal fünf Runden unter Freunden galten als Statistik-Farmen. Jetzt ist eine Serie nur noch verdächtig, wenn sie **einseitig** ist: mindestens 8 Runden desselben Paars in 60 Minuten, derselbe Spieler gewinnt mindestens 90 % und der andere macht höchstens 20 % des Schadens (`monitor.farming.rounds`, `.minutes`, `.winner-percent`, `.max-loser-damage-percent`; ersetzt `monitor.same-pair-per-hour`). Die Tagesgrenze pro Gegner (`stats.max-counted-per-opponent-per-day`) steht jetzt auf 50 statt 5 - bestehende Configs mit dem alten Standard werden umgestellt.
- **Mehr Arenen bereit als im Vorrat („3 von 1 bereit“):** Nach jeder Runde wurde die Arena wieder aufgebaut und zurück in den Vorrat gelegt, auch wenn der Vorrat (`pool` der Arena) schon voll war - nach mehreren gleichzeitigen Runden waren so mehr Arenen bereit als eingestellt. Jetzt wird eine Arena nach der Runde nur aufgebaut, solange der Vorrat nicht voll ist, sonst abgeräumt; ein vorhandener Überschuss wird beim Start abgebaut.
- **Update-Hinweis bei `/mrpvp version` doppelt:** Fand die Abfrage eine neue Version, kam zuerst der Admin-Hinweis „Für MRpvp gibt es ein Update“ und direkt danach „Neue Version verfügbar“. Jetzt kommt nur noch die Antwort auf den Befehl. Die Download-Links führen auf die Website (https://mrtechnology.marcelr.at/download/mrpvp/).
- **Config und plugin.yml auf Englisch:** Alle Kommentare in der `config.yml` und die Beschreibungen der Befehle und Permissions in der `plugin.yml` sind jetzt englisch. Bestehende Configs bekommen die neuen Kommentare beim nächsten Start automatisch (config-version 7, Sicherung als `config.v<alt>.backup.yml`), alle Einstellungen bleiben erhalten.
- **kits.yml:** Die Erklärung oben in der Datei ist jetzt englisch und aktuell (Kit-Editor und „Standard wiederherstellen“ gibt es bereits). Kit-Namen und -Beschreibungen sind unverändert.

## [0.2.0] - 2026-10-27

### Geändert – Lizenz, Doku, Website
- **MRtechnology Lizenz 1.0:** Eine einheitliche Lizenz für alle MRtechnology-Projekte. Ausdrücklich erlaubt ist jetzt auch die gewerbliche Nutzung, Weiterveröffentlichen und Verkaufen bleiben verboten, die Nutzung erfolgt auf eigenes Risiko.
- **README überarbeitet:** Schnellstart, Auf einen Blick, Häufige Fragen, Kurzlinks zur Website (Download, Anleitung, Fehler melden).
- **Links zur Website** [mrtechnology.marcelr.at](https://mrtechnology.marcelr.at): Update-Hinweise in Konsole und Spiel führen zur Download-Seite, `/mrpvp version` zeigt „Website | GitHub“ (statt des Modrinth-Links), `website` in der plugin.yml zeigt auf die Anleitung. Der Text `version-links` wird in den mitgelieferten Sprachdateien automatisch erneuert.

### Geändert
- **MRadmin über die Modul-API:** Das Plugin meldet sich als Modul bei MRadmin an (statt der alten
  `MRadminHook`-Klasse). Admin-Modus, Live-Updates der Duell-Seiten und Admin-Log laufen jetzt ebenfalls über die Modul-API. Braucht MRadmin mit Modul-API; ohne MRadmin ändert sich nichts.
- **Verbindung zu anderen MR-Plugins nur noch über MRadmin:** Duelle, Arena-Welt und MRtoolbars Gürtel teilt
  MRpvp über MRadmin mit (vorher eigene Schnittstellen `MRpvpApi` bzw. zu `MRtoolbarApi`). Ohne MRadmin bekommen
  MRsleep, MRtoolbar und MRchunks von Duellen nichts mehr mit, und die Gürtel werden nicht mitgesichert.
  Neu: Hinweis „Runden warten auf eine Prüfung“ auf der Startseite der Weboberfläche.

### Neu
- **NPCs sind keine Gegner:** Dummy-Spieler von MRchunks und andere NPCs (Metadata `NPC`, z.B. Citizens) lassen sich
  nicht zum Duell einladen und erscheinen nicht in Spielerlisten und der Tab-Vervollständigung.

## [0.1.0] - 2026-10-23

Erste Testversion.

### Neu
- **Duelle** über `/duel`: Spieler anklicken, Kit und Map wählen, Optionen (Siege bis Gewinn, Zeitlimit pro Runde,
  Hunger, natürliche Regeneration), senden. Die letzten Einstellungen werden gemerkt. Einladungen im Chat mit
  [Annehmen] [Ablehnen] [Details].
- **Matches** über mehrere Runden („first to N“), jede Runde mit eigener Spiel-ID.
- **Kein echter Tod:** tödlicher Schaden beendet die Runde - keine Minecraft-Statistik, keine Todesnachricht, keine
  Drops, keine Advancements; die Minecraft-Statistik wird nach der Runde auf den Stand davor gesetzt.
- **Sichere Rückgabe:** der ganze Zustand (Inventar, Rüstung, Zweithand, Enderchest, MRtoolbar-Gürtel, XP, Leben,
  Hunger, Effekte, Spielmodus, Position) liegt in SQLite, bevor etwas geändert wird - auch nach Ausloggen, Absturz
  oder Neustart.
- **Bossbar** im Duell (Runde, Stand, Gegner mit Herzen, Restzeit, Treffer bei Boxing), nur eine Chat-Zusammenfassung
  am Ende.
- **Aufgeben** ist eine Niederlage im Match (vor Runde 1 nur Abbruch). **Verbindungsabbruch:** die Runde wartet
  20 Sekunden (`duel.reconnect-seconds`, einmal pro Match), danach Niederlage.
- **14 Standard-Kits** (Klassisch, Diamant, Netherite, Bogen, Streitkolben, Elytra & Streitkolben, UHC, Build-UHC,
  Sumo, Boxing, NoDebuff, Crystal, Axt & Schild, Dreizack) mit Kit-Regeln (kein Schaden, Sieg nach Treffern, feste
  Regeneration/Hunger, Arenen, Bau-Arena) und Kit-Editor im Spiel.
- **Arenen** als Strukturen in eigener Void-Welt, mit Raster aus bereiten Kopien, Arena-Editor im Kreativmodus
  (Inventar lässt sich dort nicht leeren). Standard-Arenen: Standard, Sumo, Bau-Arena, XXL-Himmel (nur Elytra-Kits).
- **Eigene Statistik:** K/D, Siege, Serien, pro Kit, Kopf-an-Kopf, Bestenliste als Podest, letzte Runden mit
  Kampf-Log.
- **Prüfung auffälliger Runden** statt automatischem Ausschluss: Admin-Menü → Prüfen, werten oder verwerfen,
  automatische Entscheidung nach 7 Tagen (`review.*`).
- **Admin-Menü:** Live-Duelle (zusehen, abbrechen), alle Runden mit gesicherten Inventaren, Rückgabe, Spieler
  (Sperren, Statistik zurücksetzen), Auffälligkeiten, Standard wiederherstellen.
- Optionale Anbindung an **MRadmin** (Dashboard, Spieler-Menü, Serverzustand, Web-Seite „Duelle“, Discord-Log),
  **MRtoolbar**, **MRsleep** und **MRchunks** - keine Abhängigkeit in irgendeine Richtung.
- Deutsch und Englisch (Sprache pro Spieler), alles in `config.yml` einstellbar, Update-Prüfung über GitHub.
