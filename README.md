# Pxokie's TierTagger

A Fabric mod for Minecraft 1.21.11 that shows players' PvP tiers from MCTiers, PvPTiers, SubTiers, TierTests and MCPvP next to their names. It also adds a profile screen that shows every tier of a player on one screen.

It is a fork of [TierTagger](https://github.com/mctiers-dev/TierTagger) by uku and netiyiy.

## Building

You need JDK 21.

```
./gradlew build
```

The jar ends up in `build/libs/`.

## Project layout

| Path | What it does |
|---|---|
| `source/` | One adapter per tierlist API (`McTiersSource`, `PvpTiersSource`, `TierTestsSource`, `McPvpSource`) |
| `TierCache` | Caches tiers per tierlist and refreshes them every 5 minutes |
| `config/` | Config and the Mod Menu screen (via ukulib) |
| `mixin/` | Hooks for nametags, the player list and text-display nametags |
| `tierlist/` | The profile screen and the player search screen |

## Licence

MPL-2.0, same as TierTagger. See [LICENSE](LICENSE).

The tierlist logos in `assets/pxokies-tiertagger/textures/tierlists/` belong to their respective tierlists.
