# Kits

Mitgeliefert: **Klassisch, Diamant, Netherite, Bogen, Streitkolben, Elytra & Streitkolben, UHC, Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axt & Schild** und **Dreizack**. Bearbeiten im Spiel mit dem **Kit-Editor** (Admin-Menü → Kits), in der **MRadmin-Weboberfläche** (Duelle → Kits) oder lesbar in `kits.yml`. Alle Wege brauchen `mrpvp.admin.kits`.

## Kit-Editor im Spiel
Im Admin-Menü → Kits ein Kit wählen: Dein eigenes Inventar wird sicher weggespeichert (auch im Admin-Modus), dann bearbeitest du das Kit direkt in deinem Inventar. Speichern, Einstellungen und Abbrechen gehen über die Knöpfe im Chat bzw. `/duel` (`/duel kit done`, `/duel kit icon`, `/duel kit cancel`). Droppen und Kisten sind dabei gesperrt.

## Kit-Editor im Web
Mit MRadmin: alle Kits mit Bildern und Tooltip wie im Spiel, Reihenfolge per ↑/↓, kopieren, neu anlegen, umbenennen, löschen. Ein Klick auf einen Platz öffnet den Item-Editor (Anzahl, Name, Beschreibung, Verzauberungen, Tränke, Farbe, Rüstungsbesatz …), dazu Name und Beschreibung auf Deutsch und Englisch, Symbol, Permission und alle Regeln. Gespeichert wird in `kits.yml`; neue Runden bekommen das Kit sofort, laufende behalten ihres.

## Regeln
- kein Schaden bzw. nur Rückstoß (Sumo, Boxing), Sieg nach Treffern
- feste Regeneration oder Hunger, festes Zeitlimit
- mehr oder weniger Herzen, Effekte für die ganze Runde (z.B. Schnelligkeit II)
- eigene Cooldowns für Items (z.B. 15 Sekunden für Enderperlen - das Spiel zeigt die Wartezeit am Item)
- nur bestimmte Arenen, braucht eine Bau-Arena

Einstellbar im Kit-Editor über den Knopf **Regeln**, im Web oder in `kits.yml`. Was ein Kit fest vorgibt, ist beim Herausfordern ausgegraut.

> [!WARNING]
> Ein Kit ist in `/duel` nur wählbar, wenn mindestens eine eingeschaltete Arena dazu passt (z.B. Arena „nur Elytra“, aber das Kit hat keine Elytra). Die Kit-Liste im Admin-Menü zeigt solche Kits rot, der Kit-Editor im Web nennt bei jeder Arena den Grund.

## Permission pro Kit
Ein Kit kann eine eigene Permission haben (`permission` in `kits.yml`), z.B. für VIP-Kits - nur wer sie hat, kann es wählen.

## Name und Beschreibung pro Sprache
In `kits.yml` entweder ein Text für alle Sprachen oder `name: { de: "<white>Klassisch", en: "<white>Classic" }` - jeder Spieler sieht seine Sprache, fehlt sie, gilt Englisch. Die Standard-Kits sind deutsch und englisch. Im Kit-Editor änderst du Name und Beschreibung in deiner eigenen Sprache, die anderen bleiben.

## Kits weitergeben (Export/Import)
- **Exportieren:** In der Kit-Liste schreibt **Alle Kits exportieren** eine Datei nach `plugins/MRpvp/exports/`.
- **Importieren:** Auf dem anderen Server die Datei nach `plugins/MRpvp/imports/` legen und **Kits importieren** wählen. Vorher siehst du pro Kit, ob es neu ist oder schon existiert (dann als `<id>_2` anlegen oder ersetzen), welche Arenen es dort nicht gibt (werden weggelassen) und ob Items nicht lesbar sind.
- **Im Web** (Duelle → Kits) geht beides genauso, dort lassen sich fehlende Arenen auch einer eigenen zuordnen.
- Die Datei enthält jedes Item komplett - Items aus einer neueren Minecraft-Version kann ein älterer Server eventuell nicht lesen.

Gelöschte Standard-Kits holt **Standard wiederherstellen** im Admin-Menü zurück (`mrpvp.admin.defaults`).
