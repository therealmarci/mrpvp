# Konfiguration

Am einfachsten im Browser: Mit **MRadmin** unter **Plugins → MRpvp → Konfiguration** - jede Einstellung mit Erklärung, Speichern lädt MRpvp sofort neu (Permission `mradmin.config`). Alles steht außerdem kommentiert in `plugins/MRpvp/config.yml`; Änderungen von Hand übernimmst du mit `/mrpvp reload`.

| Abschnitt | Wofür | Mehr dazu |
|---|---|---|
| `general` | Sprache der Spieler (`auto` = wie der Minecraft-Client), Ersatz- und Server-Sprache | [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `arena-world` · `arenas` | Eigene Welt der Arenen, bereite Kopien pro Arena | [Arenen](arenen.md) |
| `duel` · `duel-options` | Wiederverbinden, erlaubte Befehle, Auswahl beim Herausfordern | [Duelle](duell.md) |
| `stats` · `review` · `monitor` | Zählgrenzen pro Gegner, automatische Wertung, Statistik-Farmen erkennen | [Auffällige Runden prüfen](pruefen.md) |
| `protection` · `history` | Minecraft-Statistik schützen, wie lange Kampf-Logs und Inventare bleiben | |
| `discord` | Discord-Log | [Discord-Log](discord.md) |
| `updates` · `gui` | Hinweis auf neue Versionen, Menüs (z.B. Schnellauswahl der Sperrdauern) | |

Jede einzelne Einstellung mit Standardwert steht unten unter [Alle Einstellungen](#alle-einstellungen).

## Weitere Dateien
| Datei | Inhalt |
|---|---|
| `plugins/MRpvp/lang/<sprache>.yml` | alle Texte im [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html) - mehr unter [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `plugins/MRpvp/kits.yml` | die Kits - siehe [Kits](kits.md) |
| `plugins/MRpvp/templates/` | die Arenen - siehe [Arenen](arenen.md) |
| `plugins/MRpvp/mrpvp.db` | Runden, Statistik und gesicherte Inventare (SQLite) |

## Alle Einstellungen
Jede Einstellung der `config.yml` mit Standardwert - dieselben Felder wie im Browser-Formular von MRadmin. Die Liste entsteht automatisch aus dem Plugin und ist damit immer auf dem Stand dieser Version.

<!-- auto:config -->
