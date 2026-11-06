# Commands

In game everything goes through menus - the commands only open them.

| Command | What it does | Permission |
|---|---|---|
| `/duel` (`/pvp`, `/duell`) | Duel menu: challenge, invitations, statistics, leaderboard, last rounds | `mrpvp.use` |
| `/duel <player>` | Send an invitation to this player right away | `mrpvp.use` |
| `/duel leave` | Give up (loss of the match) | `mrpvp.use` |
| `/duel admin` | [Admin menu](admin-menue.md) | `mrpvp.admin.gui` |
| `/duel kit done` · `icon` · `cancel` | Kit editor in game: save · set icon · cancel | `mrpvp.admin.kits` |
| `/mrpvp version` | Show the version | `mrpvp.version` (in `mrpvp.player`) |
| `/mrpvp reload` | Reload config and messages | `mrpvp.admin.reload` |
| `/mrpvp discord [test]` | Status of the Discord log · send a test message | `mrpvp.admin.discord` |
| `/mrpvp debug …` | Test saving and returning, view saved states | `mrpvp.admin.debug` |
