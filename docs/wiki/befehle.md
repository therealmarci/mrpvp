# Befehle

Im Spiel läuft alles über Menüs - die Befehle öffnen sie nur.

| Befehl | Was er tut | Permission |
|---|---|---|
| `/duel` (`/pvp`, `/duell`) | Duell-Menü: herausfordern, Einladungen, Statistik, Bestenliste, letzte Runden | `mrpvp.use` |
| `/duel <spieler>` | Direkt eine Einladung an diesen Spieler | `mrpvp.use` |
| `/duel leave` | Aufgeben (Niederlage im Match) | `mrpvp.use` |
| `/duel admin` | [Admin-Menü](admin-menue.md) | `mrpvp.admin.gui` |
| `/duel kit done` · `icon` · `cancel` | Kit-Editor im Spiel: speichern · Symbol setzen · abbrechen | `mrpvp.admin.kits` |
| `/mrpvp version` | Version anzeigen | `mrpvp.version` (in `mrpvp.player`) |
| `/mrpvp reload` | Config und Texte neu laden | `mrpvp.admin.reload` |
| `/mrpvp discord [test]` | Status des Discord-Logs · Testnachricht senden | `mrpvp.admin.discord` |
| `/mrpvp debug …` | Sichern und Zurückgeben testen, gesicherte Zustände ansehen | `mrpvp.admin.debug` |
