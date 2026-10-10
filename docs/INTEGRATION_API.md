# Developer API Reference — L’DieDiet 2.0.0

Forge / Minecraft 1.20.1 / Java 17. Optional integrations do not become required mod dependencies.

[Project overview](../README.md) · [Author Guide](../README_MODPACK_AUTHORS.md) · [Developer Setup](DEVELOPMENT.md)

## Scripting and integration entry points


JSON/TOML suffice for scales, groups, composition, CCI, thresholds, world conditions, and formulas. Use KubeJS for external quests, abilities, custom state rules, or event reactions. KubeJS, FTB Quests, and CraftTweaker are not mandatory dependencies.

Public classes:

```text
com.lumbra.ldiediet.api.PackApi
com.lumbra.ldiediet.api.IntegrationApi
com.lumbra.ldiediet.api.DietEvents
```

KubeJS uses its Java bridge, not a dedicated `LDieDietEvents.*` plugin. Register Forge hooks in `kubejs/startup_scripts`, as in the tested project fixture. Changes to startup scripts require restart. Server-side handlers may call PackApi when an actual server player exists.

Copyable startup example, blocking only eating-related accumulation for tagged players:

```js
const LddPackApi = Java.loadClass('com.lumbra.ldiediet.api.PackApi')

ForgeEvents.onEvent('com.lumbra.ldiediet.api.DietEvents$MonotonyPre', event => {
  if (event.getReason() === 'eat' && event.getPlayer().getTags().contains('diet_no_gain')) {
    event.setCanceled(true)
  }
})
```

Add the tag with `/tag @s add diet_no_gain`. This cancels accumulation changes, not physical consumption, nutrition gain, or history. Use `MealPre` to cancel the entire portion.

Calls inside your own server handler:

```js
const DietApi = Java.loadClass('com.lumbra.ldiediet.api.PackApi')

// player is an existing ServerPlayer; stack is the desired food ItemStack.
DietApi.addNutrient(player, 'mypack:protein', 10)
DietApi.setMonotony(player, stack, 0)
DietApi.discoverGroup(player, 'mypack:breakfast')
```

The second fragment is a handler body, not a standalone startup file: `player` and `stack` must come from a real event. Players do not exist during startup initialization. Check permissions for operator commands that change state or grant rewards.

## Every player-state API method

PackApi requires **server-thread** execution with `ServerPlayer`. Changes refresh rewards and synchronize the client. Pass an ItemStack with appropriate NBT to retain FoodKey identity.

| Method | Behavior and limitations |
|---|---|
| `getNutrient(player, id)` | Reads current scale value. |
| `setNutrient(player, id, value)` | Sets nutrition through events, clamped to 0…scale maximum. |
| `addNutrient(player, id, amount)` | Adds/removes nutrition; negative finite amounts are valid. |
| `foodState(player, stack)` | Returns `key`, `monotony`, `stage`, `recoveryMeals`, `eatenCount`, `countExact`, `cci`. |
| `getMonotony(player, stack)` | Reads this FoodKey's accumulation. |
| `setMonotony(player, stack, value)` | Sets through events; boolean reports acceptance. |
| `setMonotony(player, foodKey, value)` | Changes the exact saved record, including old variants. |
| `addMonotony(player, stack, amount)` | Adds/removes accumulation. |
| `getStage(player, stack)` | Reads effective stage with exclusions/policies. |
| `setStage(player, stack, stage)` | Sets a stage's lower boundary, 0…4. Excluded food permits only 0; fixed-stage food permits only its fixed stage. Unreachable boundaries cause an error. |
| `getRecoveryMeals(player, stack)` | Reads other-portion count. |
| `setRecoveryMeals(player, stack, meals)` | Sets a nonnegative count; zero accumulation cannot retain a stockpile of acceleration. |
| `getCCI(player, stack)` | Reads effective CCI, with missing calculation governed by settings. |
| `groupProgress(player, id)` | Counts eligible history after the group's activation cursor. |
| `grantGroup(player, id)` | Attempts activation under its normal conditions; not a consumed portion. |
| `cancelGroup(player, id)` | Removes rewards and closes current activation history. New meals can reactivate it. |
| `cancelGroupEffect(player, id, slot)` | Cancels one zero-indexed slot; reports whether something changed. |
| `activatedGroups(player)` | Returns live activations, including waiting/intermittent states. |
| `isGroupKnown(player, id)` | Whether visible; nonsecret groups need no discovery entry. |
| `discoveredGroups(player)` | Saved group discoveries specifically. |
| `discoverGroup(player, id)` | Reveals independently of granting rewards, through discovery events. |
| `setSuite(player, id)` | Selects an existing imported Diet suite. |
| `refresh(player)` | Refreshes rewards and client state. |
| `consume(player, stack, hunger, saturation, callback)` | Runs an external portion; see External feeding below. |
| `reloadRecipeAdapters(player)` | Refreshes recipe indexing without overwriting saved CCI. |

Numbers must be finite. Unknown IDs, empty stacks, invalid stages/counters, and calls outside the server thread throw exceptions. Setting accumulation/nutrition does not record a meal or increment consumption count. Use `consume` for an actual feeding mechanic.

## Forge events: cancellation, modification, observation

Events are posted on the server's `MinecraftForge.EVENT_BUS`. Nested names are `DietEvents.MealPre` in Java, `DietEvents$MealPre` in KubeJS class strings.

| Event | Capability |
|---|---|
| `MealPre` | Cancels before item/cake consumption; `setNutrition(hunger,saturation)` changes raw nutrition. |
| `MealPost` | Completed portion, source `item`/`cake`/`external`, and actual hunger gain. |
| `MonotonyPre` | `setValue` changes **final** accumulation; cancellation prevents its update. |
| `StagePre` | Cancels the accumulation change that crosses a stage boundary. |
| `StagePost` | Reads stored old/new stages. |
| `RecoveryPre` | Changes final recovery state or cancels the step and counter advance. |
| `RecoveryPost` | Reads recovery result and final counter. |
| `NutrientPre` | Changes/cancels final scale value; reasons `eat`, `decay`, `api`, `death`. |
| `NutrientPost` | Reads stored nutrition. |
| `QuotaPre` | Allows/cancels quota rewards and sets multiplier 0…1000. |
| `QuotaPost` | Reports changed multiplier after reward application/removal. |
| `GroupAttempt` | Cancels activation; `cancelEffect(slot)` cancels a slot; `replaceEffect(slot,definition)` changes reward parameters. |
| `DiscoveryPre` | Cancels group/boundary discovery. |
| `DiscoveryPost` | Reports persisted discovery. |

Cancelable events use `setCanceled(true)`. Post events are not cancelable and do not inherit from Pre; Pre subscriptions do not receive Post. Shared observation bases include `StageChange`, `RecoveryChange`, `NutrientChange`, and `DiscoveryChange`. Legacy `GroupRewardEvent` remains a post-activation notification.

QuotaPre runs at each refresh; QuotaPost only on multiplier transitions. Cancellation removes the quota's own reward rather than freezing a previous bonus. Quota IDs are `scale:<id>:<index>`, `rule:<id>`, and `diet:<suite>:<index>`. Group discoveries use `group:<group-id>`; nutrition boundary discoveries are separate.

Accumulation/stage reasons are `eat`, `recovery`, `api`. Recovery cannot increase accumulation through a hook. Canceling a stage does not cancel physical feeding/history. Use MealPre for an entire portion and quota/group events for a reward.

Java reward hook:

```java
public final class MyDietHooks {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onGroup(com.lumbra.ldiediet.api.DietEvents.GroupAttempt event) {
        if (event.getGroup().toString().equals("mypack:breakfast") && !event.getEffects().isEmpty()) {
            var original = event.getEffects().get(0);
            event.replaceEffect(0, original.withValues(0.2, original.getAmplifier(), 1200));
        }
    }
}
```

Register once with `MinecraftForge.EVENT_BUS.register(new MyDietHooks())`. `withValues(value, amplifier, durationTicks)` retains type, ID, duration mode, operation, and conditions. Replacement preserves type/ID/mode for timer/reload compatibility. It modifies the granted instance, not datapack JSON.

Do not recursively consume from that portion's event or grant the same group from GroupAttempt. Reentry guards reject recursive changes. Prefer side-effect-free condition predicates.

## External predicates and quests

```java
var handle = com.lumbra.ldiediet.api.IntegrationApi.registerCondition(
    "mypack:quest_complete",
    (player, args) -> {
        String quest = args.get("quest").getAsString();
        return myQuestSystem.hasCompleted(player, quest);
    }
);
```

`myQuestSystem` denotes your specific quest integration, not a built-in bridge to every quest mod. Datapack `custom` conditions supply its ID and arguments.

A standalone KubeJS startup predicate using player command tags:

```js
const LddIntegration = Java.loadClass('com.lumbra.ldiediet.api.IntegrationApi')

LddIntegration.registerCondition('mypack:has_tag', (player, args) => {
  return args.has('tag') && player.getTags().contains(args.get('tag').getAsString())
})
```

Group condition:

```json
{
  "custom": [
    {
      "id": "mypack:has_tag",
      "args": {"tag": "healthy_diet_quest_done"},
      "description_key": "mypack.condition.healthy_diet"
    }
  ]
}
```

A quest/command can add the tag, e.g. `/tag @s add healthy_diet_quest_done`. Enable continuous conditions if removing it should temporarily disable a running group reward.

Registration with the same ID replaces the handler. The returned handle's `close()` unregisters it; closing an old handle does not remove a newer registration. Arguments are copied. Missing/failing external APIs should return false with clear diagnostics. A description key only provides UI text.

## External feeding: blocks, machines, abilities

For food sources outside normal item-use completion:

```java
boolean eaten = com.lumbra.ldiediet.api.PackApi.consume(
    player,
    representativeStack,
    6,
    4.0,
    () -> {
        machine.consumeOnePortion();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1200));
    }
);
```

`machine` is your actual mechanism. The representative stack selects food identity/NBT, CCI, composition, history, and groups, and may lack FoodProperties. Hunger is raw food points; saturation is **absolute points**, not vanilla saturation modifier.

The API applies hunger/saturation once with penalties, records the portion/history, performs recovery/nutrition/rewards, and synchronizes. Do not restore the same hunger/saturation manually inside the callback. The callback consumes the real resource; the representative stack is not decremented automatically.

Canceled MealPre returns false without invoking the callback. Callback-applied effects run within eating context and filters. Nested ordinary completion of that portion does not double-record it; nested `consume` is forbidden.

Latest external hunger/saturation values are retained for journal descriptions/forecasts. Normal consumption of the same FoodKey clears those overrides and uses item properties again. If the callback throws, context clears and the portion is not recorded, but arbitrary completed external actions cannot be rolled back automatically. Handle your resource-consumption errors.

## Machine and external recipes for CCI

Ordinary recipes use `getResultItem`/`getIngredients`. Custom types can register adapters:

```java
var handle = com.lumbra.ldiediet.api.IntegrationApi.registerRecipeAdapter(
    "my_mod:machine_food",
    (recipe, registries) -> {
        var machineRecipe = (MyMachineRecipe) recipe;
        return java.util.List.of(new com.lumbra.ldiediet.api.IntegrationApi.CciRecipe(
            machineRecipe.output(),
            machineRecipe.itemAlternatives(),
            0.3
        ));
    }
);
```

`MyMachineRecipe` and its methods represent your real machine API. An adapter **replaces** standard extraction for its recipe type. An empty collection excludes the recipe from CCI.

For recipes not present in RecipeManager, register a contributor:

```java
var handle = com.lumbra.ldiediet.api.IntegrationApi.registerRecipeContributor(
    "mypack:machine_recipes",
    server -> java.util.List.of(new com.lumbra.ldiediet.api.IntegrationApi.CciRecipe(
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE),
        java.util.List.of(java.util.List.of(
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD))),
        0.75
    ))
);
```

CciRecipe parameters are output, ingredient positions (each with alternatives), and finite nonnegative processing weight added to `stages_weight`. Empty outputs/positions and invalid weights are rejected; ItemStacks are copied.

The model remains item-ID-based. Translate fluids, energy, counts, and machine requirements into ingredients/weight yourself. Configure variant complexity with food profiles. Closing handles removes registrations; index revision tracks integration changes. Recalculate saved automatic CCI explicitly afterward; manual values remain unchanged.


## Data and validation

World condition fields, stage profiles, formulas, and every configuration option are documented in the [Author Guide](../README_MODPACK_AUTHORS.md). Use `docs/schemas` and `/ldiediet validate`. Runnable opt-in examples are in `examples` and `docs/examples`. Forecasts do not execute third-party Pre handlers for hypothetical future meals; they cannot guarantee future hook cancellations or world-dependent changes.
