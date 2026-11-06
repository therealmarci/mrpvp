# Schnellstart

1. **[MRpvp herunterladen](https://mrtechnology.marcelr.at/download/mrpvp/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. MRpvp legt die Arena-Welt `mrpvp_arenas` an und baut die Standard-Arenen und -Kits auf. Den Datenbank-Treiber lädt Paper beim ersten Start selbst herunter.
3. **Rechte geben** - ohne sie dürfen nur OPs Duelle spielen:
   ```text
   /lp group default permission set mrpvp.player true
   /lp group supporter permission set mrpvp.groups.support true
   /lp group staff permission set mrpvp.groups.staff true
   /lp group admin permission set mrpvp.groups.admin true
   ```
4. Fertig - Spieler legen mit **`/duel`** los.

## Update
Die alte JAR durch die neue ersetzen und den Server neu starten. Neue Einstellungen und neue Standard-Kits kommen automatisch dazu. Mehr unter [Installieren & aktualisieren](https://mrtechnology.marcelr.at/wiki/allgemein/installieren/).

> [!NOTE]
> MRpvp ist eine **Vorabversion** (0.x): Config, Menüs und Permissions können sich bis 1.0.0 noch ändern - lies vor einem Update den [Changelog](../../CHANGELOG.md).
