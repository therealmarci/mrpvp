# Working with other plugins

MRpvp runs **on its own**. Together with other MRtechnology plugins it can do more - the plugins talk to each other through **MRadmin** ([how that works](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/)).

| Feature | Needs |
|---|---|
| Page **Duels** in the web interface: live with hearts and mini map, all rounds with inventories, combat log and replay, review | MRadmin |
| Tile **Duels** on the player page of the web interface: statistics, running and last rounds, ban | MRadmin |
| Own duel statistics, last rounds and duel rankings for players in the browser (`/web`) | MRadmin |
| **Kit editor in the web** with item pictures and item editor | MRadmin |
| Configure MRpvp in the browser (Plugins → MRpvp → Configuration) | MRadmin |
| Button in the dashboard, player menu, waiting reviews in the server status | MRadmin |
| Events in the **log** of the MRadmin web interface, Discord log via MRadmin's webhook (`discord.use-mradmin-webhook`) | MRadmin |
| Nobody can start a duel in admin mode | MRadmin |
| No new duels before a restart, running duels end after the current round (`duel.restart.*`), restart announcement in a duel without a title | MRadmin (module API 14 or newer) |
| The tool bar pauses during a duel, the belts are saved along | MRadmin + MRtoolbar |
| The arena world does not appear on the map of the MRadmin player page | MRadmin (hides it automatically) |
| Players in a duel are not counted for the sleep quota | MRadmin + MRsleep |
| No chunk loaders can be placed in the arena world | MRadmin + MRchunks |
| **Team notice** "Duel round waits for review" in the inbox and as push; the message in the game follows Account → Notifications and `/mradmin mute` | MRadmin |

## Several worlds (Multiverse)
Duels can be started from any world, afterwards you go back to the same place. If the world is missing, the player lands at the spawn of the main world.

- **Multiverse-Core:** MRpvp manages the arena world itself. Do not import it with `/mv import`, otherwise Multiverse enforces its game mode there.
- **Multiverse-Inventories:** when moving into the arena world and back, the inventory stays with MRpvp, Multiverse-Inventories does not touch it then. Nothing to set up.
