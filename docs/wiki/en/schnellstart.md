# Quick start

1. **[Download MRpvp](https://mrtechnology.marcelr.at/download/mrpvp/)** and put the JAR into the `plugins/` folder.
2. Start the server. MRpvp creates the arena world `mrpvp_arenas` and builds the default arenas and kits. Paper downloads the database driver by itself on the first start.
3. **Give permissions** - without them only OPs may play duels:
   ```text
   /lp group default permission set mrpvp.player true
   /lp group supporter permission set mrpvp.groups.support true
   /lp group staff permission set mrpvp.groups.staff true
   /lp group admin permission set mrpvp.groups.admin true
   ```
4. Done - players get going with **`/duel`**.

## Update
Replace the old JAR with the new one and restart the server. New settings and new default kits are added automatically. More under [Install & update](https://mrtechnology.marcelr.at/wiki/allgemein/installieren/).

> [!NOTE]
> MRpvp is a **pre-release** (0.x): config, menus and permissions may still change until 1.0.0 - read the [changelog](/CHANGELOG.md) before an update.
