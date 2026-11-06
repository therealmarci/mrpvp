# Permissions

Wie die Gruppen in allen Plugins aufgebaut sind: [Rechte mit LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Gruppen
Jede Gruppe enthält die darunter, die Team-Gruppen auch `mrpvp.player` - am besten `mrpvp.player` der Default-Gruppe geben:

<!-- auto:permission-groups -->

```text
/lp group default permission set mrpvp.player true
/lp group supporter permission set mrpvp.groups.support true
/lp group staff permission set mrpvp.groups.staff true
/lp group admin permission set mrpvp.groups.admin true
```

## Einzeln
„Enthalten in“ nennt die kleinste Gruppe, die die Permission mitbringt; „–“ heißt: in keiner Gruppe, nur einzeln vergeben.

<!-- auto:permissions -->

Eigene Kits können zusätzlich eine eigene Permission verlangen - siehe [Kits](kits.md#permission-pro-kit).
