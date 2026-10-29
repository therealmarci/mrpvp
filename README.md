# MRpvp

**Duelle ohne Risiko:** Zwei Spieler kämpfen in einer eigenen Arena mit einem vorgegebenen Kit - und bekommen danach **alles genau so zurück**, wie es vorher war. Mit eigener Statistik, Bestenliste und Schutz gegen Statistik-Farmen.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mrpvp/)** · **[📖 Anleitung online](https://mrtechnology.marcelr.at/docs/mrpvp/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mrpvp/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - MRadmin optional |
| Sprachen | Deutsch, Englisch - automatisch wie im Minecraft-Client |
| Status | Vorabversion (0.x) - Config, Menüs und Permissions können sich bis 1.0.0 noch ändern |

## Was kann MRpvp?
- **Herausfordern per Menü:** Spieler, Kit, Arena und Regeln wählen, Einladung senden - der Gegner nimmt per Klick im Chat an.
- **Nichts geht verloren:** Inventar, Rüstung, Enderchest, XP, Leben, Hunger, Effekte, Spielmodus und Position werden **vor** dem Duell sicher gespeichert und danach zurückgegeben - auch nach einem Server-Absturz.
- **Duelle zählen nicht für Minecraft:** keine Tode und Kills in der Vanilla-Statistik, keine Todesnachricht, keine Drops, keine Advancements.
- **14 fertige Kits** (Klassisch, Netherite, UHC, Sumo, Boxing, Crystal, Elytra & Streitkolben …) und **4 Arenen** - eigene baust du im Spiel.
- **Eigene Statistik:** K/D, Siege, Serien, Bestenliste und die letzten Runden mit Kampf-Log.
- **Faire Wertung:** Auffällige Runden (z.B. abgesprochene Statistik-Farmen) warten auf eine Prüfung durch das Team.
- **Alles über Menüs** - für Spieler und für Admins.

## Schnellstart
1. **[MRpvp herunterladen](https://mrtechnology.marcelr.at/download/mrpvp/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. MRpvp legt die Arena-Welt `mrpvp_arenas` an und baut die Standard-Arenen und -Kits auf. Den Datenbank-Treiber lädt Paper beim ersten Start selbst herunter.
3. **Rechte geben** - ohne sie dürfen nur OPs Duelle spielen:
   ```text
   /lp group default permission set mrpvp.player true
   /lp group supporter permission set mrpvp.groups.support true
   /lp group staff permission set mrpvp.groups.staff true
   /lp group admin permission set mrpvp.groups.admin true
   ```
4. Fertig - Spieler legen mit **`/duel`** los.

**Update:** Die alte JAR durch die neue ersetzen und den Server neu starten. Neue Einstellungen und neue Standard-Kits kommen automatisch dazu.

---

## So läuft ein Duell
1. **`/duel`** → **Herausfordern** → Spieler anklicken. Oder direkt: `/duel <spieler>`.
2. **Kit** und **Map** wählen, dazu die Optionen:
   - **Siege bis Gewinn** (1, 2, 3 oder 5 Runden als Match)
   - **Zeitlimit pro Runde**
   - **Hunger** und **natürliche Regeneration** an oder aus (manche Kits geben das fest vor, z.B. UHC ohne Regeneration)
3. **Einladung senden.** Der Gegner bekommt im Chat **[Annehmen] [Ablehnen] [Details]**. Deine letzten Einstellungen sind beim nächsten Mal vorausgewählt.
4. Im Duell zeigt die **Bossbar** Runde, Spielstand, die Herzen des Gegners und die Restzeit. Am Ende gibt es eine Zusammenfassung im Chat - und alles zurück.

- **Aufgeben** mit `/duel leave` zählt als Niederlage im ganzen Match (nur vor Runde 1 wird einfach abgebrochen).
- **Verbindung weg?** Die Runde pausiert 20 Sekunden, der Gegner ist eingefroren. Wer rechtzeitig zurückkommt, spielt weiter (einmal pro Match).
- Wer bei der Rückgabe offline ist, bekommt alles beim nächsten Login.

## Statistik & Bestenliste
Im Duell-Menü sieht jeder Spieler seine Statistik (gesamt und pro Kit), die **Bestenliste** (Siege, K/D, Siegquote, beste Serie - gesamt, 30 oder 7 Tage) und seine letzten Runden mit Kampf-Log. Jede Runde hat eine **Spiel-ID** (z.B. `#1042`), über die das Team sie wiederfindet.

---

## Für Admins

### Admin-Menü
`/duel admin` öffnet das Admin-Menü. Von dort aus:
- **Live** laufende Duelle steuern - ein Klick auf ein Duell öffnet seine Steuerung:
  - **Zuschauen** über der Arena oder direkt **bei Spieler 1 / Spieler 2** (als Zuschauer, dein Inventar wird gesichert)
  - **Pausieren / Fortsetzen:** beide werden eingefroren, niemand nimmt Schaden, die Zeit steht
  - **Sieger bestimmen:** z.B. wenn jemand einen Bug ausnutzt - das Duell endet, der gewählte Spieler gewinnt das Match, die Runde zählt als Sieg (ohne Kill und Tod)
  - **Abbrechen:** zählt nicht, beide bekommen alles zurück
- **Runden** ansehen - mit Kampf-Log und den gesicherten Inventaren (vor und nach der Runde) - und offene Rückgaben sofort ausführen. Eine gewertete Runde lässt sich hier nachträglich **aus der Wertung nehmen** (mit optionalem Grund) und später **wieder werten** - die Statistik beider Spieler wird jeweils neu berechnet.
- **Prüfen:** auffällige Runden werten oder verwerfen
- **Spieler** verwalten, von Duellen sperren, Statistik zurücksetzen
- **Kits** und **Arenen** bearbeiten, **Standard wiederherstellen**

### Kits
Mitgeliefert: **Klassisch, Diamant, Netherite, Bogen, Streitkolben, Elytra & Streitkolben, UHC, Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axt & Schild** und **Dreizack**.
- Bearbeiten im Spiel mit dem **Kit-Editor** (Admin-Menü → Kits), in der **MRadmin-Weboberfläche** (Duelle → Kits) oder lesbar in `kits.yml`.
- **Kit-Editor im Web** (mit MRadmin, Permission `mrpvp.admin.kits` wie im Spiel): alle Kits mit Bildern und Tooltip wie im Spiel, Reihenfolge per ↑/↓, kopieren, neu anlegen, umbenennen, löschen. Ein Klick auf einen Platz öffnet den Item-Editor (Anzahl, Name, Beschreibung, Verzauberungen, Tränke, Farbe, Rüstungsbesatz …), dazu Name und Beschreibung auf Deutsch und Englisch, Symbol, Permission und alle Regeln. Gespeichert wird in `kits.yml`; neue Runden bekommen das Kit sofort, laufende behalten ihres.
- Kit-Regeln: kein Schaden bzw. nur Rückstoß (Sumo, Boxing), Sieg nach Treffern, feste Regeneration oder Hunger, festes Zeitlimit, mehr oder weniger Herzen, Effekte für die ganze Runde (z.B. Schnelligkeit II), eigene Cooldowns für Items (z.B. 15 Sekunden für Enderperlen - das Spiel zeigt die Wartezeit am Item), nur bestimmte Arenen, braucht eine Bau-Arena. Einstellbar im Kit-Editor über den Knopf **Regeln**, im Web oder in `kits.yml`. Was ein Kit fest vorgibt, ist beim Herausfordern ausgegraut.
- Ein Kit kann eine eigene Permission haben (`permission` in `kits.yml`), z.B. für VIP-Kits.
- **Kits weitergeben (Export/Import):** In der Kit-Liste schreibt **Alle Kits exportieren** eine Datei nach `plugins/MRpvp/exports/`. Auf dem anderen Server die Datei nach `plugins/MRpvp/imports/` legen und **Kits importieren** wählen: Vorher siehst du pro Kit, ob es neu ist oder schon existiert (dann als `<id>_2` anlegen oder ersetzen), welche Arenen es dort nicht gibt (werden weggelassen) und ob Items nicht lesbar sind. Im Web (Duelle → Kits) geht beides genauso, dort lassen sich fehlende Arenen auch einer eigenen zuordnen. Die Datei enthält jedes Item komplett - Items aus einer neueren Minecraft-Version kann ein älterer Server eventuell nicht lesen.
- **Name und Beschreibung pro Sprache:** In `kits.yml` entweder ein Text für alle Sprachen oder `name: { de: "<white>Klassisch", en: "<white>Classic" }` - jeder Spieler sieht seine Sprache, fehlt sie, gilt Englisch. Die Standard-Kits sind deutsch und englisch. Im Kit-Editor änderst du Name und Beschreibung in deiner eigenen Sprache, die anderen bleiben.

### Arenen
Mitgeliefert: **Standard**, **Sumo** (Plattform über dem Void), **Bau-Arena** (Bauen erlaubt) und **XXL-Himmel** (schwebende Inseln, nur für Kits mit Elytra).
- Neue Arenen baust du im Spiel im **Arena-Editor** (Admin-Menü → Arenen → Neue Arena; Kreativmodus, Werkzeuge in der Hotbar, dein Inventar wird solange sicher gespeichert).
- **Entwürfe „im Bau“:** Neue Arenen und Änderungen an bestehenden sind erst ein Entwurf. Mehrere aus dem Team können gleichzeitig daran bauen (in der Arenen-Liste Klick auf den Entwurf bzw. im Arena-Menü **Mitbauen**). Ein Entwurf ist nicht spielbar und erscheint nicht im Web - bei bestehenden Arenen bleibt bis dahin die alte Version spielbar. Im Editor (grüner Farbstoff, Slot 9): **Veröffentlichen** (wird die neue Version), **Verlassen** (der Entwurf bleibt im Bau, auch nach einem Neustart) oder **Entwurf verwerfen** (auch in der Liste mit Shift+Rechtsklick).
- Pro Arena einstellbar: erlaubte Kits, „nur Kits mit Elytra“, Bauen erlaubt und Bauhöhe, wie viele Kopien bereitstehen.
- Jede Arena liegt als fertige Kopie bereit, damit eine Runde sofort startet. Nach der Runde wird sie automatisch zurückgesetzt.
- Gelöschte Standard-Kits und -Arenen holt **Standard wiederherstellen** zurück.

### Auffällige Runden prüfen
Auffällige Runden zählen nicht einfach nicht - sie **warten auf eine Entscheidung**. Auffällig sind z.B. zu viele Runden gegen denselben Gegner, sehr kurze Runden ohne Treffer oder extreme Treffer. Unter **Admin-Menü → Prüfen** wertest du sie (die Statistik beider wird neu berechnet) oder verwirfst sie. Ohne Entscheidung zählt eine Runde nach 7 Tagen (einstellbar unter `review`).

---

## Befehle
| Befehl | Was er tut | Permission |
|---|---|---|
| `/duel` (`/pvp`, `/duell`) | Duell-Menü: herausfordern, Einladungen, Statistik, Bestenliste, letzte Runden | `mrpvp.use` |
| `/duel <spieler>` | Direkt eine Einladung an diesen Spieler | `mrpvp.use` |
| `/duel leave` | Aufgeben (Niederlage im Match) | `mrpvp.use` |
| `/duel admin` | Admin-Menü | `mrpvp.admin.gui` |
| `/mrpvp version` | Version anzeigen | `mrpvp.version` (in `mrpvp.player`) |
| `/mrpvp reload` | Config und Texte neu laden | `mrpvp.admin.reload` |
| `/mrpvp debug …` | Sichern und Zurückgeben testen, gesicherte Zustände ansehen | `mrpvp.admin.debug` |

## Permissions
| Permission | Standard | Wofür |
|---|---|---|
| **Gruppen** | | in allen MRtechnology-Plugins gleich aufgebaut, jede enthält die darunter |
| `mrpvp.player` | OP | Duelle: herausfordern, annehmen, eigene Statistik |
| `mrpvp.groups.support` | OP | Spieler + Admin-Menü, alle Runden ansehen, auffällige Runden prüfen und werten, zusehen, Duelle abbrechen oder Sieger bestimmen, pausieren, Spieler-Verwaltung |
| `mrpvp.groups.staff` | OP | Support + Kits und Arenen bearbeiten, Spieler sperren |
| `mrpvp.groups.admin` | OP | Alles: Staff + Statistik zurücksetzen, gesicherte Stände zurückgeben, Standard wiederherstellen, Reload, Debug, Update-Hinweis, keine Einladungs-Wartezeit |
| **Einzeln** | | |
| `mrpvp.use` | OP | Duelle spielen |
| `mrpvp.bypass.cooldown` | OP | Keine Wartezeit zwischen Einladungen |
| `mrpvp.bypass.commands` | niemand | Alle Befehle auch im Duell (sonst nur `duel.allowed-commands`) - in keiner Gruppe |
| `mrpvp.admin.gui` | OP | Admin-Menü öffnen (Aktionen darin brauchen ihr eigenes Recht) |
| `mrpvp.admin.arena` | OP | Arenen bauen, bearbeiten, aktivieren, löschen |
| `mrpvp.admin.kits` | OP | Kit-Editor |
| `mrpvp.admin.games` | OP | Alle Runden mit Kampf-Log und gesicherten Inventaren |
| `mrpvp.admin.restore` | OP | Offene Rückgabe sofort ausführen |
| `mrpvp.admin.spectate` · `stop` | OP | Duellen zusehen · Duelle abbrechen oder den Sieger bestimmen |
| `mrpvp.admin.pause` | OP | Duelle pausieren und fortsetzen |
| `mrpvp.admin.players` · `ban` · `reset` | OP | Spieler-Verwaltung · sperren · Statistik zurücksetzen |
| `mrpvp.admin.review` | OP | Auffällige Runden prüfen (bekommt auch die Hinweise), Runden nachträglich aus der Wertung nehmen oder wieder werten |
| `mrpvp.admin.defaults` | OP | Standard-Kits und -Arenen wiederherstellen |
| `mrpvp.admin.reload` · `debug` · `update` | OP | `/mrpvp reload` · `/mrpvp debug` · Update-Hinweis beim Betreten |

## Konfiguration
Alles steht kommentiert in `plugins/MRpvp/config.yml`. Änderungen übernimmst du mit `/mrpvp reload`.

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `language` | `auto` | `auto` = wie im Minecraft-Client des Spielers, sonst `de` oder `en` für alle |
| `fallback-language` | `en` | Für Spieler, deren Minecraft-Sprache keine eigene Datei hat |
| `server-language` | `en` | Texte ohne einzelnen Empfänger: Discord-Meldungen zu auffälligen Runden (über MRadmin) |
| `duel.options.wins` · `.minutes` | `[1,2,3,5]` · `[3,5,10,15]` | Auswahl im Einladungsmenü |
| `duel.reconnect-seconds` | `20` | Wartezeit bei Verbindungsabbruch, `0` = sofort verloren |
| `duel.allowed-commands` | `msg`, `r` … | Befehle, die im Duell erlaubt sind |
| `arenas.pool-per-template` | `1` | Bereite Kopien pro Arena |
| `arena-world.hide-on-squaremap` | `true` | Arena-Welt auf der Webkarte squaremap ausblenden (trägt sie dort einmalig als `map.enabled: false` ein) |
| `stats.max-counted-per-opponent-per-day` | `50` | Mehr Runden gegen denselben Gegner pro Tag warten auf eine Prüfung |
| `monitor.farming.*` | 8 Runden / 60 min, 90 %, 20 % | Statistik-Farmen: so viele Runden desselben Paars in dieser Zeit, **und** derselbe gewinnt mindestens 90 %, **und** der andere macht höchstens 20 % des Schadens. Ausgeglichene Runden unter Freunden sind nie verdächtig. |
| `review.auto-days` | `7` | Nach so vielen Tagen zählt eine ungeprüfte Runde automatisch |
| `protection.restore-statistics` | `true` | Minecraft-Statistik nach der Runde auf den Stand davor |
| `history.days` · `.snapshot-days` | `30` · `90` | Wie lange Kampf-Logs und gesicherte Inventare bleiben |
| `gui.ban-durations` | `1d`, `7d`, `permanent` | Schnellauswahl für Duell-Sperren auf der Spielerseite der MRadmin-Weboberfläche |
| `update-checker.*` | an, `github`, alle 6 h | Hinweis auf neue Versionen (Quelle, Vorabversionen, Prüfintervall) |

Alle Texte stehen in `plugins/MRpvp/lang/<sprache>.yml` ([MiniMessage](https://docs.advntr.dev/minimessage/format.html)), die Kits in `kits.yml`, die Arenen unter `templates/`, Runden und Statistik in der Datenbank `mrpvp.db`.

---

## Zusammenarbeit mit anderen Plugins
MRpvp läuft **allein**. Zusammen mit anderen MRtechnology-Plugins kann es mehr - die Plugins sprechen dafür über **MRadmin** miteinander:

| Funktion | Braucht |
|---|---|
| Seite **Duelle** in der Weboberfläche: live mit Herzen und Mini-Karte, alle Runden mit Inventaren, Kampf-Log und Wiedergabe, Prüfen | MRadmin |
| Kachel **Duelle** auf der Spielerseite der Weboberfläche: Statistik, laufende und letzte Runden, Sperre | MRadmin |
| Eigene Duell-Statistik, letzte Runden und Duell-Ranglisten für Spieler im Browser (`/web`) | MRadmin |
| **Kit-Editor im Web** mit Item-Bildern und Item-Editor | MRadmin (ab der Version mit Item-Editor) |
| Knopf im Dashboard, Spieler-Menü, wartende Prüfungen im Serverzustand, auffällige Runden im Discord-Log | MRadmin |
| Im Admin-Modus kann man kein Duell starten | MRadmin |
| Die Werkzeugleiste pausiert im Duell, die Gürtel werden mitgesichert | MRadmin + MRtoolbar |
| Die Arena-Welt erscheint nicht auf der Webkarte | squaremap (wird automatisch eingetragen) |
| Spieler im Duell zählen nicht zur Schlafquote | MRadmin + MRsleep |
| In der Arena-Welt lassen sich keine Chunkloader setzen | MRadmin + MRchunks |

## Häufige Fragen
**Kann beim Duell etwas aus dem Inventar verloren gehen?**
Nein. Alles wird in der Datenbank gesichert, **bevor** etwas am Inventar geändert wird - auch ein Absturz verliert nichts. Admins sehen jede Sicherung im Admin-Menü unter **Runden** und können eine offene Rückgabe sofort ausführen.

**Warum zählt eine Runde nicht in meiner Statistik?**
Sie wurde als auffällig markiert - z.B. zu viele Runden gegen denselben Gegner oder eine sehr kurze Runde ohne Treffer - und wartet auf eine Prüfung durch das Team. Spätestens nach 7 Tagen wird sie automatisch entschieden.

**Kann ich eigene Kits nur für VIPs anbieten?**
Ja: Gib dem Kit in `kits.yml` eine `permission` und vergib sie an die VIP-Gruppe.

**Mein neues Kit steht im Admin-Menü, aber nicht in `/duel`.**
Ein Kit ist nur wählbar, wenn mindestens eine eingeschaltete Arena dazu passt. Häufige Gründe: Die Arena ist auf „nur Elytra“ gestellt, das Kit hat aber keine Elytra; die Arena lässt nur bestimmte Kits zu; das Kit braucht „Bauen“, die Arena erlaubt es nicht. Die Kit-Liste im Admin-Menü zeigt solche Kits rot an, der Kit-Editor im Web nennt bei jeder Arena den Grund.

**Ich habe einen Fehler gefunden oder einen Wunsch.**
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mrpvp/)** - am besten mit der Spiel-ID der betroffenen Runde und der Ausgabe von `/mrpvp version`.

---

## Lizenz
MRpvp steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/`. Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md), Konzept und Stand: [docs/PLAN.md](docs/PLAN.md).
