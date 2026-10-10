# Developer Setup — L’DieDiet 2.0.0

[Project overview](../README.md) · [Modpack Author Guide](../README_MODPACK_AUTHORS.md) · [API Reference](INTEGRATION_API.md)

## Toolchain and build

Use Java 17, Minecraft 1.20.1, and Forge 47.x. The repository includes the Gradle wrapper; a global Gradle installation is unnecessary.

```text
./gradlew build
./gradlew test
./gradlew runGameTestServer
```

Use `gradlew.bat` on Windows. Output: `build/libs/l'diediet_2.0.0.jar`. Server-side GameTests complement unit tests; release JARs exclude GameTest classes and fixtures.

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/com/lumbra/ldiediet/api` | Public player facade, integration registration, and Forge events. |
| `src/main/java/com/lumbra/ldiediet/data` | Definition loading, food identities, profiles, and journal data. |
| `src/main/java/com/lumbra/ldiediet/math` | Expression parsing, configurable formulas, recipe CCI, and forecasts. |
| `src/main/java/com/lumbra/ldiediet/consumption` | Portion capture, penalties, use timing, and external feeding. |
| `src/main/java/com/lumbra/ldiediet/rewards` | Scale/group/quota rewards, decay, and source ownership. |
| `src/main/java/com/lumbra/ldiediet/compat` | Optional Diet and climate compatibility. |
| `src/main/java/com/lumbra/ldiediet/network` | Authoritative server-to-client state synchronization. |
| `src/main/java/com/lumbra/ldiediet/client` | Journal, tooltips, and client presentation. |
| `src/test` / `src/testFixtures` | Unit tests and opt-in fixture data. |
| `docs/schemas` | JSON schemas for authoring tools. |
| `examples` / `docs/examples` | Opt-in datapacks/resource packs and integration examples. |

Installable datapacks are maintained in [l-diediet-datapacks](https://github.com/lbdumbra-jpg/l-diediet-datapacks). Test fixtures are not automatically installed in a user's world.

## Integration contracts

Use `PackApi` on the server thread with ServerPlayer. Pass ItemStack rather than only an item ID when NBT identity matters. Use `IntegrationApi` to register custom conditions and recipe adapters/contributors, and close registration handles when unloading an integration.

Use Forge Pre events to alter/cancel an operation and Post events to observe completed state. Do not recursively consume, grant the same group, or mutate the same food/nutrient inside its own hook. Changes to state are distinct from consumed portions; external feeding should use `PackApi.consume`.

The API reference documents parameter units, cancellation semantics, source ownership, and forecast limits. Examples using `myQuestSystem`, `machine`, or `MyMachineRecipe` are integration templates: replace those names with your actual external API.

## Verification and contributions

Run relevant unit tests and GameTests for changes to consumption, persistence, formulas, or rewards. For client behavior, check a separate multiplayer client, reload synchronization, both interface languages, and resource-pack overrides.

Treat manual configuration and explicit datapack CCI as protected author choices. Preserve saved FoodKey identity, reward-slot ownership, and reentry guards. Validate JSON definitions with schemas and `/ldiediet validate` in the target mod set; schema validity alone does not prove registry availability or reachability.

Describe the trigger, resulting behavior, and validation in a pull request. Report compatibility issues with versions, logs, configuration/datapack, and minimal reproduction steps. Code contributions use the repository's [MIT license](../LICENSE).
