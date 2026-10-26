# MRpvp

Duelle in eigenen Arenen für Paper 26.3. Ein Spieler fordert einen anderen heraus, beide kämpfen in einer eigenen
Arena mit einem vorgegebenen Kit und bekommen danach **alles genau so zurück**, wie es vorher war: Inventar, Rüstung,
Zweithand, Enderchest, XP, Leben, Hunger, Effekte, Spielmodus und Position. Im Duell stirbt niemand wirklich: keine
Tode und Kills in der Minecraft-Statistik, keine Todesnachricht, keine Drops, keine Advancements. MRpvp führt eine
**eigene Statistik** mit K/D, Siegen, Serien und Bestenliste.

Im Spiel läuft alles über Menüs, für Spieler und für Admins. Befehle öffnen nur die Menüs.

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis 1.0.0 können sich Config, Menüs und Permissions noch
> ändern.

## Installation
1. Paper **26.3** mit **Java 25**.
2. `MRpvp-mc26.3-v….jar` aus den [Releases](https://github.com/therealmarci/mrpvp/releases) in `plugins/` legen und
   den Server starten. Paper lädt den SQLite-Treiber beim ersten Start selbst herunter (Ordner `libraries/`).
3. Beim ersten Start legt MRpvp die Arena-Welt `mrpvp_arenas` an (leere Void-Welt) und baut die Standard-Arenen und
   Standard-Kits auf. Fertig - `/duel` öffnet das Menü.

## So läuft ein Duell
- `/duel` → **Herausfordern** → Spieler anklicken → eine Seite mit **Kit**, **Map** (Klick öffnet jeweils die
  Auswahl) und den Optionen → **Einladung senden**. Die letzten Einstellungen sind vorausgewählt.
- Der Eingeladene bekommt im Chat **[Annehmen] [Ablehnen] [Details]**.
- Optionen: **Siege bis Gewinn** (1, 2, 3, 5 - mehrere Runden als Match), **Zeitlimit pro Runde**, **Hunger** und
  **natürliche Regeneration**. Kits können Hunger oder Regeneration fest vorgeben (z.B. UHC ohne Regeneration).
- Im Duell zeigt die **Bossbar** Runde, Spielstand, Gegner mit Herzen und Restzeit (bei Boxing die Treffer). Im Chat
  kommt nur eine Zusammenfassung am Ende.
- **Aufgeben** (`/duel leave`) ist eine Niederlage im ganzen Match - nur vor Runde 1 wird einfach abgebrochen.
- **Verbindung weg** (Absturz, Server verlassen): Die Runde pausiert 20 Sekunden, der Gegner ist eingefroren. Wer
  rechtzeitig zurückkommt, spielt nach einem kurzen Countdown weiter - einmal pro Match. Sonst ist es eine Niederlage.
- Die eigenen Sachen sind sicher in der Datenbank gespeichert, bevor irgendetwas am Inventar geändert wird - auch ein
  Server-Absturz verliert nichts. Wer gerade offline ist, bekommt alles beim nächsten Login.

## Kits und Arenen
Mitgeliefert werden die Kits **Klassisch, Diamant, Netherite, Bogen, Streitkolben, Elytra & Streitkolben, UHC,
Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axt & Schild** und **Dreizack** sowie die Arenen **Standard**, **Sumo**
(Plattform über dem Void), **Bau-Arena** (Bauen erlaubt) und **XXL-Himmel** (128 × 128 schwebende Inseln, nur für
Kits mit Elytra).

- **Kits** stehen lesbar in `kits.yml` und lassen sich im Spiel mit dem Kit-Editor bearbeiten (Admin-Menü → Kits).
  Kit-Regeln (`rules`): kein Schaden / nur Rückstoß (Sumo, Boxing), Sieg nach Treffern, feste Regeneration oder
  Hunger, nur bestimmte Arenen, braucht eine Bau-Arena.
- **Arenen** baut man im Spiel im Arena-Editor (Kreativmodus, Werkzeuge in der Hotbar). Gespeichert als
  Minecraft-Struktur (`.nbt`) und in einem Raster aus Kopien bereitgehalten - eine Runde startet ohne Wartezeit, nach
  der Runde wird die Kopie neu eingefügt. Pro Arena: erlaubte Kits, „nur Kits mit Elytra“, Bauen erlaubt und Bauhöhe,
  Anzahl bereiter Kopien.
- Neue Standard-Kits kommen bei Updates einmal automatisch dazu. Gelöschte Standard-Kits und -Arenen holt
  Admin-Menü → **Standard wiederherstellen** zurück.

## Statistik und Prüfung
- Spieler sehen ihre Statistik (gesamt und pro Kit), die Bestenliste (Siege, K/D, Siegquote, beste Serie; gesamt,
  30 oder 7 Tage) und ihre letzten Runden mit Kampf-Log.
- Jede Runde hat eine **Spiel-ID** (z.B. #1042). Admins sehen jede Runde mit den gesicherten Inventaren (beim
  Betreten und am Ende) und können offene Rückgaben sofort ausführen.
- **Auffällige Runden** zählen nicht einfach nicht, sie **warten auf einen Admin**. Auffällig sind z.B.
  Statistik-Farmen, zu viele Runden gegen denselben Gegner, sehr kurze Runden ohne Treffer und extreme Treffer.
  Admin-Menü → **Prüfen**: werten (die Statistik beider wird neu berechnet) oder verwerfen. Ohne Entscheidung zählt
  die Runde nach 7 Tagen (einstellbar unter `review.*`).

## Zusammenarbeit mit anderen Plugins
MRpvp läuft **allein** – kein anderes Plugin ist Pflicht, und keins braucht MRpvp. Die MR-Plugins sprechen
**über MRadmin** miteinander (Modul-Schnittstelle): Ohne MRadmin bekommen sie voneinander nichts mit. Was welche
Plugins braucht:

| Funktion | Braucht |
|---|---|
| Knopf im Dashboard, Spieler-Menü, wartende Prüfungen im Serverzustand, Hinweis „Runden warten auf eine Prüfung“ auf der Startseite im Web | MRadmin |
| Seite **Duelle** im Web: live mit Herzen und Mini-Karte, alle Runden mit Inventaren, Kampf-Log und Wiedergabe, Prüfen | MRadmin |
| Auffällige Runden im Discord-Admin-Log | MRadmin |
| Im Adminmodus kann man kein Duell starten | MRadmin |
| Die Werkzeugleiste pausiert im Duell, die Gürtel werden mitgesichert und im gesicherten Inventar angezeigt | MRadmin + MRtoolbar |
| Spieler im Duell zählen nicht zur Schlafquote, die Arena-Welt wird nie verwaltet | MRadmin + MRsleep |
| In der Arena-Welt lassen sich keine Chunkloader setzen | MRadmin + MRchunks |

## Befehle
| Befehl | Permission | Funktion |
|---|---|---|
| `/duel` (`/pvp`, `/duell`) | `mrpvp.use` | Duell-Menü: herausfordern, Einladungen, Statistik, Bestenliste, letzte Runden |
| `/duel <spieler>` | `mrpvp.use` | direkt die Einladung an diesen Spieler |
| `/duel leave` | `mrpvp.use` | aufgeben (Niederlage im Match) |
| `/duel admin` | `mrpvp.admin.gui` | Admin-Menü |
| `/mrpvp version \| reload` | `mrpvp.admin.reload` | Version anzeigen, Config und Texte neu laden |
| `/mrpvp debug …` | `mrpvp.admin.debug` | Zustand sichern/zurückgeben testen, gesicherte Zustände ansehen |

## Permissions
| Permission | Standard | Funktion |
|---|---|---|
| `mrpvp.player` | alle | alle Spieler-Rechte (`mrpvp.use`) |
| `mrpvp.use` | alle | herausfordern, Einladungen annehmen, eigene Statistik |
| `mrpvp.bypass.cooldown` | OP | keine Wartezeit zwischen Einladungen |
| `mrpvp.bypass.commands` | niemand | alle Befehle auch im Duell |
| `mrpvp.admin` | OP | alle Admin-Rechte |
| `mrpvp.admin.gui` | OP | Admin-Menü öffnen |
| `mrpvp.admin.arena` | OP | Arenen bauen, bearbeiten, aktivieren, löschen |
| `mrpvp.admin.kits` | OP | Kit-Editor |
| `mrpvp.admin.games` | OP | alle Runden mit Kampf-Log und gesicherten Inventaren |
| `mrpvp.admin.restore` | OP | offenen Zustand sofort zurückgeben |
| `mrpvp.admin.spectate` | OP | laufenden Duellen zusehen |
| `mrpvp.admin.stop` | OP | laufende Duelle abbrechen |
| `mrpvp.admin.players` | OP | Spieler-Verwaltung |
| `mrpvp.admin.ban` | OP | von Duellen sperren |
| `mrpvp.admin.reset` | OP | Statistik eines Spielers zurücksetzen |
| `mrpvp.admin.review` | OP | auffällige Runden werten oder verwerfen, Hinweise dazu |
| `mrpvp.admin.defaults` | OP | Standard-Kits und -Arenen wiederherstellen |
| `mrpvp.admin.reload` / `.debug` / `.update` | OP | `/mrpvp reload`, `/mrpvp debug`, Update-Hinweis |

Kits können eine eigene Permission haben (`permission` in `kits.yml`).

## Config (Auszug)
Alles steht kommentiert in `plugins/MRpvp/config.yml`, neue Einstellungen kommen bei Updates von selbst dazu.

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `language` | `auto` | Sprache der Texte: `auto` (wie im Minecraft-Client), `de`, `en` - Texte in `lang/` |
| `arenas.pool-per-template` | `1` | bereite Kopien pro Arena |
| `arenas.headroom` | `150` | Luft über einer Arena (Elytra, Streitkolben) |
| `duel.options.wins` / `.minutes` | `[1,2,3,5]` / `[3,5,10,15]` | Auswahl im Einladungsmenü |
| `duel.reconnect-seconds` | `20` | Wartezeit bei Verbindungsabbruch (einmal pro Match), `0` = sofort verloren |
| `duel.allowed-commands` | `msg`, `r` … | Befehle, die im Duell erlaubt sind |
| `stats.max-counted-per-opponent-per-day` | `5` | mehr Runden gegen denselben Gegner pro Tag sind auffällig |
| `review.hold` | `farming, limit, short, hit, border, idle` | was eine Runde zur Prüfung zurückhält |
| `review.auto-days` / `.auto-decision` | `7` / `count` | automatische Entscheidung |
| `protection.restore-statistics` | `true` | Minecraft-Statistik nach der Runde auf den Stand davor |
| `history.days` / `.snapshot-days` | `30` / `90` | wie lange Kampf-Logs und gesicherte Inventare bleiben |
| `monitor.*` | | Grenzen für Auffälligkeiten, Discord-Log über MRadmin |

## Dateien
| Datei | Inhalt |
|---|---|
| `config.yml` | Einstellungen |
| `kits.yml` | Kits (auch vom Kit-Editor geschrieben) |
| `lang/de.yml`, `lang/en.yml` | alle Texte (MiniMessage) |
| `templates/*.nbt` + `*.yml` | Arenen |
| `mrpvp.db` | SQLite: Runden, gesicherte Zustände, Kampf-Log, Statistik, Sperren |
| `slots.yml`, `data.yml` | intern (Arena-Kopien, installierte Standards) |

## Bauen
```
./gradlew build
```
Die JAR liegt danach in `build/libs/`. Branches und Release-Ablauf: [docs/RELEASES.md](docs/RELEASES.md),
Konzept und Stand: [docs/PLAN.md](docs/PLAN.md).

## Lizenz
Siehe [LICENSE](LICENSE): Nutzung und Veränderung für den eigenen Server erlaubt, Weiterveröffentlichung nicht.
