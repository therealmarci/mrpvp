# Permissions

How the groups are built in all plugins: [Permissions with LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Groups
Every group contains the one below, the team groups also contain `mrpvp.player` - best give `mrpvp.player` to the default group:

<!-- auto:permission-groups -->

```text
/lp group default permission set mrpvp.player true
/lp group supporter permission set mrpvp.groups.support true
/lp group staff permission set mrpvp.groups.staff true
/lp group admin permission set mrpvp.groups.admin true
```

## Single permissions
"Included in" names the smallest group that brings the permission; "–" means: in no group, only given on its own.

<!-- auto:permissions -->

Your own kits can additionally require their own permission - see [Kits](kits.md#permission-per-kit).
