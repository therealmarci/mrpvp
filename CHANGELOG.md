# Changelog

Alle wichtigen Änderungen an MRpvp. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config, Menüs und
> Permissions noch ändern.

## [Unveröffentlicht]

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
