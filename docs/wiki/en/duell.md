# How a duel works

1. **`/duel`** → **Challenge** → click a player. Or directly: `/duel <player>`.
2. Choose **kit** and **map**, plus the options:
   - **Wins to win** (1, 2, 3 or 5 rounds as a match)
   - **Time limit per round**
   - **Hunger** and **natural regeneration** on or off (some kits set this fixed, e.g. UHC without regeneration - then the option is grayed out)
3. **Send the invitation.** The opponent gets **[Accept] [Decline] [Details]** in chat. Your last settings are preselected next time.
4. During the duel the **boss bar** shows the round, the score, the opponent's hearts and the remaining time. At the end there is a summary in chat - and everything back.

`/duel` is also called `/pvp` or `/duell`.

## During the duel
- **Giving up** with `/duel leave` counts as a loss of the whole match (only before round 1 it is simply cancelled).
- **Connection lost?** The round pauses for 20 seconds, the opponent is frozen. Whoever comes back in time plays on (once per match).
- Only few commands work in a duel (e.g. `/msg`) - the server decides which.
- The team can pause or cancel a duel or decide the winner in case of a bug.
- **Shortly before a restart** of the server (10 minutes before) no new duels start. If your duel has more rounds, the current round is then the last one: whoever won more rounds wins, otherwise it is a draw. In a duel you see the restart announcement only as a line above the hotbar.

## Nothing gets lost
Inventory, armor, ender chest, XP, health, hunger, effects, game mode and position are saved safely **before** the duel and given back afterwards - even after a server crash. Whoever is offline when things are given back gets everything on the next login.

Duels do not count for Minecraft: no deaths and kills in the vanilla statistics, no death message, no drops, no advancements.

## The kits
Included are **Classic, Diamond, Netherite, Bow, Mace, Elytra & Mace, UHC, Build-UHC, Sumo, Boxing, NoDebuff, Crystal, Axe & Shield** and **Trident** - depending on the server also its own. Some kits have fixed rules (e.g. Sumo: knockback only, no damage) and only run in suitable arenas.
