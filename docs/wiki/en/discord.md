# Discord log

MRpvp reports to a Discord channel:
- **Rounds:** duel ended (`duel-ended`, off at first - comes with every duel), suspicious round (`alert`), round waits for review (`review-waiting`), review decided (`review-counted` / `review-discarded`).
- **Team actions:** round cancelled or ended with a winner, paused and resumed, later taken out of scoring or scored again.
- **Kits and arenas:** kit saved, deleted, imported; arena published, deleted, draft discarded.
- **Players and management:** duel ban and unban, statistics reset, inventory given back, defaults restored, reload.

## Setup
1. In Discord: **Edit channel → Integrations → Webhooks → New webhook** and **copy the webhook URL**.
2. In the config set `discord.enabled: true` and enter the URL at `webhook-url`.
3. `/mrpvp reload`, then check with **`/mrpvp discord test`**.

Under `discord.events` you set per event whether it is sent and in which color. The texts are in the language file under `discord` (in `general.server-language`).

> [!TIP]
> **No own webhook?** With MRadmin it also works through its Discord log: `discord.enabled: true`, leave `webhook-url` empty and set `discord.use-mradmin-webhook: true`. Whoever had `monitor.discord` on before gets this automatically with the update.
>
> With MRadmin every event is also in the **log** of the web interface (up to 14 days) - even when Discord is off.
