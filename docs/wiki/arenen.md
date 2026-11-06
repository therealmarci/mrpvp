# Arenen

Mitgeliefert: **Standard**, **Sumo** (Plattform über dem Void), **Bau-Arena** (Bauen erlaubt) und **XXL-Himmel** (schwebende Inseln, nur für Kits mit Elytra). Alle Arenen liegen in der eigenen Welt `mrpvp_arenas`.

## Neue Arena bauen
Admin-Menü → Arenen → **Neue Arena** (`mrpvp.admin.arena`): Du landest im **Arena-Editor** - Kreativmodus, Werkzeuge in der Hotbar, dein Inventar wird solange sicher gespeichert.

## Entwürfe „im Bau“
Neue Arenen und Änderungen an bestehenden sind erst ein **Entwurf**:
- Mehrere aus dem Team können gleichzeitig daran bauen - in der Arenen-Liste Klick auf den Entwurf bzw. im Arena-Menü **Mitbauen**.
- Ein Entwurf ist nicht spielbar und erscheint nicht im Web. Bei bestehenden Arenen bleibt bis dahin die alte Version spielbar.
- Im Editor (grüner Farbstoff, Slot 9):
  - **Veröffentlichen** - wird die neue Version
  - **Verlassen** - der Entwurf bleibt im Bau, auch nach einem Neustart
  - **Entwurf verwerfen** - auch in der Liste mit Shift+Rechtsklick

## Einstellungen pro Arena
- erlaubte Kits
- „nur Kits mit Elytra“
- Bauen erlaubt und Bauhöhe
- wie viele Kopien bereitstehen (Standard für neue: `arenas.pool-per-template`)

Jede Arena liegt als fertige Kopie bereit, damit eine Runde sofort startet. Nach der Runde wird sie automatisch zurückgesetzt.

Gelöschte Standard-Arenen holt **Standard wiederherstellen** zurück (`mrpvp.admin.defaults`).

> [!WARNING]
> Die Arena-Welt verwaltet MRpvp selbst. Importiere sie nicht mit Multiverse (`/mv import`) - siehe [Zusammenarbeit](zusammenarbeit.md#mehrere-welten-multiverse).
