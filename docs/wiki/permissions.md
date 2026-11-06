# Permissions

Wie die Gruppen in allen Plugins aufgebaut sind: [Rechte mit LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Gruppen
Jede enthält die darunter:

| Permission | Standard | Wofür |
|---|---|---|
| `mrpvp.player` | OP | Duelle: herausfordern, annehmen, eigene Statistik, `/mrpvp version` |
| `mrpvp.groups.support` | OP | Spieler + Admin-Menü, alle Runden ansehen, auffällige Runden prüfen und werten, zusehen, Duelle abbrechen oder Sieger bestimmen, pausieren, Spieler-Verwaltung |
| `mrpvp.groups.staff` | OP | Support + Kits und Arenen bearbeiten, Spieler sperren |
| `mrpvp.groups.admin` | OP | Alles: Staff + Statistik zurücksetzen, gesicherte Stände zurückgeben, Standard wiederherstellen, Reload, Discord, Debug, Update-Hinweis, keine Einladungs-Wartezeit |

```text
/lp group default permission set mrpvp.player true
/lp group supporter permission set mrpvp.groups.support true
/lp group staff permission set mrpvp.groups.staff true
/lp group admin permission set mrpvp.groups.admin true
```

## Einzeln
| Permission | Standard | Wofür |
|---|---|---|
| `mrpvp.use` | OP | Duelle spielen |
| `mrpvp.version` | OP | `/mrpvp version` |
| `mrpvp.bypass.cooldown` | OP | Keine Wartezeit zwischen Einladungen |
| `mrpvp.bypass.commands` | niemand | Alle Befehle auch im Duell (sonst nur `duel.allowed-commands`) - in keiner Gruppe |
| `mrpvp.admin.gui` | OP | Admin-Menü öffnen (Aktionen darin brauchen ihr eigenes Recht) |
| `mrpvp.admin.arena` | OP | Arenen bauen, bearbeiten, aktivieren, löschen |
| `mrpvp.admin.kits` | OP | Kit-Editor (im Spiel und im Web) |
| `mrpvp.admin.games` | OP | Alle Runden mit Kampf-Log und gesicherten Inventaren |
| `mrpvp.admin.restore` | OP | Offene Rückgabe sofort ausführen |
| `mrpvp.admin.spectate` · `stop` | OP | Duellen zusehen · Duelle abbrechen oder den Sieger bestimmen |
| `mrpvp.admin.pause` | OP | Duelle pausieren und fortsetzen |
| `mrpvp.admin.players` · `ban` · `reset` | OP | Spieler-Verwaltung · sperren · Statistik zurücksetzen |
| `mrpvp.admin.review` | OP | Auffällige Runden prüfen (bekommt auch die Hinweise), Runden nachträglich aus der Wertung nehmen oder wieder werten |
| `mrpvp.admin.defaults` | OP | Standard-Kits und -Arenen wiederherstellen |
| `mrpvp.admin.reload` · `debug` · `update` | OP | `/mrpvp reload` · `/mrpvp debug` · Update-Hinweis beim Betreten |
| `mrpvp.admin.discord` | OP | `/mrpvp discord [test]` |

Eigene Kits können zusätzlich eine eigene Permission verlangen - siehe [Kits](kits.md#permission-pro-kit).
