# Arenas

Included: **Standard**, **Sumo** (platform above the void), **Build arena** (building allowed) and **XXL sky** (floating islands, only for kits with elytra). All arenas are in their own world `mrpvp_arenas`.

## Build a new arena
Admin menu → Arenas → **New arena** (`mrpvp.admin.arena`): you land in the **arena editor** - creative mode, tools in the hotbar, your inventory is saved safely meanwhile.

## Drafts "under construction"
New arenas and changes to existing ones are a **draft** first:
- Several team members can build on it at the same time - in the arena list click on the draft, or **Build along** in the arena menu.
- A draft is not playable and does not appear in the web. For existing arenas the old version stays playable until then.
- In the editor (green dye, slot 9):
  - **Publish** - becomes the new version
  - **Leave** - the draft stays under construction, also after a restart
  - **Discard draft** - also in the list with shift + right click

## Settings per arena
- allowed kits
- "kits with elytra only"
- building allowed and build height
- how many copies are kept ready (default for new ones: `arenas.pool-per-template`)

Every arena is kept ready as a finished copy, so a round starts right away. After the round it is reset automatically.

**Restore defaults** brings back deleted default arenas (`mrpvp.admin.defaults`).

> [!WARNING]
> MRpvp manages the arena world itself. Do not import it with Multiverse (`/mv import`) - see [Working with other plugins](zusammenarbeit.md#several-worlds-multiverse).
