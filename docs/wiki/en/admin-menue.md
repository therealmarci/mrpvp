# Admin menu

`/duel admin` opens the admin menu (`mrpvp.admin.gui`). If you lack a permission, a gray lock is shown instead of the button.

## Live
Control running duels - a click on a duel opens its controls:
- **Spectate** above the arena or directly **at player 1 / player 2** (as a spectator, your inventory is saved)
- **Pause / resume:** both are frozen, nobody takes damage, the time stands still
- **Decide the winner:** e.g. when someone exploits a bug - the duel ends, the chosen player wins the match, the round counts as a win (without kill and death)
- **Cancel:** does not count, both get everything back

## Rounds
All rounds with combat log and the saved inventories (before and after the round). Pending returns can be carried out right here. A scored round can later be **taken out of scoring** (with an optional reason) and **scored again** later - the statistics of both players are recalculated each time.

## More sections
- **Review:** score or discard suspicious rounds - see [Reviewing suspicious rounds](pruefen.md)
- Manage **players**, ban them from duels, reset statistics
- Edit **kits** and **arenas**, **restore defaults** - see [Kits](kits.md) and [Arenas](arenen.md)

> [!TIP]
> With MRadmin this is also available in the browser: page **Duels** with a live view (hearts, mini map), all rounds with replay, review and the kit editor.
