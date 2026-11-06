# Konfiguration

Am einfachsten im Browser: Mit **MRadmin** unter **Plugins → MRpvp → Konfiguration** - jede Einstellung mit Erklärung, Speichern lädt MRpvp sofort neu (Permission `mradmin.config`). Alles steht außerdem kommentiert in `plugins/MRpvp/config.yml`; Änderungen von Hand übernimmst du mit `/mrpvp reload`.

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `general.language` | `auto` | `auto` = wie im Minecraft-Client des Spielers, sonst `de` oder `en` für alle |
| `general.fallback-language` | `en` | Für Spieler, deren Minecraft-Sprache keine eigene Datei hat |
| `general.server-language` | `en` | Texte ohne einzelnen Empfänger: das Discord-Log und das Protokoll von MRadmin |
| `duel.options.wins` · `.minutes` | `[1,2,3,5]` · `[3,5,10,15]` | Auswahl im Einladungsmenü |
| `duel.reconnect-seconds` | `20` | Wartezeit bei Verbindungsabbruch, `0` = sofort verloren |
| `duel.allowed-commands` | `msg`, `r` … | Befehle, die im Duell erlaubt sind |
| `arena-world.name` · `.difficulty` · `.time` | `mrpvp_arenas` · `normal` · `6000` | Eigene Welt der Arenen: Name, Schwierigkeit, feste Uhrzeit |
| `arenas.pool-per-template` | `1` | Bereite Kopien pro Arena |
| `stats.max-counted-per-opponent-per-day` | `50` | Mehr Runden gegen denselben Gegner pro Tag warten auf eine Prüfung |
| `monitor.farming.*` | 8 Runden / 60 min, 90 %, 20 % | Statistik-Farmen erkennen - siehe [Auffällige Runden prüfen](pruefen.md) |
| `review.auto-days` | `7` | Nach so vielen Tagen zählt eine ungeprüfte Runde automatisch |
| `protection.restore-statistics` | `true` | Minecraft-Statistik nach der Runde auf den Stand davor |
| `history.days` · `.snapshot-days` | `30` · `90` | Wie lange Kampf-Logs und gesicherte Inventare bleiben |
| `gui.ban-durations` | `1d`, `7d`, `permanent` | Schnellauswahl für Duell-Sperren auf der Spielerseite der MRadmin-Weboberfläche |
| `discord.*` | aus | Discord-Log - siehe [Discord-Log](discord.md) |
| `updates.*` | an, `github`, alle 6 h | Hinweis auf neue Versionen (Quelle, Vorabversionen, Prüfintervall) |

## Weitere Dateien
| Datei | Inhalt |
|---|---|
| `plugins/MRpvp/lang/<sprache>.yml` | alle Texte im [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html) - mehr unter [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `plugins/MRpvp/kits.yml` | die Kits - siehe [Kits](kits.md) |
| `plugins/MRpvp/templates/` | die Arenen - siehe [Arenen](arenen.md) |
| `plugins/MRpvp/mrpvp.db` | Runden, Statistik und gesicherte Inventare (SQLite) |
