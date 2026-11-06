# Reviewing suspicious rounds

Suspicious rounds do not simply not count - they **wait for a decision**. Under **admin menu → Review** (`mrpvp.admin.review`) you score them (the statistics of both are recalculated) or discard them. Without a decision a round counts after 7 days (`review.auto-days`).

## What is suspicious?
- **Stat farming:** the same pair plays often in a short time, **and** nearly always the same one wins, **and** the other one deals hardly any damage (`monitor.farming.*`, default 8 rounds in 60 minutes, 90 % wins, at most 20 % of the damage). Balanced rounds among friends are never suspicious.
- **Too many rounds against the same opponent** on one day (`stats.max-counted-per-opponent-per-day`, default 50).
- very short rounds without hits or extreme hits.

Whoever has `mrpvp.admin.review` gets a notice when a round waits for review. With MRadmin waiting reviews are also shown in the server status and on the page **Duels** of the web interface.

## Take out of scoring later
A round that was already scored can also be taken out of scoring under **admin menu → Rounds** (with an optional reason) and scored again later - see [Admin menu](admin-menue.md#rounds).
