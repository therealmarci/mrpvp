# Zusammenarbeit mit anderen Plugins

MRpvp läuft **allein**. Zusammen mit anderen MRtechnology-Plugins kann es mehr - die Plugins sprechen dafür über **MRadmin** miteinander ([wie das funktioniert](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/)).

| Funktion | Braucht |
|---|---|
| Seite **Duelle** in der Weboberfläche: live mit Herzen und Mini-Karte, alle Runden mit Inventaren, Kampf-Log und Wiedergabe, Prüfen | MRadmin |
| Kachel **Duelle** auf der Spielerseite der Weboberfläche: Statistik, laufende und letzte Runden, Sperre | MRadmin |
| Eigene Duell-Statistik, letzte Runden und Duell-Ranglisten für Spieler im Browser (`/web`) | MRadmin |
| **Kit-Editor im Web** mit Item-Bildern und Item-Editor | MRadmin |
| MRpvp im Browser einstellen (Plugins → MRpvp → Konfiguration) | MRadmin |
| Knopf im Dashboard, Spieler-Menü, wartende Prüfungen im Serverzustand | MRadmin |
| Ereignisse im **Protokoll** der MRadmin-Weboberfläche, Discord-Log über MRadmins Webhook (`discord.use-mradmin-webhook`) | MRadmin |
| Im Admin-Modus kann man kein Duell starten | MRadmin |
| Vor einem Neustart keine neuen Duelle, laufende Duelle enden nach der aktuellen Runde (`duel.restart.*`), Neustart-Ankündigung im Duell ohne Titel | MRadmin (ab Modul-API 14) |
| Die Werkzeugleiste pausiert im Duell, die Gürtel werden mitgesichert | MRadmin + MRtoolbar |
| Die Arena-Welt erscheint nicht auf der Karte der MRadmin-Spielerseite | MRadmin (blendet sie automatisch aus) |
| Spieler im Duell zählen nicht zur Schlafquote | MRadmin + MRsleep |
| In der Arena-Welt lassen sich keine Chunkloader setzen | MRadmin + MRchunks |
| **Team-Meldung** „Duell-Runde wartet auf Prüfung“ im Posteingang und als Push; der Hinweis im Spiel richtet sich nach Konto → Benachrichtigungen und `/mradmin mute` | MRadmin |

## Mehrere Welten (Multiverse)
Duelle lassen sich aus jeder Welt starten, danach geht es an denselben Ort zurück. Fehlt die Welt, landet der Spieler am Spawn der Hauptwelt.

- **Multiverse-Core:** Die Arena-Welt verwaltet MRpvp selbst. Importiere sie nicht mit `/mv import`, sonst setzt Multiverse dort seinen Spielmodus durch.
- **Multiverse-Inventories:** Bei Wechseln in die Arena-Welt und zurück bleibt das Inventar bei MRpvp, Multiverse-Inventories fasst es dann nicht an. Nichts einzustellen.
