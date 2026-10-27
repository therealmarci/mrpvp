# MRpvp – Konzept & Plan

Name: **MRpvp** (Ordner `mrpvp`, Package `at.marcelr.mrpvp`). Paper-Plugin für Minecraft 26.3, gleicher Aufbau wie
MRsleep / MRchunks / MRadmin / MRtoolbar (Gradle Kotlin DSL, Java 25, `mc<MC>-v<semver>`, beta/main, lang-Dateien,
eine config.yml, LuckPerms-Permission für jede Funktion, Konsole Englisch).

## Idee

Ein Spieler lädt einen anderen zum Duell ein. Nimmt dieser an, kommen beide in eine eigene PvP-Arena und kämpfen
mit einem vorgegebenen Kit. Danach bekommen beide ihren alten Zustand zurück. Duelle zählen **nicht** zu
Vanilla-Statistik, Scoreboards oder anderen Plugins.

## Entscheidungen (2026-10-22)

| Thema | Entscheidung |
|---|---|
| Tode | **Kein echter Tod**: tödlicher Schaden wird abgefangen und als Niederlage gewertet → kein `PlayerDeathEvent`, keine Statistik (`DEATHS`, `PLAYER_KILLS`), keine Scoreboard-Criteria, keine Drops, keine Death-Message |
| Spielerzustand | Inventar, Rüstung, Offhand, XP, Leben, Hunger, Effekte, Gamemode, Position werden vor dem Duell **in SQLite** gesichert (crash-/quit-sicher), danach wiederhergestellt. Enderchest bleibt unberührt (wird nur protokolliert) |
| Bedienung (2026-10-22) | **In-Game alles per GUI** – für Spieler und Admins. Befehle öffnen nur GUIs (`/duel`, `/duel admin`); Texteingaben (Namen, Grund) über Paper-Dialoge wie in MRadmin; Chat-Links öffnen ebenfalls GUIs |
| Statistik (2026-10-22) | **Eigene Duell-Statistik** (K/D, Siege, Serien …), komplett getrennt von Vanilla, für Spieler im GUI sichtbar (siehe 9.); **kein Ranked/Elo** |
| Datenbank & Game-ID (2026-10-22) | **SQLite** (`plugins/MRpvp/mrpvp.db`). Jede Runde bekommt eine **Game-ID** (`#1042`) mit allen Details, auch dem kompletten Zustand beider Spieler beim Betreten (inkl. MRtoolbar-Gürtel). Auswertung im Admin-GUI und – wenn MRadmin-Schnittstelle aktiv – im Web (siehe 8.) |
| Kits | Presets; Admin bearbeitet Kits im **Kit-Editor-GUI** (Spieler-Layout zum Reinlegen der Items, oder „aktuelles Inventar übernehmen“); Recht pro Kit |
| Welt | Eigene Void-Welt, vom Plugin selbst angelegt (eigener leerer Generator, kein Multiverse nötig) |
| Arenen | **Variante B**: Arena-Vorlagen (`.nbt` über Vanilla-`StructureManager`, keine WorldEdit-Abhängigkeit) werden in Raster-Slots eingefügt, mit vorgewärmtem Pool |
| Arena/Kit | Frei kombinierbar, optional einschränkbar über `kits:` in der Vorlage |
| Editor | Editor-Bereich in der Arena-Welt (abseits vom Slot-Raster) |
| Mitgeliefert | Eine Standard-Arena und Standard-Kits im Jar, beim ersten Start entpackt (siehe 6.) |
| Schnittstellen | Optional, ohne Build-Abhängigkeit (Reflection/MethodHandle wie MRadminApi/MRadminHook): MRsleep, MRtoolbar, MRadmin inkl. Web-Live-Ansicht (siehe 7.) |

## Bausteine

### 1. Einladung
- Hauptmenü `/duel` → **Herausfordern** → Spielerauswahl (Köpfe der Online-Spieler, blätterbar, Suche per Dialog)
  → Arena wählen (Icons der Vorlagen + „Zufällig“) → Kit wählen (Vorschau des Inhalts im Tooltip) → Senden.
  `/duel <spieler>` springt direkt zur Arena-Auswahl.
- Eingeladener bekommt eine Chat-Nachricht mit **[Ansehen]** → GUI mit Gegner (Kopf + dessen K/D), Arena, Kit
  und **Annehmen / Ablehnen**. Offene Einladungen auch im Hauptmenü (Glocke mit Anzahl).
- Ablaufzeit (Standard 30 s) und Cooldown konfigurierbar. Einstellungen im Hauptmenü: Einladungen an/aus,
  nur von Freunden/niemand (falls später), Benachrichtigungston.
- Keine Einladung an Spieler, die schon im Duell / in der Warteschlange / im MRadmin-Adminmode bzw. gefreezt sind.

### 2. Duell-Ablauf
1. Annehmen → freier, bereiter Slot der Vorlage wird reserviert (sonst neuer Slot / Warteschlange).
2. Game-ID vergeben, Zustand beider Spieler in SQLite sichern – **erst wenn der Eintrag geschrieben ist**, wird das
   Inventar angefasst (läuft während des Countdowns, merkt niemand). Inventar leeren, Kit geben, volles Leben/Hunger, Effekte weg.
3. Teleport auf Spawn 1 / Spawn 2, Countdown 3-2-1 (eingefroren), Start.
4. Tödlicher Schaden → Niederlage (Totem im Kit: konfigurierbar, ob es auslösen darf).
5. Ergebnis-Nachricht mit Game-ID („Spiel #1042“, klickbar für Admins), kurze Pause, Zustand wiederherstellen, zurück an alte Position.
6. Slot wird zurückgesetzt (siehe 4.).

### 3. Regeln im Duell
- Befehle blockiert bis auf Whitelist (`/duel leave` = aufgeben, `/msg` …).
- Region der Vorlage = Grenze: Verlassen (Enderperle, Chorusfrucht, Knockback ins Void) → zurücksetzen bzw.
  unterhalb einer Y-Grenze = Niederlage (konfigurierbar).
- Quit = Niederlage; Zustand wird beim nächsten Join wiederhergestellt.
- Items droppen verboten; Bauen nur wenn `allow-build` in der Vorlage.
- Max. Dauer (Standard 10 min) → Unentschieden.

### 4. Arena-Welt & Slots
```
Arena-Welt (Void)
┌──────────┬──────────┬──────────┐
│ Slot 0   │ Slot 1   │ Slot 2   │   ← Raster, Abstand konfigurierbar (Standard 1000)
│ [Wald]   │ [leer]   │ [Nether] │
├──────────┼──────────┼──────────┤
│ Slot 3   │ ...      │          │
└──────────┴──────────┴──────────┘
Editor-Bereich (separat, z.B. bei 0, -10000)
```

**Vorlagen** (`plugins/MRpvp/templates/<name>.nbt` + `<name>.yml`):
```yaml
display-name: "&2Waldlichtung"
icon: OAK_SAPLING
size: [64, 40, 64]
spawns:
  1: { x: 10.5, y: 5, z: 32.5, yaw: -90 }
  2: { x: 53.5, y: 5, z: 32.5, yaw: 90 }
allow-build: false
max-build-height: 30
kits: []            # leer = alle Kits erlaubt
```
Erstellen (per GUI): Admin-Menü → Arenen → **Neue Arena** (Name per Dialog) bzw. Arena anklicken → **Bearbeiten**
→ Teleport in den Editor-Bereich; der Admin bekommt eine **Editor-Hotbar** (eigenes Inventar wird solange gesichert):
- Auswahl-Stab: Linksklick = Ecke 1, Rechtsklick = Ecke 2 (Rahmen per Partikel sichtbar)
- Spawn-1- und Spawn-2-Marker: Rechtsklick setzt Spawn an der eigenen Position inkl. Blickrichtung (Hologramm)
- Einstellungen: öffnet GUI für Anzeigename, Icon (Item reinlegen), Bauen erlaubt, Bauhöhe, erlaubte Kits
- Speichern / Verwerfen
Arena-Liste: Klick = Details (Größe, Slots im Pool, Spiele gespielt), Bearbeiten, Pool-Größe, Deaktivieren, Löschen
(mit Bestätigung).

**Slots** – Zustände (in `slots.yml`):
```
LEER → EINFÜGEN → BEREIT → BELEGT → ZURÜCKSETZEN → BEREIT
                                  ↘ (Vorlage gelöscht/geändert) → LEEREN → LEER
```
- `Structure.place()` ist synchron → **Pool**: pro Vorlage X Slots fertig eingefügt (`pool.warm-per-template`,
  Standard 1). Duellstart = nur Teleport.
- Zurücksetzen = Vorlage erneut über den Slot einfügen (Struktur enthält Luft → komplett sauber), danach Entities
  in der Region entfernen (Items, Pfeile, Trident, XP-Orbs).
- Chunks vorher async laden (`getChunkAtAsync`), Einfügen gestaffelt (max. 1 Arena pro Tick), Plugin-Chunk-Tickets
  nur solange BELEGT/EINFÜGEN. Obergrenze `pool.max-slots`.
- Server-Start: Slots in BELEGT/ZURÜCKSETZEN/EINFÜGEN werden zuerst zurückgesetzt.

**Welt-Einstellungen** (setzt das Plugin): Schwierigkeit Normal (nicht Peaceful – Regeneration!),
`doMobSpawning`/`doDaylightCycle`/`doWeatherCycle`/`announceAdvancements` = false, PvP an, Spawn-Chunks nicht
geladen, Portale blockiert.

**Bauen** (`allow-build: true`): nur innerhalb der Region und bis `max-build-height`; Flüssigkeiten, Feuer,
Explosionen und Kolben dürfen nicht über die Grenze (`BlockFromToEvent` usw.).

### 5. Nebenwirkungen abfangen
- **Advancements**, die in der Arena-Welt erreicht werden (z.B. Kit-Diamantrüstung), sofort wieder entziehen.
- Übrige Vanilla-Statistiken (Schaden, Laufen, Springen): optional vorher sichern und danach zurücksetzen
  (`stats.restore`, Standard an).
- Join in der Arena-Welt ohne laufendes Duell → Zustand wiederherstellen + raus teleportieren.
- `/back` u.ä. aus der Arena-Welt blockieren; Inventare-pro-Welt-Plugins (Multiverse-Inventories) dokumentieren.

### 6. Mitgelieferte Inhalte
- **Standard-Arena** `standard` (~48×48, Boden + Wände + ein paar Deckungs-Säulen, `allow-build: false`):
  wird mit dem eigenen Template-Editor auf dem Testserver gebaut, als `.nbt` + `.yml` in
  `src/main/resources/templates/` gelegt und beim ersten Start nach `plugins/MRpvp/templates/` kopiert.
- **Standard-Kits** in `kits.yml`, lesbar als YAML (Material, Menge, Verzauberungen, Slot), z.B.:
  - `klassisch` – Eisenrüstung, Eisenschwert, Bogen + 16 Pfeile, Schild, Essen
  - `diamant` – Diamantrüstung (Schutz II), Diamantschwert (Schärfe II), Bogen, Schild, 2 goldene Äpfel
  - `netherite` – Netherite-Rüstung (Schutz IV), Netherite-Schwert (Schärfe V), Armbrust, Totem? (s. offene Punkte)
  - `bogen` – Lederrüstung, Bogen (Stärke III, Unendlichkeit), 1 Pfeil, Holzschwert
  - `streitkolben` – Eisenrüstung, Streitkolben, 8 Windkugeln, Schild
- **Kit-Editor** (Admin-Menü → Kits): Liste aller Kits; Kit öffnen = GUI im Spieler-Layout (Hotbar, Inventar,
  4 Rüstungs-Slots, Offhand), Items direkt reinlegen/rausnehmen (Admin ist dabei im Kreativ-Kontext, Items
  verlassen den Editor nicht), „aktuelles Inventar übernehmen“, Anzeigename/Icon/Recht, Kopieren, Löschen.
  Im Editor gespeicherte Kits werden als Paper-Item-Bytes (Base64) abgelegt, damit alle Komponenten erhalten
  bleiben; beide Formate werden gelesen.
- Gelöschte Standard-Inhalte kommen nicht von selbst wieder; Button **Standard wiederherstellen** im Admin-Menü
  (Arena / Kits, mit Bestätigung).

### 7. Schnittstellen zu anderen Plugins (alle optional)

Gleiches Muster wie bisher: keine Build-Abhängigkeit, Aufruf über Reflection/MethodHandle, stabile Signaturen,
neue Methoden erhöhen `API_VERSION`. Jede Integration lässt sich in der config abschalten.

**MRpvp bietet an** – `at.marcelr.mrpvp.api.MRpvpApi` (API_VERSION 1, Main-Thread):
```java
public static boolean inGame(UUID player)   // true ab dem Sichern des Zustands bis nach dem Wiederherstellen
```
Wichtig: `inGame` wird **vor** dem Sichern/Leeren des Inventars gesetzt und erst **nach** dem Wiederherstellen
wieder entfernt, damit andere Plugins in dieser Zeit nichts am Inventar machen.

#### MRsleep
- Neue Klasse `api/MRpvpGame` (Kopie des `MRadminAdminMode`-Musters), neuer Grund `reason-pvp` in
  `SleepManager.exclusionReason`, config `sleep.ignore-pvp` (Standard true).
- MRsleep zählt pro Welt – Spieler in der Arena-Welt zählen in der Overworld schon automatisch nicht. Die
  Schnittstelle macht es trotzdem ausdrücklich (auch für Countdown/Teleport-Moment und falls Spiele später in
  anderen Welten laufen) und zeigt den Grund in `/mrsleep status`.
- Arena-Welt selbst wird von MRsleep nicht verwaltet (MRsleep fragt `MRpvpApi`, ob eine Welt die Arena-Welt ist –
  `isArenaWorld(String world)`, gleiches API).

#### MRtoolbar
- `ToolbarManager.paused(player)` wird true, wenn `MRpvpApi.inGame` (wie beim MRadmin-Adminmode), config
  `toolbar.pause-in-pvp` (Standard true). Der Mod bekommt den Pause-Zustand → keine Markierung, Tasten aus.
- **Kritisch (Dupe/Verlust):** MRpvp leert und befüllt das Inventar. Im Pause-Zustand darf der Wächter von
  MRtoolbar weder das aktive Tool aus dem Gürtel lösen noch Fremd-Items verschieben oder auto-ausrüsten.
  MRpvp sichert das aktive Tool mit seinem PDC-Marker und stellt es 1:1 wieder her. Beim Ende der Pause gleicht
  MRtoolbar den Zustand neu ab. → Eigener Testfall: Tool-Slot aktiv, Duell, Wiederherstellen, Server-Crash mitten
  im Duell.
- Alternativ/zusätzlich: Arena-Welt in `toolbar.disabled-worlds` (reicht allein nicht wegen des Zeitpunkts).
- **Gürtel protokollieren:** neues `at.marcelr.mrtoolbar.api.MRtoolbarApi` (API_VERSION 1, Main-Thread):
  ```java
  public static boolean paused(UUID player)                       // für Tests/Anzeige
  public static List<Map<String, Object>> belts(UUID player)      // je Tool-Slot: "slot" (int), "selected" (int),
                                                                  // "items" (List<byte[]>, ItemStack.serializeAsBytes, leer = null)
  ```
  MRpvp speichert das beim Betreten mit in den Snapshot (nur lesend – die Gürtel werden vom Duell nicht verändert).
  Ohne MRtoolbar bleibt das Feld leer.

#### MRadmin (In-Game + Web, volle Überwachung)
- Neue `Integration("mrpvp", "MRpvp", IRON_SWORD, "duel admin gui", "mrpvp.admin.gui", "at.marcelr.mrpvp.api.MRadminHook")`
  und `web/PvpRoutes.java` + Seite `web/src/pages/Pvp.svelte` (+ `DuelView.svelte`).
- **MRadminHook** (Version 4, wie MRsleep): `summary` (laufende Duelle, Warteschlange, Pool), `playerSummary`
  („Im Duell gegen X – Arena, 2:31“, Siege/Niederlagen falls Statistik), `openPlayerMenu` (Duell abbrechen,
  zuschauen, sperren), `activity` (auffällige Duelle, s.u.) und `web(request, args)`.
- **Live-Ansicht im Web:**
  - Übersicht: laufende Duelle (Spieler, Arena, Kit, Laufzeit, Leben/Hunger beider als Balken), offene Einladungen,
    Warteschlange, Slot-Raster mit Zuständen (LEER/EINFÜGEN/BEREIT/BELEGT/ZURÜCKSETZEN) und Einfüge-Dauer in ms.
  - Duell-Detail: Leben, Hunger, Rüstung, Effekte, aktuelles Inventar beider Spieler; **Mini-Karte** der Arena
    (Draufsicht, beim Speichern der Vorlage aus den Block-Kartenfarben erzeugt) mit Spieler-Positionen und
    Blickrichtung; Kampf-Log (Treffer: wer, wen, Schaden, Waffe, Ursache; Perlen, Totems, Grenzverletzungen).
  - Spiele: alle Runden aus der SQLite-Datenbank (siehe 8.), Suche nach Game-ID, Spieler, Arena, Kit, Ergebnis,
    Zeitraum; Detail-Seite `/pvp/game/1042` mit allem inkl. der Snapshots als Inventar-Ansicht (Item-Icons,
    Tooltip mit Name/Verzauberungen/Haltbarkeit) und dem Kampf-Log auf einer Zeitleiste.
  - Aktionen: Duell abbrechen (Unentschieden), Spieler aus Duellen sperren (Dauer + Grund, wie MRsleep-Ban),
    Einladungen löschen, Slot zurücksetzen/leeren, Pool neu füllen.
- **Live-Updates:** MRadmin hat schon Server-Sent Events (`WebEvents`, nur „schau nochmal“, kein Inhalt). Neu:
  `MRadminApi.webEvent(String plugin, String id)` (MRadminApi Version 2), MRpvp ruft das bei Start/Ende/Treffer
  auf – gedrosselt (max. 2/s pro Duell); die Seite lädt dann `/api/mrpvp/duel/{id}` neu. Ist die Detail-Seite offen,
  zusätzlich Polling 1/s für die Positionen auf der Mini-Karte.
- **Überwachung / Auffälligkeiten** (`activity` + Discord-Admin-Log optional): sehr kurze Duelle zwischen denselben
  Spielern in Serie (Stats-Farming, falls eigene Statistik), Disconnects mitten im Duell, Grenzverletzungen,
  auffällig hoher Schaden pro Treffer, Duelle ohne Treffer. Schwellwerte in der config.
- Rechte (MRpvp-eigene, MRadmin prüft wie bei MRsleep): `mrpvp.admin.gui` (ansehen), `mrpvp.admin.stop`,
  `mrpvp.admin.ban`, `mrpvp.admin.arena` (Slots/Pool).
- MRadmin-Adminmode/Freeze blockieren Duelle; Freeze oder Bann während eines Duells → Duell endet sofort
  (Unentschieden), Zustand wird wiederhergestellt.

#### Sonstige
- **MRchunks**: kein Claimen/Laden in der Arena-Welt (MRchunks fragt `MRpvpApi.isArenaWorld`).
- **squaremap**: Arena-Welt ausblenden (squaremap-Config, im README dokumentiert).

### 8. Spiele-Datenbank & Game-ID

**Speicher:** SQLite, eine Datei `plugins/MRpvp/mrpvp.db`. Treiber wie bei MRadmin über `libraries:` in der
plugin.yml (`org.xerial:sqlite-jdbc`), WAL-Modus, alle Schreibzugriffe über **einen** Storage-Thread in Reihenfolge.
Schema-Version in einer Meta-Tabelle, Migrationen wie in MRadmins `SqlStorage`. (MySQL später möglich, jetzt nicht.)

**Game-ID:** fortlaufende Zahl (`AUTOINCREMENT`), angezeigt als `#1042`. Eine Runde = ein Spiel. Kommt später
Best-of-N, fasst eine `match_id` mehrere Spiele zusammen – jede Runde behält ihre eigene Game-ID.

**Tabellen (Entwurf):**
```
games          id, match_id?, arena, arena_version, slot, kit, inviter, started_at, ended_at,
               result (WIN | DRAW | ABORTED), end_reason (KILL | QUIT | TIMEOUT | SURRENDER | ADMIN | BORDER | CRASH),
               winner?, stopped_by?, plugin_version
game_players   game_id, uuid, name, side (1|2), health_end, damage_dealt, damage_taken, hits, crits,
               arrows_shot, arrows_hit, totems, pearls
snapshots      game_id, uuid, phase (JOIN | END), taken_at,
               inventory (36), armor (4), offhand, enderchest (27),     -- je Item serializeAsBytes, als ein Blob
               toolbar_belts (aus MRtoolbarApi, null ohne MRtoolbar),
               level, exp, health, max_health, food, saturation, effects, gamemode, world, x, y, z, yaw, pitch,
               restored_at?                                             -- null = noch nicht zurückgegeben
events         game_id, t (ms seit Start), type (HIT | SHOT | TOTEM | PEARL | BORDER | QUIT | ...),
               actor?, target?, damage?, cause?, weapon?, x, y, z
positions      game_id, t, uuid, x, y, z, yaw            -- alle 500 ms, für Mini-Karte/Wiedergabe
bans           uuid, until?, reason, by, created_at
```
- **JOIN-Snapshot** = Zustand beim Betreten. Er ist gleichzeitig die **Wiederherstellungs-Quelle** (ersetzt die
  Datei aus dem ersten Entwurf): `restored_at` leer → beim Join/Server-Start zurückgeben. Eine Quelle der Wahrheit.
- **END-Snapshot** = Inventar beim Spielende (was vom Kit übrig war) – für Auswertung („mit wie viel Leben/Äpfeln
  hat er gewonnen“).
- Items als Paper-Bytes (`serializeAsBytes`) → bleiben über MC-Updates lesbar (DataFixer). Für die Anzeige
  werden sie beim Lesen in Material, Menge, Name, Verzauberungen, Haltbarkeit übersetzt.
- **Aufbewahrung:** `history.days` (Standard 30) für `events`/`positions`/END-Snapshots; Spiele + JOIN-Snapshots
  `history.snapshot-days` (Standard 90). Nie gelöscht werden JOIN-Snapshots mit leerem `restored_at`.
  Aufräumen einmal täglich asynchron.

**Auswertung In-Game** (Admin-Menü → Spiele, Recht `mrpvp.admin.gui`):
- Liste (neueste zuerst, blättern), Filter-Buttons: Spieler, Arena, Kit, Ergebnis; „Game-ID suchen“ per Dialog.
- Detail: Köpfe beider Spieler, Ergebnis, Grund, Dauer, Statistik-Werte, Buttons:
  - **Inventar beim Betreten** (Spieler 1 / 2) – Read-only-Kiste im Spieler-Layout: Hauptinventar, Hotbar,
    4 Rüstungs-Slots, Offhand; Seiten für Enderchest und MRtoolbar-Gürtel (je Tool-Slot eine Reihe, gewähltes
    Tool markiert); Infos (Level, Leben, Hunger, Effekte, Position) als Item-Tooltips.
  - **Inventar beim Ende**, **Kampf-Log** (Buch), **Zurückgeben erzwingen** (nur falls nicht wiederhergestellt,
    Recht `mrpvp.admin.restore`).
- Read-only wie bei MRadmin (keine Items herausnehmbar – bewusst, gegen Dupes).

**Auswertung Web** (MRadmin, Schnittstelle aktiv): siehe 7. – gleiche Daten über `web("games" | "game", …)`.
Item-Bytes verlassen MRpvp nicht; MRpvp liefert fertige Daten (Material-ID, Menge, Name, Lore, Verzauberungen,
Haltbarkeit, Slot) an MRadmin, die Seite zeigt sie mit Item-Icons an.

**Spieler-Seite:** Spieler sehen am Ende ihre Game-ID und können sie im MRadmin-Ticket (`/report`) angeben →
im Ticket als Link zum Spiel (wenn beide Plugins aktiv).

### 9. Spieler-GUI & Statistik

**Hauptmenü `/duel`** (für alle mit `mrpvp.use`):
```
┌───────────────────────────────────────────┐
│ [Herausfordern] [Einladungen (2)] [Warteschlange verlassen]
│ [Meine Statistik] [Bestenliste] [Meine Spiele]
│ [Einstellungen]                     [Admin*]   * nur mit mrpvp.admin.gui
└───────────────────────────────────────────┘
```

**Meine Statistik** (eigener Kopf; auch für andere Spieler aufrufbar über Bestenliste/Einladung):
- Spiele, Siege, Niederlagen, Unentschieden, **Siegquote**
- **Kills, Tode, K/D**
- Aktuelle und beste Siegesserie
- Schaden ausgeteilt/eingesteckt, Ø Schaden pro Spiel, Bogen-Trefferquote, Totems, Ø Spieldauer
- Lieblings-Kit und -Arena; Unterseite **pro Kit** (Siege/K/D je Kit)
- **Direktvergleich**: gegen einen bestimmten Gegner (Siege : Niederlagen) – im Einladungs-GUI automatisch angezeigt

**Bestenliste**: Top 45 (Kopf-Raster), umschaltbar nach Siege / K/D / Siegquote / beste Serie, gesamt oder pro Kit,
Zeitraum gesamt / 30 Tage / 7 Tage. K/D und Quote erst ab `stats.min-games` (Standard 10) Spielen. Eigener Platz
wird unten angezeigt.

**Meine Spiele**: eigene letzte Spiele (Game-ID, Gegner, Ergebnis, Kit, Arena, Dauer, Datum) → Detail mit eigener
Statistik der Runde und Kampf-Log. Inventar-Snapshots sehen nur Admins (Datenschutz zwischen Spielern).

**Zählregeln** (konfigurierbar, `stats.*`):
- Kill/Tod = Spiel endet durch KILL (bzw. BORDER/Void, wenn `border-counts-as-kill`).
- QUIT = Niederlage + Tod für den Leaver (`quit-counts-as-death`, Standard true), Sieg ohne Kill für den anderen.
- ABORTED/ADMIN/CRASH/TIMEOUT-Unentschieden zählen nicht für K/D.
- **Anti-Farming**: gegen denselben Gegner zählen nur `stats.max-counted-per-opponent-per-day` Spiele (Standard 50, früher 5; Issue #7);
  Spiele unter `stats.min-duration` (Standard 10 s) zählen nicht. Auffälliges landet in MRadmins Überwachung.
- Daten: berechnet aus `games`/`game_players`, dazu eine Cache-Tabelle `player_stats` (uuid, kit, Werte), bei
  Spielende aktualisiert – Bestenliste ohne teure Abfragen. Admin-Button „Statistik zurücksetzen“ (Spieler oder alle,
  mit Bestätigung). Spiele bleiben für die Statistik auch nach `history.days` (nur Log/Positionen werden gelöscht).

**Admin-Menü** (Button im Hauptmenü oder `/duel admin`): Live (laufende Duelle → Zuschauen, Abbrechen),
Spiele, Arenen (Editor, Pool, Slots), Kits (Kit-Editor), Spieler (Statistik, Sperren, Statistik zurücksetzen,
Zurückgeben erzwingen), Standard wiederherstellen, Reload.

### 10. Befehle & Rechte
Befehle sind nur Einstiegspunkte in die GUIs:
- `/duel` – Hauptmenü; `/duel <spieler>` – Herausfordern mit vorausgewähltem Spieler; `/duel leave` – aufgeben
  (zusätzlich als Item in der Hotbar während des Countdowns/als Notausgang)
- `/duel admin` – Admin-Menü; `/mrpvp reload|info` (auch für die Konsole)
- Intern (für Chat-Links, nicht dokumentiert): `/duel invite <id>` öffnet das Einladungs-GUI, `/duel game <id>` das
  Spiel-Detail
- Permissions: `mrpvp.use`, `mrpvp.kit.<name>`, `mrpvp.arena.<name>`, `mrpvp.bypass.cooldown`, `mrpvp.admin`,
  `mrpvp.admin.gui`, `mrpvp.admin.stop`, `mrpvp.admin.ban`, `mrpvp.admin.arena`, `mrpvp.admin.restore`

## Phasen

1. **Grundgerüst**: Projekt, config/lang, SQLite-Storage + Schema, Arena-Welt anlegen, Snapshots
   sichern/wiederherstellen.
2. **Duell-Kern**: Game-ID, Einladung, Kits, falscher Tod, Regeln, Quit/Crash-Sicherheit, Kampf-Log/Positionen.
3. **Vorlagen & Slots**: Editor, `.nbt` speichern, Slot-Raster, Pool, Zurücksetzen.
4. **GUIs & Inhalte**: Hauptmenü, Einladungs-GUIs, Statistik + Bestenliste + Meine Spiele, Admin-Menü mit
   Spiele-Auswertung, Kit-Editor, Arena-Editor-Hotbar, Advancements/Statistik-Schutz,
   Standard-Arena bauen, Standard-Kits.
5. **Schnittstellen**: `MRpvpApi`; MRsleep (`reason-pvp`), MRtoolbar (Pause, `MRtoolbarApi.belts`, Dupe-Tests), MRchunks.
6. **MRadmin**: MRadminHook v4, `MRadminApi.webEvent`, PvpRoutes, Web-Seiten (Live, Spiele, Spiel-Detail mit
   Inventaren und Mini-Karte), Game-ID in Tickets.
7. Release-Vorbereitung (README, CHANGELOG, Workflows, Repo). Änderungen an MRsleep/MRtoolbar/MRadmin jeweils als
   eigene Releases dieser Plugins (unabhängig von MRpvp nutzbar).

## Entscheidungen zu den früheren offenen Punkten (2026-10-22, „Abfahrt“)

- Rundenmodus: vorerst **1 Runde**; `match_id` ist für Best-of-N vorbereitet.
- Zuschauer: nur **Admins** (Admin-Menü → Live → Zuschauen), Spieler nicht.
- Einsätze: **keine**.
- Arena-Größe: bis ca. **64×64** (Einfügen in einem Tick).
- Vorlagen: nur im **Editor-Bereich** der Arena-Welt.
- Kits: die fünf Standard-Kits, **Netherite ohne Totem**.
- Mini-Karte: **ja**.
- Auffälligkeiten: zusätzlich ins **Discord-Admin-Log** von MRadmin (eigener Schalter).
- Multiverse: unbekannt → MRpvp verwaltet die Welt selbst; ein Welt-Manager darf sie nicht verwalten (config-Hinweis).

## Stand

### Phase 1 – erledigt (2026-10-22)
- Projekt nach MR-Muster (Gradle, Java 25, config/lang mit BOM, ConfigUpdater, Messages mit Sprache pro Spieler,
  UpdateChecker, GUI-Basis Menu/MenuListener/Items, Tests, Workflows, LICENSE).
- `storage/Database`: SQLite über `libraries:` (sqlite-jdbc 3.53.4.0 wie MRadmin), **ein** Storage-Thread mit der
  einzigen Verbindung, WAL; `submit` (asynchron) und `call` (wartet). `storage/Schema` Version 1: `meta`, `games`,
  `snapshots` (weitere Tabellen kommen in Phase 2, solange nichts veröffentlicht ist noch in Version 1).
- `snapshot/*`: `Snapshot` (reine Daten), `SnapshotStore` (SQL), `PlayerState` (erfassen/leeren/zurückgeben),
  `StateKeeper` (Sitzungen, Login/Join/Quit, Shutdown).
  - Regel: Zustand ist **in der Datenbank, bevor** das Inventar angefasst wird, und wird **als zurückgegeben markiert,
    bevor** er ausgegeben wird (beides wartet bewusst ein paar ms auf die DB – kein Dupe-Fenster). Schlägt das Ausgeben
    fehl, wird die Markierung zurückgenommen. Nach dem Ausgeben `player.saveData()`.
  - Offene Zustände werden im `AsyncPlayerPreLoginEvent` gesucht und beim Join ausgegeben; mehrere offene → nur der
    älteste wird ausgegeben (Warnung).
  - Wer ohne Sitzung in der Arena-Welt einloggt, kommt zum Spawn.
- `world/ArenaWorld` + `VoidGenerator`: Welt `mrpvp_arenas` (Biom the_void), Regeln bei jedem Start/Reload.
- `/mrpvp version | reload | debug arena | leave | state [spieler]` (debug = Test des Sicherns ohne Runde).
- Getestet: Unit-Tests (29, u.a. SnapshotStore gegen echte SQLite-Datei), Testserver: Start, Welt angelegt,
  Konsolenbefehle. **Im Spiel noch zu testen:** `/mrpvp debug arena` → Inventar weg, in der Arena-Welt →
  `debug leave` bzw. Aus-/Einloggen bzw. Server-Neustart → alles wieder da.

### Für Phase 2 vorgemerkt
- Kit-Items bekommen einen PDC-Marker (`mrpvp:kit`); beim Join und beim Zurückgeben werden markierte Items überall
  entfernt → selbst wenn etwas schiefgeht, verlässt kein Kit-Item die Arena.

### Phase 2 – gebaut (2026-10-22), Test im Spiel ausstehend
- Schema Version 2: `game_players`, `events` (Kampf-Log), `positions` (alle 10 Ticks, für Mini-Karte/Wiedergabe).
- `kit/*`: `kits.yml` (lesbares YAML, 5 Standard-Kits: klassisch, diamant, netherite ohne Totem, bogen,
  streitkolben; `data:` für Base64-Items aus dem späteren Kit-Editor). Jedes ausgegebene Item trägt `mrpvp:kit`
  (Game-ID); beim Join werden solche Items aus Inventar und Enderchest entfernt (Sicherheitsnetz).
- `arena/*`: Schnittstelle `ArenaProvider` / `ArenaInstance`; vorläufig `BuiltinArenas` (per Code gebaute
  33×33-Arena mit Glaswänden und zwei Säulen, Slot n bei x = n·1000). Phase 3 ersetzt das durch Vorlagen.
- `game/*`:
  - `InviteManager` (ohne Bukkit, getestet), Einladung mit Ablaufzeit und Cooldown, Chat-Link **[Ansehen]** öffnet
    das Einladungs-GUI.
  - `GameManager`: Start = Game-ID anlegen → beide Zustände sichern (scheitert der zweite, bekommt der erste alles
    zurück) → Teleport, Kit, Countdown (eingefroren) → Kampf (Actionbar: Gegner-Leben, Zeit) → Ende (Ergebnis,
    Zahlen, END-Snapshots, Kampf-Log in einem Rutsch) → nach `end-delay-seconds` Rückgabe, Arena frei.
    Server-Stopp: alle Runden als ABORTED/SHUTDOWN geschrieben, dann Rückgabe. Beim Start: unbeendete Runden →
    ABORTED/CRASH.
  - `GameListener`: **kein echter Tod** (tödlicher Schaden auf HIGHEST abgebrochen → Niederlage; Totem nur wenn
    `duel.totems`), Notnetz für PlayerDeathEvent (nichts droppt, keine Nachricht); Treffer/Crits/Pfeile/Perlen/Totems
    gezählt; Countdown friert ein; Region-Grenze (seitlich zurück, unten = Niederlage BORDER), Perle/Chorus aus der
    Arena blockiert; Befehle nur aus `duel.allowed-commands` (Bypass `mrpvp.bypass.commands`); Drop/Bauen/Abbauen
    gesperrt; Quit = Niederlage (Countdown: Abbruch).
- GUIs: Hauptmenü (Herausfordern, Einladungen, im Duell: Aufgeben), Spielerauswahl (Köpfe, Seiten), Kitauswahl
  (Inhalt im Tooltip), Einladungsliste, Einladung (Annehmen/Ablehnen). `/duel [spieler]`, intern `/duel invite <id>`,
  `/duel leave`.
- Rechte: `mrpvp.player` (default true) → `mrpvp.use`; `mrpvp.bypass.cooldown`, `mrpvp.bypass.commands`.

### Phase 3 – gebaut (2026-10-22), Test im Spiel ausstehend
- `arena/TemplateManager`: Vorlagen in `templates/<id>.nbt` (Vanilla-StructureManager, ohne WorldEdit) +
  `<id>.yml` (Name, Symbol, Version, Größe, Spawns relativ, Bauen/Bauhöhe, Kits, aktiv, Pool, Editor-Bauplatz).
- `arena/SlotManager` (ersetzt BuiltinArenas als Provider, BuiltinArenas bleibt Fallback ohne Vorlagen):
  Raster ab z = 2000 (32 pro Reihe, Abstand `arenas.spacing`), `slots.yml`. Pool pro Vorlage (`pool`), Einfügen
  aus einer Warteschlange (Chunks async laden, dann einfügen, max. 1 gleichzeitig), Dauer wird gemessen (> 250 ms
  → Hinweis in der Konsole). Nach der Runde: Vorlage neu einfügen (+ Entities entfernen). Nach Neustart: alle
  belegten Plätze neu einfügen. Neue Version / deaktiviert / gelöscht → freie Plätze neu bzw. abgeräumt,
  belegte nach ihrer Runde. Kein freier Platz → sofort einfügen (einmaliger Lag) bis `arenas.max-slots`.
- `arena/ArenaProtection`: im Slot-Raster fließen Flüssigkeiten nicht hinaus, Explosionen/Kolben nur innerhalb.
- Bauen pro Vorlage: Survival statt Adventure, nur in der Region und unter der Bauhöhe, nichts droppt.
- `arena/DefaultArena`: Standard-Arena „standard“ (45×12×45: Steinboden mit Muster, Blackstone-Wände, 4 Säulen,
  niedrige Mauern, erhöhte Mitte, rote/blaue Spawn-Felder) wird beim ersten Start per Code im Editor-Bereich gebaut
  und als normale Vorlage gespeichert → im Editor bearbeitbar. `data.yml` merkt sich die Installation (gelöscht =
  bleibt gelöscht).
- `editor/*`: Editor-Bereich z = -20000, jede Vorlage hat ihren Bauplatz (Quelle des Baus). Sitzung = Zustand sichern
  (Zweck EDITOR) + Kreativ + Werkzeug-Hotbar (Auswahl-Stab, Spawn 1/2, Einstellungen, Speichern/Beenden),
  Partikel-Rahmen und Spawn-Marker, Actionbar-Status. Sperren: Enderchest, Welt verlassen, Werkzeuge droppen.
  Speichern prüft Auswahl, Größe (`arenas.max-width/height`), Spawns in der Auswahl; Version +1.
  Verwerfen: bestehende Vorlage → Bau auf gespeicherten Stand zurück; neue → Bauplatz geräumt.
- GUIs: Admin-Menü (`/duel admin` oder Knopf im Hauptmenü, `mrpvp.admin.gui`) → Arenen (`mrpvp.admin.arena`):
  Liste, Neue Arena (Name per Dialog), pro Arena Bearbeiten / Aktiv / Pool ± / Neu aufbauen / Löschen (Bestätigung).
  Editor: Einstellungen (Anzeigename per Dialog, Symbol = Item in der Hand, Bauen, Bauhöhe, erlaubte Kits),
  Beenden-Menü. Spieler: Arena-Auswahl (inkl. „Zufällig“) zwischen Spieler- und Kit-Auswahl (übersprungen bei ≤ 1
  Arena), Kits nach Arena gefiltert; Einladungen zeigen die Arena.
- `gui/TextPrompt`: Texteingabe als Paper-Dialog (wie MRadmin).

### Phase 4 – gebaut (2026-10-22), Test im Spiel ausstehend
- Schema 3: `games.counted`, `game_players.outcome/kills/deaths` (beim Rundenende einmal entschieden, mit den Regeln
  von dann), `player_stats` (Summen pro Spieler und Kit, '' = alle; mit Serien), `stats_resets`, `bans`.
- `stats/StatsStore` (getestet): Zählregeln (`stats.*`: Mindestdauer, max. Runden pro Gegner/24 h, Quit/Aufgeben =
  Tod, Grenze = Kill), Serien (Unentschieden hält sie), Bestenliste (Siege, K/D, Quote, beste Serie; gesamt/30/7 Tage;
  pro Kit; Quoten erst ab `min-games-for-ratio`), Direktvergleich, Lieblings-Kit/-Arena, Reset (Runden bleiben).
  Spieler bekommen am Ende einen Hinweis, wenn eine Runde nicht zählt.
- `stats/GameQueries`: Spiele-Liste (Filter, Seiten), Spiel mit beiden Spielern, Kampf-Log, tägliches Aufräumen
  (`history.days` für Log/Positionen/END-Snapshots, `history.snapshot-days` für zurückgegebene JOIN-Snapshots).
- Schutz: Advancements in der Arena-Welt werden nicht angekündigt und sofort entzogen; Minecraft-Statistik (ohne
  Zeitwerte) wird nach der Runde/dem Editor/dem Zuschauen zurückgesetzt (`protection.*`).
- Spieler-GUIs: Hauptmenü neu (Herausfordern, Einladungen, Meine Statistik, Bestenliste, Meine Spiele), Statistik
  (pro Kit umschaltbar, Direktvergleich bei fremden Spielern), Bestenliste (Sortierung/Kit/Zeitraum, eigener Platz),
  Meine Spiele → Spiel-Detail (Zahlen beider, Kampf-Log als Buch; Inventare nur für Admins). Einladung zeigt Siege,
  K/D und den Direktvergleich des Herausforderers.
- Admin-GUIs: Live (Leben beider live, Klick = zuschauen im Spectator-Modus mit gesichertem Zustand, Shift = abbrechen),
  Spiele (alle, Filter, Game-ID-Suche) → Detail mit **Inventar beim Betreten/Ende** (Spieler-Layout, Enderchest-Seite,
  MRtoolbar-Gürtel-Seite) und „Zurückgeben" für offene Zustände, Arenen, **Kit-Editor** (arbeitet nur mit Kopien –
  kein Dupe möglich; „mein Inventar übernehmen“, Name/Symbol/Beschreibung/Recht), Spieler (Statistik, Spiele,
  Sperren mit Dauer/Grund, Statistik-Reset, offene Rückgaben), Standard wiederherstellen (fehlende Kits + Arena).
- Rechte: `mrpvp.admin.games|restore|spectate|stop|kits|players|ban|reset|defaults` (alle unter `mrpvp.admin`).
- Format der MRtoolbar-Gürtel im Snapshot festgelegt (`snapshot/ToolbarBelts`, Version 1) – befüllt in Phase 5.

### Phase 5 – gebaut (2026-10-22), Test im Spiel ausstehend
- **MRpvp** `api/MRpvpApi` (API_VERSION 1): `inGame(UUID)` = `StateKeeper.busy` – gilt schon **vor** dem Sichern und
  bis **nach** der Rückgabe, und auch ab dem Login, wenn noch ein Zustand offen ist (sonst würde MRtoolbar ein
  Gürtel-Tool ins Arena-Inventar legen, das die Rückgabe gleich danach überschreibt); `isArenaWorld(String)`.
  `api/MRtoolbarBelts` liest die Gürtel per `MRtoolbarApi.belts` beim Sichern (Snapshot-Spalte toolbar_belts).
- **MRtoolbar** (beta, nicht committet): `api/MRpvpGame`, `paused()` auch bei MRpvp (`toolbar.pause-in-pvp`,
  config-version 9). Fix für alle Pausen (auch Adminmodus): `/toolbar on|off|takeback` gesperrt, `enforceLimits`
  wartet – vorher konnten Tools im Kreativ-Inventar landen. Text `toolbar-paused` ersetzt `admin-mode-paused`.
  Neues `api/MRtoolbarApi` (API_VERSION 1): `paused(UUID)`, `belts(UUID)`. Kein Protokoll-Wechsel zum Mod.
- **MRsleep** (beta, nicht committet): `api/MRpvpGame`, Grund `reason-pvp`, `sleep.ignore-pvp` (config-version 7),
  Arena-Welt nie verwaltet.
- **MRchunks** (beta, nicht committet): `api/MRpvpArenaWorld`, keine Loader in der Arena-Welt.
- squaremap: Arena-Welt in der squaremap-Config ausblenden (kommt ins README).

### Phase 6 – gebaut (2026-10-22), Test im Spiel/Web ausstehend
- **MRpvp:** `api/MRadminHook` (API 4: summary, playerSummary, openPlayerMenu, activity/openActivity, web),
  `api/WebData` (overview, live, games, game, player, flags, alerts, action stop|ban|unban|reset|giveBack),
  `api/ItemView` (Items als einfache Daten), `arena/ArenaMap` (Draufsicht aus Block-Kartenfarben, pro Vorlage+Version
  gecacht), `api/MRadminBridge` (adminMode → keine Duelle im Adminmodus; webEvent gedrosselt 2/s; adminLog),
  `game/Monitor` (Farmen nur bei einseitigen Serien, `game/FarmingCheck` - Issue #7, Quit im Kampf, Grenzversuche, starker Treffer ohne Streitkolben, lange Runde ohne Treffer;
  24 h im Speicher; Konsole Englisch, Discord in Serversprache; `monitor.*`, config-version 3), Admin-Menü
  „Auffälligkeiten“.
- **MRadmin** (beta, nicht committet): `MRadminApi` v2 (`webEvent`, `adminLog`), `WebEvents.plugin` (nur an Seiten
  mit Recht), Integration `mrpvp`, `PvpRoutes`, Spielerteil + Markierungen, `/me`-Rechte, Discord-Ereignis
  `plugin-alert`, config-version 18. Web: Seite „Duelle“ (Live/Spiele/Arenen/Auffälligkeiten), Spiel-Detail
  (live oder beendet; Inventare als Raster mit Name/Anzahl/Tooltip – keine Texturen, Mini-Karte mit Wiedergabe,
  Kampf-Log), `PvpPart` auf der Spielerseite.
- Offen: MRadmins Server-Zustand zeigt nur die Auffälligkeiten des ersten Plugins (MRchunks) – MRpvp-Hinweise sind
  im MRpvp-Admin-Menü und im Web.

### Nachtrag – Matches, Optionen, Kits, Arenen (2026-10-23), Test im Spiel ausstehend
- **Optionen beim Herausfordern** (Menü nach der Kit-Auswahl): Siege bis Gewinn (`duel.options.wins`), Zeitlimit
  pro Runde (`duel.options.minutes`), Hunger, natürliche Regeneration. Kits können Hunger/Regeneration festlegen.
- **Matches**: „First to N“ = mehrere Runden, jede Runde eigene Game-ID (`games.match_id`, `games.round`), Ergebnis
  in `matches` (Schema 4). Zwischen den Runden bleiben beide in der Arena-Welt, Zuschauer wandern mit.
  Unentschieden zählen nicht; nach 2·N+1 Runden ist trotzdem Schluss.
- **Kit-Regeln** (`rules:` in kits.yml): `damage: false` (nur Rückstoß), `hits-to-win`, `regen`, `hunger`,
  `arenas`, `build`. Neues Item-Feld `potion`.
- **Neue Standard-Kits**: Elytra & Streitkolben, UHC, Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axt & Schild,
  Dreizack (bestehende Server: Admin-Menü → „Standard wiederherstellen“).
- **Neue Standard-Arenen**: `sumo` (Plattform über dem Void, nur Sumo), `bau` (Bauen erlaubt, Bedrock-Boden).
- **Luft über der Arena**: `arenas.headroom` (Standard 150 statt fest 20) – für Elytra und Streitkolben.
- Offen: Match-Zahlen in der Statistik/Web (Tabelle `matches` ist da), Boxing-Trefferzähler in der Statusleiste.

### Nachtrag – Prüfung, Bossbar, ein Einladungsmenü (2026-10-23), Test im Spiel ausstehend
- **Prüfung statt Ausschluss** (Schema 5, `review.*`): Auffälliges wird als Markierung an der Runde gespeichert
  (`games.flags`: farming, limit, short, hit, border, idle, quit). Steht eine davon in `review.hold`, wartet die
  Runde (`games.review = PENDING`) und zählt nicht; werten → die Statistik beider wird in Reihenfolge neu aufgebaut
  (`StatsStore.rebuild`), verwerfen → zählt nie. Nach `review.auto-days` (7) automatisch `count`.
  „short“ = kürzer als `stats.min-duration-seconds` **und** ohne Treffer (Sumo-Runden mit Treffern sind normal).
  Admins: Admin-Menü → Prüfen, Knöpfe im Rundenmenü, Chat-Hinweis mit [Prüfen] (`/duel review`), beim Betreten
  die Anzahl, Discord-Admin-Log. MRadmin: Zähler im Knopf, wartende Runden oben in der Server-Übersicht, Web-Reiter
  „Prüfen“ und Knöpfe auf der Rundenseite (`GET /api/mrpvp/reviews`, Aktionen count/discard, Recht
  `mrpvp.admin.review`). Alles über die bestehende Hook-Methode `web()` – keine neue Abhängigkeit.
- **Bossbar** im Duell (Runde, Stand, Gegner-Herzen, Restzeit, bei Boxing Treffer; Zuschauer sehen beide).
  Kein Chat mehr während des Duells – eine Zusammenfassung am Ende (bei Matches mit Schaden aller Runden, Hinweis
  „wird geprüft“). Rundenstart setzt Schwung/Fallhöhe/Feuer zurück.
- **Ein Einladungsmenü** (`DuelSetupMenu`): Kits, Arena (durchschalten), Optionen, Senden; die letzten
  Einstellungen pro Spieler sind gemerkt (`invite_prefs`). Eingeladene antworten im Chat: [Annehmen] [Ablehnen]
  [Details]. ArenaSelectMenu, KitSelectMenu und OptionsMenu sind weg.
- Danach (2026-10-23): XXL-Arena `xxl` (128 × 64 × 128 schwebende Inseln, Ringe, nur Elytra-Kit, leerer Raum als
  Strukturleere gespeichert → Einfügen setzt nur echte Blöcke); Editor erlaubt bestehenden Arenen ihre eigene Größe.
  Bestenliste als Podest (Platz 1–3 Köpfe auf Gold/Eisen/Kupfer, darunter 4–21). Bossbar-Trenner „|“.
- Danach: Arenen können „nur Kits mit Elytra“ sein (`elytra-only` im Template, Editor-Schalter); XXL nutzt das statt
  einer festen Kit-Liste. Einladung als kompakte Seite: gewähltes Kit / gewählte Map (Klick öffnet die Auswahl),
  Optionen, Senden. Web: Herzen statt Balken, Punkt auf der Mini-Karte leuchtet beim Treffen (gelb) / Getroffenwerden
  (rot) - MRpvp liefert `lastHitAt`/`lastHurtAt` und `now`.
- Danach: **Verlassen**: `/duel leave` ist jederzeit eine Niederlage im ganzen Match (auch Countdown und Pause
  zwischen Runden; `Match.forfeit`), nur vor Runde 1 wird abgebrochen. **Verbindung weg** (Absturz/Server verlassen):
  die Runde pausiert `duel.reconnect-seconds` (20) – Gegner eingefroren und unverwundbar, Rundenzeit steht
  (`Game.pause/resume`, `elapsed` ohne Pausen), Bossbar mit Restzeit; zurück → Countdown, weiter. Einmal pro Match und
  Spieler, nur solange der Gegner da ist; sonst / ohne Rückkehr → Niederlage. Zwischen zwei Runden wartet die nächste
  Runde genauso. StateKeeper behält die Sicherung so lange (`waitingFor`, Login gibt nichts zurück).
