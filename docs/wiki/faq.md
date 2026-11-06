# Häufige Fragen

### Kann beim Duell etwas aus dem Inventar verloren gehen?
Nein. Alles wird in der Datenbank gesichert, **bevor** etwas am Inventar geändert wird - auch ein Absturz verliert nichts. Admins sehen jede Sicherung im Admin-Menü unter **Runden** und können eine offene Rückgabe sofort ausführen.

### Warum zählt eine Runde nicht in meiner Statistik?
Sie wurde als auffällig markiert - z.B. zu viele Runden gegen denselben Gegner oder eine sehr kurze Runde ohne Treffer - und wartet auf eine Prüfung durch das Team. Spätestens nach 7 Tagen wird sie automatisch entschieden. Mehr unter [Auffällige Runden prüfen](pruefen.md).

### Kann ich eigene Kits nur für VIPs anbieten?
Ja: Gib dem Kit in `kits.yml` eine `permission` und vergib sie an die VIP-Gruppe - siehe [Kits](kits.md#permission-pro-kit).

### Mein neues Kit steht im Admin-Menü, aber nicht in `/duel`.
Ein Kit ist nur wählbar, wenn mindestens eine eingeschaltete Arena dazu passt. Häufige Gründe: Die Arena ist auf „nur Elytra“ gestellt, das Kit hat aber keine Elytra; die Arena lässt nur bestimmte Kits zu; das Kit braucht „Bauen“, die Arena erlaubt es nicht. Die Kit-Liste im Admin-Menü zeigt solche Kits rot an, der Kit-Editor im Web nennt bei jeder Arena den Grund.

### Ich habe einen Fehler gefunden oder einen Wunsch.
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mrpvp/)** - am besten mit der Spiel-ID der betroffenen Runde und der Ausgabe von `/mrpvp version`.
