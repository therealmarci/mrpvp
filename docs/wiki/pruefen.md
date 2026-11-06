# Auffällige Runden prüfen

Auffällige Runden zählen nicht einfach nicht - sie **warten auf eine Entscheidung**. Unter **Admin-Menü → Prüfen** (`mrpvp.admin.review`) wertest du sie (die Statistik beider wird neu berechnet) oder verwirfst sie. Ohne Entscheidung zählt eine Runde nach 7 Tagen (`review.auto-days`).

## Was ist auffällig?
- **Statistik-Farmen:** dasselbe Paar spielt oft in kurzer Zeit, **und** fast immer gewinnt derselbe, **und** der andere macht kaum Schaden (`monitor.farming.*`, Standard 8 Runden in 60 Minuten, 90 % Siege, höchstens 20 % des Schadens). Ausgeglichene Runden unter Freunden sind nie verdächtig.
- **Zu viele Runden gegen denselben Gegner** an einem Tag (`stats.max-counted-per-opponent-per-day`, Standard 50).
- sehr kurze Runden ohne Treffer oder extreme Treffer.

Wer `mrpvp.admin.review` hat, bekommt einen Hinweis, wenn eine Runde auf Prüfung wartet. Mit MRadmin stehen wartende Prüfungen auch im Serverzustand und auf der Seite **Duelle** der Weboberfläche.

## Nachträglich aus der Wertung nehmen
Auch eine schon gewertete Runde lässt sich unter **Admin-Menü → Runden** aus der Wertung nehmen (mit optionalem Grund) und später wieder werten - siehe [Admin-Menü](admin-menue.md#runden).
