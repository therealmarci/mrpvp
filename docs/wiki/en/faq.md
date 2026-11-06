# FAQ

### Can anything get lost from my inventory in a duel?
No. Everything is saved in the database **before** anything in the inventory changes - even a crash loses nothing. Admins see every backup in the admin menu under **Rounds** and can carry out a pending return right away.

### Why does a round not count in my statistics?
It was marked as suspicious - e.g. too many rounds against the same opponent or a very short round without hits - and waits for a review by the team. After 7 days at the latest it is decided automatically. More under [Reviewing suspicious rounds](pruefen.md).

### Can I offer my own kits only to VIPs?
Yes: give the kit a `permission` in `kits.yml` and give it to the VIP group - see [Kits](kits.md#permission-per-kit).

### My new kit is in the admin menu, but not in `/duel`.
A kit can only be chosen if at least one enabled arena fits it. Common reasons: the arena is set to "elytra only" but the kit has no elytra; the arena only allows certain kits; the kit needs "building" but the arena does not allow it. The kit list in the admin menu shows such kits in red, the kit editor in the web names the reason for every arena.

### I found a bug or have a wish.
Gladly via the **[form on the website](https://mrtechnology.marcelr.at/report/mrpvp/)** - ideally with the game ID of the round in question and the output of `/mrpvp version`.
