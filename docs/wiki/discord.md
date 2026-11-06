# Discord-Log

MRpvp meldet in einen Discord-Kanal:
- **Runden:** Duell beendet (`duel-ended`, anfangs aus - kommt bei jedem Duell), auffällige Runde (`alert`), Runde wartet auf Prüfung (`review-waiting`), Prüfung entschieden (`review-counted` / `review-discarded`).
- **Team-Eingriffe:** Runde abgebrochen oder mit Sieger beendet, pausiert und fortgesetzt, nachträglich aus der Wertung genommen oder wieder gewertet.
- **Kits und Arenen:** Kit gespeichert, gelöscht, importiert; Arena veröffentlicht, gelöscht, Entwurf verworfen.
- **Spieler und Verwaltung:** Duell-Sperre und Aufhebung, Statistik zurückgesetzt, Inventar zurückgegeben, Standard wiederhergestellt, Reload.

## Einrichten
1. In Discord: **Kanal bearbeiten → Integrationen → Webhooks → Neuer Webhook** und die **Webhook-URL kopieren**.
2. In der Config `discord.enabled: true` setzen und die URL bei `webhook-url` eintragen.
3. `/mrpvp reload`, dann mit **`/mrpvp discord test`** prüfen.

Unter `discord.events` legst du pro Ereignis fest, ob es gesendet wird und in welcher Farbe. Die Texte stehen in der Sprachdatei unter `discord` (in `general.server-language`).

> [!TIP]
> **Kein eigener Webhook?** Mit MRadmin geht es auch über dessen Discord-Log: `discord.enabled: true`, `webhook-url` leer lassen und `discord.use-mradmin-webhook: true` setzen. Wer bisher `monitor.discord` an hatte, bekommt das beim Update automatisch.
>
> Mit MRadmin steht jedes Ereignis außerdem im **Protokoll** der Weboberfläche (bis zu 14 Tage) - auch wenn Discord aus ist.
