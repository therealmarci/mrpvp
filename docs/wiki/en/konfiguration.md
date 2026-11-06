# Configuration

Easiest in the browser: with **MRadmin** under **Plugins → MRpvp → Configuration** - every setting with an explanation, saving reloads MRpvp right away (permission `mradmin.config`). Everything is also commented in `plugins/MRpvp/config.yml`; you apply changes by hand with `/mrpvp reload`.

| Section | What for | More |
|---|---|---|
| `general` | Language of the players (`auto` = like the Minecraft client), fallback and server language | [Languages & messages](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `arena-world` · `arenas` | Own world of the arenas, ready copies per arena | [Arenas](arenen.md) |
| `duel` · `duel-options` | Reconnecting, allowed commands, choices when challenging | [How a duel works](duell.md) |
| `stats` · `review` · `monitor` | Count limits per opponent, automatic scoring, detecting stat farming | [Reviewing suspicious rounds](pruefen.md) |
| `protection` · `history` | Protect the Minecraft statistics, how long combat logs and inventories are kept | |
| `discord` | Discord log | [Discord log](discord.md) |
| `updates` · `gui` | Notice about new versions, menus (e.g. quick-select ban durations) | |

Every single setting with its default is listed below under [All settings](#all-settings).

## More files
| File | Content |
|---|---|
| `plugins/MRpvp/lang/<language>.yml` | all messages in [MiniMessage format](https://docs.advntr.dev/minimessage/format.html) - more under [Languages & messages](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `plugins/MRpvp/kits.yml` | the kits - see [Kits](kits.md) |
| `plugins/MRpvp/templates/` | the arenas - see [Arenas](arenen.md) |
| `plugins/MRpvp/mrpvp.db` | rounds, statistics and saved inventories (SQLite) |

## All settings
Every setting of `config.yml` with its default - the same fields as in MRadmin's browser form. The list is generated automatically from the plugin and therefore always matches this version.

<!-- auto:config -->
