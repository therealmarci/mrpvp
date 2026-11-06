# MRpvp

**Duelle ohne Risiko:** Zwei Spieler kämpfen in einer eigenen Arena mit einem vorgegebenen Kit - und bekommen danach **alles genau so zurück**, wie es vorher war. Mit eigener Statistik, Bestenliste und Schutz gegen Statistik-Farmen.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mrpvp/)** · **[📖 Wiki](https://mrtechnology.marcelr.at/wiki/mrpvp/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mrpvp/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - MRadmin optional |
| Sprachen | Deutsch, Englisch - automatisch wie im Minecraft-Client |
| Status | Vorabversion (0.x) - Config, Menüs und Permissions können sich bis 1.0.0 noch ändern |

## Was kann MRpvp?
- **Herausfordern per Menü:** Spieler, Kit, Arena und Regeln wählen, der Gegner nimmt per Klick an.
- **Nichts geht verloren:** Inventar, XP, Effekte und Position werden vorher sicher gespeichert und danach zurückgegeben - auch nach einem Absturz.
- **14 fertige Kits** und **4 Arenen**, eigene baust du im Spiel oder im Browser.
- **Eigene Statistik** mit Bestenliste und Kampf-Log, auffällige Runden prüft das Team.
- **Alles über Menüs** - für Spieler und für Admins, mit MRadmin auch im Browser.

## Schnellstart
1. **[MRpvp herunterladen](https://mrtechnology.marcelr.at/download/mrpvp/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten - Arena-Welt, Standard-Arenen und -Kits entstehen von selbst.
3. Rechte geben:
   ```text
   /lp group default permission set mrpvp.player true
   /lp group supporter permission set mrpvp.groups.support true
   /lp group staff permission set mrpvp.groups.staff true
   /lp group admin permission set mrpvp.groups.admin true
   ```
4. Fertig - Spieler legen mit **`/duel`** los.

**Update:** alte JAR durch die neue ersetzen, Server neu starten - neue Einstellungen und Standard-Kits kommen automatisch dazu.

## Ganze Anleitung im Wiki
| Für Spieler | Für Admins |
|---|---|
| [So läuft ein Duell](https://mrtechnology.marcelr.at/wiki/mrpvp/duell/) | [Admin-Menü](https://mrtechnology.marcelr.at/wiki/mrpvp/admin-menue/) · [Kits](https://mrtechnology.marcelr.at/wiki/mrpvp/kits/) · [Arenen](https://mrtechnology.marcelr.at/wiki/mrpvp/arenen/) · [Prüfen](https://mrtechnology.marcelr.at/wiki/mrpvp/pruefen/) |
| [Statistik & Bestenliste](https://mrtechnology.marcelr.at/wiki/mrpvp/statistik/) | [Befehle](https://mrtechnology.marcelr.at/wiki/mrpvp/befehle/) · [Permissions](https://mrtechnology.marcelr.at/wiki/mrpvp/permissions/) · [Konfiguration](https://mrtechnology.marcelr.at/wiki/mrpvp/konfiguration/) · [Häufige Fragen](https://mrtechnology.marcelr.at/wiki/mrpvp/faq/) |

Die Seiten liegen als Markdown in [`docs/wiki/`](docs/wiki/) (Seitenbaum: [`docs/wiki/index.yml`](docs/wiki/index.yml)).

## Lizenz
MRpvp steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/`. Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md), Konzept und Stand: [docs/PLAN.md](docs/PLAN.md).
