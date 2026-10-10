# L’DieDiet

Food variety, nutrition, and rewards for **Minecraft Java 1.20.1 / Forge 47.x**. Requires Java 17. Version 2.0.0. MIT licensed.

Eating the same meal eventually gets old. L’DieDiet encourages you to try other foods, maintain your nutrition, and discover rewards for food combinations. A varied diet restores your appetite for earlier meals.

[Download the mod](https://github.com/lbdumbra-jpg/l-diediet/releases/tag/v2.0.0) · [Get datapacks](https://github.com/lbdumbra-jpg/l-diediet-datapacks/releases/tag/v2.0.0) · [Modpack Author Guide](README_MODPACK_AUTHORS.md) · [Developer API](docs/INTEGRATION_API.md)

## Player features

- Five stages for each food, from fresh to aversion. Stages affect hunger and saturation restoration, eating time, nutrition gain, and allowed beneficial food effects.
- Appetite recovery by eating other nutritious foods. Waiting alone does not restore it.
- A journal on **N**, with nutrition, eaten foods, groups, and active effects. View effect sources and portions remaining to each stage boundary.
- Configurable nutrition scales, fill-based bonuses, and natural decay as hunger is spent.
- Food groups with rewards and achievements. Secret groups reveal themselves after their first effective reward activation.
- English and Russian interfaces. Resource packs can replace translations, presentation, and icons.

## Installation

1. Install Minecraft 1.20.1, Forge 47.x, and Java 17.
2. Put the release JAR in `mods`. Multiplayer requires the mod on both server and clients.
3. For predefined nutrition scales and groups, install **one** separate datapack ZIP in `<world>/datapacks`. Do not unzip it; `pack.mcmeta` is at the archive root.
4. Run `/reload` and check `/datapack list enabled`. Open the journal with N, configurable in Controls.

GitHub normalizes the uploaded JAR filename to `l.diediet_2.0.0.jar`. Both it and the JAR inside `LDieDiet-2.0.0.zip` work. The ZIP preserves the original filename `l'diediet_2.0.0.jar`; extract that JAR before installing it in `mods`.

Habituation works independently. Groups, scales, and rewards are no longer bundled as an installable datapack inside the mod JAR: install a datapack or provide supported Diet data.

## Separate datapacks

| Pack | Contents |
|---|---|
| **All of Food** | Current FreshCraft food/drink catalog: 1091 IDs, 17 original categories, six nutrition scales, achievements, and saved CCI profiles. |
| **All of Food Vanilla** | 43 vanilla food/drink IDs, 11 applicable categories, six scales, and achievements. Requires only L’DieDiet. |
| **LDieDiet Original** | Original 17 groups, scales, exclusions, and formulas extracted from the mod. Original external effects, conditions, and achievement references are preserved. |

[Datapack source and installation details](https://github.com/lbdumbra-jpg/l-diediet-datapacks)

All of Food reflects the FreshCraft registry exported on October 10, 2026. Foods from newly added mods need new profiles/membership. `food-assignments.csv` lists assignments and `coverage.json` summarizes coverage. Foods may belong to multiple groups. Splash/lingering potions are excluded from the catalog; ordinary potions are counted as drinks but excluded from habituation.

The variants share group/scale IDs: install one variant per world. Original retains source-pack dependencies; choose Vanilla for ordinary Minecraft.

## Default behavior

Repeated consumption builds habituation based on original nutritional value and CCI, the food's complexity. Other food reduces recorded accumulation. By default, recovery requires an actual gain of at least one whole hunger point after penalties/capacity limits. Creative-mode testing at full hunger therefore does not demonstrate recovery. Pack authors can change eligibility.

Nutrition scales decay on hunger loss by default. Death removes **50% of each current nutrition value**; that setting does not reset habituation, lifetime consumption counts, or discoveries. These settings are configurable.

Automatic recipe-derived CCI is disabled by default. Ready-made packs contain saved values. Authors can supply their own or enable calculation; `/ldiediet calc_all_cci` explicitly recalculates automatic values with operator permissions.

## Compatibility and customization

Diet is optional. With the supported version installed, L’DieDiet imports its supported data and disables its gameplay handlers to avoid duplicate systems. The tested version is Diet Forge 2.1.1 for Minecraft 1.20.1; unsupported Diet versions stop loading with an incompatibility message.

The [Author Guide](README_MODPACK_AUTHORS.md) covers every scale/group field, configuration, formulas, NBT variants, rewards, and translations. The [API Reference](docs/INTEGRATION_API.md) covers server state, cancelable Forge events, KubeJS access, world predicates, external feeding, and custom recipe adapters.

For a bug report, include Forge/mod versions, `latest.log`, relevant datapack/configuration, and reproduction steps. [Report a bug](https://github.com/lbdumbra-jpg/l-diediet/issues/new/choose).

## Building from source

With Java 17:

```text
./gradlew build
./gradlew test
./gradlew runGameTestServer
```

On Windows, use `gradlew.bat`. The build output is `build/libs/l'diediet_2.0.0.jar`. Test classes and fixtures are excluded from the release JAR. See [Developer Setup](docs/DEVELOPMENT.md) for project layout and contributions.

## License

The mod code and datapacks prepared in this project are distributed under [MIT](LICENSE). Separate datapacks have their own copy of the license.
