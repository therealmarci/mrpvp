# Kits

Included: **Classic, Diamond, Netherite, Bow, Mace, Elytra & Mace, UHC, Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axe & Shield** and **Trident**. Edit them in game with the **kit editor** (admin menu → Kits), in the **MRadmin web interface** (Duels → Kits) or readable in `kits.yml`. All ways need `mrpvp.admin.kits`.

## Kit editor in game
Choose a kit in the admin menu → Kits: your own inventory is stored away safely (also in admin mode), then you edit the kit directly in your inventory. Save, settings and cancel work via the buttons in chat or `/duel` (`/duel kit done`, `/duel kit icon`, `/duel kit cancel`). Dropping items and chests are blocked meanwhile.

## Kit editor in the web
With MRadmin: all kits with pictures and tooltips like in game, order via ↑/↓, copy, create, rename, delete. A click on a slot opens the item editor (amount, name, lore, enchantments, potions, color, armor trim …), plus name and description in German and English, icon, permission and all rules. It is saved in `kits.yml`; new rounds get the kit right away, running ones keep theirs.

## Rules
- no damage or knockback only (Sumo, Boxing), win after a number of hits
- fixed regeneration or hunger, fixed time limit
- more or fewer hearts, effects for the whole round (e.g. Speed II)
- own cooldowns for items (e.g. 15 seconds for ender pearls - the game shows the waiting time on the item)
- only certain arenas, needs a build arena

Set them in the kit editor via the button **Rules**, in the web or in `kits.yml`. What a kit sets fixed is grayed out when challenging.

> [!WARNING]
> A kit can only be chosen in `/duel` if at least one enabled arena fits it (e.g. arena "elytra only", but the kit has no elytra). The kit list in the admin menu shows such kits in red, the kit editor in the web names the reason for every arena.

## Permission per kit
A kit can have its own permission (`permission` in `kits.yml`), e.g. for VIP kits - only whoever has it can choose the kit.

## Name and description per language
In `kits.yml` either one text for all languages or `name: { de: "<white>Klassisch", en: "<white>Classic" }` - every player sees their language, if it is missing English applies. The default kits are German and English. In the kit editor you change name and description in your own language, the others stay.

## Sharing kits (export/import)
- **Export:** in the kit list, **Export all kits** writes a file to `plugins/MRpvp/exports/`.
- **Import:** on the other server put the file into `plugins/MRpvp/imports/` and choose **Import kits**. Before that you see per kit whether it is new or already exists (then create it as `<id>_2` or replace it), which arenas do not exist there (they are left out) and whether items cannot be read.
- **In the web** (Duels → Kits) both work the same way, there missing arenas can also be mapped to one of your own.
- The file contains every item completely - an older server may not be able to read items from a newer Minecraft version.

**Restore defaults** in the admin menu brings back deleted default kits (`mrpvp.admin.defaults`).
