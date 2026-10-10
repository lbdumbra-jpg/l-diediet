# L’DieDiet 2.0.0 — Modpack Author Guide

This guide covers the implemented configuration features in L’DieDiet 2.0.0 for Minecraft Java 1.20.1 / Forge. It explains how settings interact and provides editable examples. Example values illustrate a possible configuration; they do not imply that every installed pack uses that balance.

The four chapters follow a practical order: nutrition scales, habituation and CCI, food groups, then translations and integrations. Each chapter proceeds from creating data to configuring behavior and checking the result.

## Before you start: where data belongs

Datapacks contain server definitions. Resource packs contain client translations and images. The server synchronizes definitions and player values, but custom images and language files must be delivered to clients separately.

Create a directory in `<world>/datapacks` with this `pack.mcmeta` at its root:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "My pack — LDieDiet configuration"
  }
}
```

A resource pack for 1.20.1 also uses `pack_format: 15`, with `assets` instead of `data`. In a ZIP, `pack.mcmeta` must be at the archive root, not inside another directory.

`mypack` is the example modpack namespace. Choose a stable namespace using lowercase Latin letters, digits, `_`, `-`, and `.`. A definition's ID combines its namespace and file path without `.json`.

| Data | Location | Apply changes |
|---|---|---|
| Nutrition scales | `data/<namespace>/nutrition_scales/*.json` | `/reload` |
| Compound nutrition rewards | `data/<namespace>/nutrition_rules/*.json` | `/reload` |
| Item profile | `data/<item namespace>/food_profiles/<item path>.json` | `/reload` |
| Formulas | **`data/ldiediet/formulas.json`** | `/reload` |
| Individual stage policies | `data/<namespace>/food_stage_profiles/*.json` | `/reload` |
| Fixed stages | `data/<namespace>/exclusions/*.json` | `/reload` |
| Food groups | `data/<namespace>/food_groups/*.json` | `/reload` |
| Explicit profiles and CCI cache | `config/ldiediet/diet_config.json` | `/ldiediet reload` or `/reload` |
| Server settings | `<world>/serverconfig/ldiediet-server.toml` | Forge watches changes; verify the effective values in your world |
| Defaults for new worlds | `defaultconfigs/ldiediet-server.toml` | Used when creating a world; does not overwrite existing worlds |
| Client settings | `config/ldiediet-client.toml` | On each client |
| Translations and images | `assets/<namespace>/lang`, `assets/<namespace>/textures` | Client resource reload, e.g. F3+T |

L’DieDiet does not automatically install predefined groups and scales. Start with a separate All of Food, All of Food Vanilla, or LDieDiet Original datapack, or create your own. A higher-priority datapack replaces a resource with the same namespace and path. Different IDs create different definitions: renaming a file does not replace the old rule.

JSON does not allow comments or trailing commas. Use a decimal point. Experiment in a copy of your world: state-changing commands really change player data.

## 1. Nutrition scales and attribute rewards

### 1.1. A scale is not a Minecraft attribute

A **nutrition scale** stores protein, vegetables, or any quantity you define. A **Minecraft attribute** is maximum health, movement speed, attack damage, or another registered player characteristic. A rule converts a scale's value into an attribute bonus or status effect.

You can create your own scales, maxima, food composition, decay rates, and reward intervals. The system need not represent real nutrition: a scale can describe magic energy, spicy food, or another modpack resource.

Diet is optional. When a supported Diet version is installed, L’DieDiet imports its supported data and disables its gameplay handlers to prevent duplicate processing. Diet remains loaded; this does not remove it from the mod list.

### 1.2. Create a scale with two reward tiers

`data/mypack/nutrition_scales/protein.json` creates **`mypack:protein`**:

```json
{
  "name": "Protein",
  "name_key": "mypack.nutrition.protein",
  "max_value": 100,
  "decay_multiplier": 1.0,
  "decay_percent_per_minute": 0.5,
  "order": 10,
  "color": "#A96C58",
  "icon": "minecraft:textures/item/beef.png",
  "thresholds": [
    {
      "min": 80,
      "max": 100,
      "min_inclusive": true,
      "max_inclusive": false,
      "modifiers": [
        {"attribute": "minecraft:generic.max_health", "amount": 2, "operation": "addition"}
      ]
    },
    {
      "min": 100,
      "max": 100,
      "modifiers": [
        {"attribute": "minecraft:generic.max_health", "amount": 4, "operation": "addition"}
      ]
    }
  ]
}
```

At values from 80 inclusive to 100 exclusive, this gives +2 maximum health. At exactly 100 it gives +4: the earlier interval no longer matches, so +2 and +4 do not stack. Two health points are one heart. Increasing maximum health does not itself immediately heal the player.

| Scale field | Meaning, defaults, and options |
|---|---|
| `name` | Literal fallback name. Defaults to the ID path. Do not put a translation key here instead of a readable name. |
| `name_key` | Translation key. Missing translations fall back to `name`. Set it explicitly for a custom scale. |
| `max_value` | Finite positive maximum, default 100. Values are clamped to 0…maximum. You can use 20, 200, etc. |
| `decay_multiplier` | Nonnegative **HUNGER** decay multiplier. 0 disables this scale's decay in that mode; 0.5 halves it; 2 doubles it. Explicit values override imported Diet multipliers. |
| `decay_percent_per_minute` | **TIMED** decay: 0…100 percentage points of the maximum per 1200 ticks. Omission inherits the global TOML setting; 0 disables this scale's timed decay. |
| `order` | Journal row order, ascending; default 0. |
| `color` | Fill color as `#RRGGBB` or `RRGGBB`, exactly six hexadecimal digits. |
| `icon` | **Texture file** ID, e.g. `mypack:textures/gui/protein.png`. Use 16×16 for consistent display. Unlike a group's icon, this is not an item ID. |
| `thresholds` | Array of reward intervals. An empty array gives no direct attribute rewards. Add as many intervals as needed. |

Each `thresholds` entry supports:

| Field | Meaning |
|---|---|
| `min` | Lower boundary in **scale units**, default 0. |
| `max` | Upper boundary in **scale units**, default scale maximum; must not be below `min`. |
| `min_inclusive` | Include the lower boundary; default `true`. |
| `max_inclusive` | Include the upper boundary; default `true`. |
| `modifiers` | Array of attribute rewards. One interval can affect several attributes. |
| `modifiers[].attribute` | Full ID of a registered attribute present on the player. |
| `modifiers[].amount` | Finite number: positive for a bonus, negative for a penalty. |
| `modifiers[].operation` | `addition` by default, `multiply_base`, or `multiply_total`. |

**Scale thresholds are not automatically percentages.** With `max_value: 200`, an 80…100 interval represents 40…50% fill. Use 160 for an 80% reward. Journal markers convert positions to percentages of the maximum.

Overlapping intervals apply simultaneously. `[80,100]` and `[100,100]` both match at 100. Use `max_inclusive: false` on the lower tier for replacement instead of stacking. The validator warns about overlap.

### 1.3. Attribute operations and stacking

| Operation | Example | Effect |
|---|---|---|
| `addition` | `amount: 2` | Adds two units. |
| `multiply_base` | `amount: 0.1` | Adds 10% at Minecraft's base-multiplier calculation stage. |
| `multiply_total` | `amount: 0.1` | Multiplies the intermediate total by 1.1. |

Compatible aliases are `add`, `multiply_base_value`, and `multiply_total_value`. Prefer the three primary names for new definitions.

Minecraft applies the operations in this order:

> X₁ = X₀ + Σ aᵢ
> X₂ = X₁ × (1 + Σ bᵢ)
> X₃ = X₂ × ∏ⱼ (1 + cⱼ)

Here aᵢ are additions, bᵢ are base multipliers, and cⱼ are total multipliers. The attribute's own allowed range then clamps the result. Two total multipliers of 0.1 give ×1.21, not ×1.20. Rewards from different rules may stack; scales do not automatically make them mutually exclusive.

### 1.4. Assign nutrition to food

Create **`data/minecraft/food_profiles/apple.json`**:

```json
{
  "nutrients": {
    "mypack:protein": 0.2,
    "fruits": 0.8
  }
}
```

`fruits` requires a corresponding scale. Use a full ID for custom scales, such as `mypack:protein`. Scales in the `ldiediet` namespace accept short keys such as `proteins`, `vegetables`, and `fruits`.

For `farmersdelight:apple_pie_slice`, use `data/farmersdelight/food_profiles/apple_pie_slice.json`. For `othermod:food/example`, use `data/othermod/food_profiles/food/example.json`. This namespace belongs to the **item**, not your pack.

Nutrition weights are not direct scale units. The default calculation is:

> Q = H + S
> Bᵢ = Q × wᵢ × ∏ⱼ max(0, 1 + uⱼ)
> ΔNᵢ = Bᵢ × pₛ
> Nᵢ′ = min(Nᵢ,max, max(0, Nᵢ + ΔNᵢ))

H is the portion's original hunger points; S is its original **absolute** saturation; wᵢ is its `nutrients` weight; uⱼ are active group nutrition multipliers; pₛ is the food stage's nutrition multiplier.

An item's vanilla saturation property is a modifier f: S = 2 × H × f. An apple has H = 4 and f = 0.3, so S = 2.4 and Q = 6.4. Weights 0.2 and 0.8 give 1.28 and 5.12 units at stage 0 without group multipliers. Reaching a scale's maximum reduces the actual gain.

Weights are **not normalized**. A total weight of 2 gives twice the total nutrition of a total weight of 1. All of Food generally uses a total of 1 for nutritious food. Negative weights are technically supported and can reduce a scale; use them only as an intentional penalty mechanic.

`food_value` replaces Q for the mod's calculations without changing actual vanilla hunger or saturation restoration. This is useful for drinks and external feeding. `food_value: 5` with a weight of 0.4 gives a base contribution of 2 even if the item itself restores no hunger. Q ≤ 0 prevents normal nutrition processing. A stage nutrition multiplier of 0 prevents even a custom `nutrient_add` expression from running.

You can replace `nutrient_add`; chapter 2 documents its syntax and variables. Its `base_n` already includes Q, the weight, and active group multipliers.

### 1.5. Datapack, configuration, and Diet precedence

Fields merge in this order: **Diet import → datapack `food_profiles` → explicit `diet_config.json` entry**. A higher source replaces each matching field as a whole. Explicit `"nutrients": {}` therefore removes the lower source's entire composition. `nbt_filters` is also replaced as a whole array.

Configuration entries belong in `overrides`:

```json
{
  "schema_version": 2,
  "overrides": {
    "minecraft:apple": {
      "nutrients": {"mypack:protein": 0.3, "fruits": 0.7}
    }
  }
}
```

This is another configuration route, especially for a local pack. For a separately distributed datapack, `food_profiles` avoids requiring users to copy a configuration file manually. Existing explicit entries still win: inspect the configuration first if a datapack seems ineffective.

Diet import handles supported food tags, nutrition groups, rule suites, and settings. It does not automatically understand every mechanic of every Diet addon. The saved `ldiediet:diet_baseline` package has the lowest priority. Assign an imported suite with `/ldiediet suite @s <suite>`. Native `nutrition_rules` apply to every player without suite selection.

A journal scale is hidden if no food composition, NBT variant, reward condition, or targeted nutrition multiplier references it and the player has no stored value. Hiding does not delete saved data. Check all references before completely removing a scale.

### 1.6. Compound quotas: multiple scales, status effects, achievements

A scale's direct `thresholds` provide attributes. For status effects, multiple requirements, or world conditions, create `data/mypack/nutrition_rules/balanced.json`:

```json
{
  "name": "Balanced diet",
  "name_key": "mypack.rule.balanced",
  "logic": "AND",
  "conditions": [
    {
      "nutrients": ["mypack:protein", "vegetables"],
      "match": "all",
      "min": 60,
      "max": 100,
      "percent": true,
      "min_inclusive": true,
      "max_inclusive": true
    }
  ],
  "attributes": [
    {"attribute": "minecraft:generic.movement_speed", "amount": 0.1, "operation": "multiply_base"}
  ],
  "effects": [{"effect": "minecraft:strength", "amplifier": 0}],
  "advancement": "mypack:balanced_diet"
}
```

Its ID is `mypack:balanced`. Both scales must be at least 60%. Speed and Strength I are maintained while the quota matches. When a value falls below the boundary, the rule's sources are removed; unrelated effects are retained. The achievement is granted separately and is not revoked when the quota stops matching.

| Rule field | Options and meaning |
|---|---|
| `name`, `name_key` | Fallback name and translation key. The name defaults to the ID path. |
| `logic` | `AND` by default: all condition objects must match. `OR`: one is enough. |
| `conditions` | Nonempty list of nutrition conditions. |
| `conditions[].nutrients` | Nonempty list of scale IDs. Repeated references to one scale do not count twice in one condition. |
| `conditions[].match` | `all` by default; also `any`, `none`, `average`, `every`. |
| `conditions[].min`, `max` | Range, default 0…100. Minimum must not exceed maximum. |
| `conditions[].percent` | `true` by default: compare percentages of each scale's maximum. `false`: compare units. |
| `conditions[].min_inclusive`, `max_inclusive` | Boundary inclusion; both default to `true`. |
| `attributes` | Array of `{attribute, amount, operation}` using the operations in 1.3. |
| `effects` | Array of `{effect, amplifier}`. `effect` is a status ID; `amplifier` is 0…255, with 0 meaning level I. Quotas have no `duration_ticks`: matching conditions maintain the source. |
| `advancement` | Optional ID of a separately defined achievement. Grants its remaining criteria. |
| `world_conditions` | Additional world requirements; full format in 3.7. This object must match independently of nutrition `logic`. |

| `match` | Test | Use case |
|---|---|---|
| `all` | Every listed scale is in range. | A complete diet. |
| `any` | At least one is in range. | Alternative energy sources. |
| `none` | None is in range. | A reward for avoiding excess sugar/fat. |
| `average` | Their average is in range; averages percentages when `percent: true`. | Overall nutrition. |
| `every` | Counts every matching scale and multiplies attribute `amount` by the count. | +1 health for each qualifying food category. |

An unknown scale fails even a `none` condition: a typo must not grant a free reward. Multiple `every` conditions sum positive counts; ordinary conditions gate eligibility but do not add to that sum. A successful rule without qualifying `every` conditions has multiplier 1. Potion levels are not multiplied by `every`.

Rules refresh on meals, nutrition changes through commands/API, login, reload, and once per second. Different overlapping rules can stack. For tier replacement at 100%, use `max_inclusive: false` on the lower rule, as with direct scale thresholds.

### 1.7. Natural nutrition decay

Server TOML:

```toml
[Nutrition]
decay_enabled = true
decay_mode = "HUNGER"
decay_penalty_per_group = -1
decay_percent_per_minute = 1.0
decay_in_creative = false
death_loss_percent = 50.0
```

| Setting | Full behavior |
|---|---|
| `decay_enabled` | Global natural-decay switch. `false` disables decay, including local rates. Death loss is configured separately. |
| `decay_mode` | `HUNGER` by default, driven by hunger loss; `TIMED`, driven by game ticks. |
| `decay_penalty_per_group` | HUNGER: 0…100% reduction for each additional participating nonempty scale. `-1` imports Diet's setting, including saved import data, or uses 15 without it. |
| `decay_percent_per_minute` | TIMED: 0…100 percentage points per 1200 game ticks; default 1.0. Unused in HUNGER. |
| `decay_in_creative` | Allows decay for living creative players. HUNGER still requires hunger loss. Spectators do not decay. |
| `death_loss_percent` | Separate loss of **each current value** on death; 0…100%, default 50. |

HUNGER uses Diet 2.1.1's algorithm. Let Nᵢ be a current scale, Lᵢ its maximum, vᵢ = Nᵢ/Lᵢ, h the lost whole hunger points, n the participating nonempty scales, p the penalty per additional scale, and dᵢ that scale's decay multiplier:

> Δvᵢ = (e^(vᵢ) / 100) × (h / n) × (1 − p / 100)^(n−1) × dᵢ
> Nᵢ′ = Lᵢ × max(0, vᵢ − Δvᵢ)

One hunger point is half a food icon. Checks run every server tick. Saturation expenditure without losing a whole hunger point does not reduce nutrition. Five scales at 50%, with h = 1, p = 15, dᵢ = 1, each lose about 0.172 percentage points. Fuller scales lose more under otherwise equal conditions; additional scales reduce decay. With p = 100 and multiple participating scales, the multiplier is zero.

TIMED uses:

> Nᵢ′ = max(0, Nᵢ − Lᵢ × (rᵢ / 100) × (Δt / 1200))

rᵢ is the local rate or global TOML rate; Δt is elapsed game ticks. Updates run every 20 ticks. A rate of 1 means 20 percentage points per standard 24000-tick Minecraft day, which may take longer than 20 real minutes on a lagging server.

Do not confuse the settings: `decay_multiplier` affects HUNGER; `decay_percent_per_minute` affects TIMED. Set both local values to 0 to prevent a custom scale from decaying in either mode. Offline, dead, and spectator players do not accumulate natural decay; there is no offline catch-up. Login/respawn does not retroactively apply earlier hunger expenditure.

### 1.8. Death loss and verification

> Nᵢ′ = Nᵢ × (1 − p_death / 100)

At 50%, a value of 80 becomes 40. 0 preserves all nutrition; 100 clears it. This does not subtract a fixed percentage of the maximum. Rewards refresh after respawn. Dimension travel is not death. Habituation, discoveries, and lifetime consumption counters are unaffected; separate history/group death settings are in chapter 3.

```text
/reload
/ldiediet validate
/ldiediet nutrition @s mypack:protein
/ldiediet nutrition @s mypack:protein 79
/ldiediet nutrition @s mypack:protein 80
/ldiediet nutrition @s mypack:protein 100
/ldiediet rule @s mypack:balanced
```

For the example maximum of 100, 79 gives no reward, 80 gives +2 health, and 100 gives +4. Set **all** required scales when testing a compound quota. L’DieDiet commands require permission level 2.

## 2. Habituation and CCI

### 2.1. Separate pieces of player state

Each food stores habituation M, stage 0…4, an other-meal counter for recovery acceleration, and lifetime consumption count. These are distinct. Lifetime apples eaten cannot determine the stage: other foods reduce M while the lifetime count remains.

CCI is configured food complexity. Under the current defaults, simple foods build habituation more slowly and recover more slowly; complex foods build it faster and recover faster. CCI may be fractional. Configuration and API names use CCI, not SSI.

One portion is one completed meal or one registered cake bite/external portion. A stack's item count does not multiply one meal. Interrupted use does not record a completed portion.

The stage is captured when eating begins. That portion uses the old stage, then updates accumulation and forecasts. A boundary crossed by a portion therefore affects the next portion. Reloading midway through eating does not change the captured policy.

### 2.2. Complete food profiles and NBT variants

The correct path is **`data/minecraft/food_profiles/apple.json`**:

```json
{
  "cci": 1.0,
  "base_monotony": 10.0,
  "food_value": 6.4,
  "is_exclusion": false,
  "nutrients": {"fruits": 0.8, "sugars": 0.2},
  "nbt_filters": [
    {
      "variant_id": "enchanted",
      "match": "{Special:1b}",
      "cci": 3.0,
      "base_monotony": 8.0,
      "food_value": 12.0,
      "nutrients": {"fruits": 0.9}
    }
  ]
}
```

| Field | Behavior |
|---|---|
| `cci` | Explicit complexity. Recommended ordinary range 1…4; general bounds are in `formulas.json`. |
| `base_monotony` | Individual growth scale, default 10. 20 doubles the `base_increase` supplied to the formula; 5 halves it. The positive-growth floor may mask reductions. |
| `food_value` | Explicit Q, replacing original hunger plus saturation for growth, nutrition, and recovery eligibility. Does not change vanilla FoodProperties. Negative values become 0; omission uses H + S. |
| `is_exclusion` | `true` excludes the portion from L’DieDiet habituation, history/groups, and scale gain while retaining normal item behavior. This is broader than disabling one penalty. |
| `nutrients` | Scale weights described in 1.4. |
| `nbt_filters` | Ordered variants; first match wins. |
| `nbt_filters[].match` | Partial SNBT pattern; extra item tags do not prevent matching. NBT number types matter: do not casually replace `1b` with `1`. |
| `nbt_filters[].variant_id` | Stable variant name, up to 256 characters. Without it, the ID derives from SNBT. Explicit stable IDs avoid unintentionally creating new history when patterns change. |
| Variant profile fields | Override `cci`, `base_monotony`, `food_value`, and `is_exclusion`. Variant nutrition entries overlay base keys. |
| `cci_source` | Configuration cache marker: `manual` or `auto`. In distributed food profiles, set explicit `cci` without marking it as a cache. |

The special apple has its own FoodKey and accumulation. Ordinary and special apples can count as distinct foods for `require_unique`. Group membership still selects item IDs, not SNBT: a group containing `minecraft:apple` includes both.

**Current variant nutrition behavior:** nonzero entries replace corresponding base weights; omitted keys inherit. Zero entries are filtered during parsing, so `"fruits": 0` inside a variant does not reliably remove the inherited fruit weight. An empty variant map also does not clear the base. For completely different compositions, leave the base map empty and define complete nonzero maps in separate NBT rules. The earlier guide incorrectly described zero as disabling inherited contribution.

In `diet_config.json`, wrap profiles in `schema_version` and `overrides` as in 1.5. Omitted top-level fields inherit; supplied matching fields replace the lower source as a whole.

### 2.3. Manual, automatic, and recipe-derived CCI

An explicit profile `cci` is the simplest route and is protected from automatic recalculation. Configuration values may use `cci_source: "manual"`; older values without a source are also treated as manual.

```toml
[CCI]
automatic_cci = false
```

`false` is the default: use saved/explicit values, or minimum CCI if absent. `true` calculates and saves missing values from recipes rather than recalculating every meal. The cache survives restarts and resource reloads. Old automatic configuration values do not override explicit datapack CCI.

| Command | Behavior |
|---|---|
| `/ldiediet calc_all_cci` | Explicitly recalculates automatic/missing values even with auto calculation disabled. Preserves manual values. |
| `/ldiediet update_config` | Adds missing food entries. Fills automatic CCI when enabled. Does not invent nutrition composition or group membership. |
| `/ldiediet clean_config` | Removes configuration entries for absent item IDs. Does not arbitrarily clear existing foods. |
| `/ldiediet reload` | Reloads resources and repaired configuration. |

Changing recipes or `cci_formula` does not necessarily overwrite saved values. Run explicit recalculation afterward; manual values remain protected. If external edits change the file on disk, automatic writes pause until reload. Malformed configuration is not silently overwritten; the last working state is retained.

Recipe formula:

> C_recipe = min(C_max, max(C_min, C̄_ingredients + 0.4 × √max(0, k − 1) + W))

```json
{
  "cci_formula": "average_ingredients_cci + 0.4 * sqrt(max(0, ingredients_count - 1)) + stages_weight"
}
```

| Variable | Construction |
|---|---|
| `average_ingredients_cci` | Average ingredient-position complexity: average valid alternatives for each position, then average positions. |
| `ingredients_count` | Number of ingredient positions. Repeated positions count again; not merely unique item IDs. |
| `stages_weight` | 0.1 × greatest ingredient preparation depth, plus the adapter's `processingWeight`. |

Three raw ingredients at CCI 1 with no extra weight/depth give 1 + 0.4 × √2 ≈ 1.566. Multiple valid recipes are averaged, not reduced to the cheapest recipe. Results are clamped to `cci_min`/`cci_max`.

Raw materials without a suitable recipe use the minimum. Cyclic conversions do not increase complexity endlessly: the calculator removes backward dependencies within cyclic components; unproductive cycles use a minimum baseline. This is a recipe-graph estimate, not a hand-curated assessment of every dish.

Output counts, fluids, and custom machine data are not universally recognized as economic cost. Use adapters/contributors from chapter 4 or manual CCI for such recipes.

### 2.4. Global stages and penalties

```toml
[General]
stage_thresholds = [5, 10, 15, 20]
hunger_multipliers = [1.0, 0.75, 0.5, 0.25, 0.25]
min_hunger_restored = 0.5
enable_saturation_penalty = true

[Monotony]
minimum_per_meal = 0.25

[Stages]
chewing_multipliers = [1.0, 1.5, 2.0, 2.5, 3.0]
nutrient_multipliers = [1.0, 1.0, 1.0, 0.5, 0.0]
allow_food_buffs = [true, true, false, false, false]

[FoodEffects]
allowed_food_effects = []
blocked_food_effects = []
```

| Setting | Configuration scope |
|---|---|
| `stage_thresholds` | Exactly four strictly increasing positive integers of **accumulation**, not portions or CCI. Boundaries are inclusive. |
| `hunger_multipliers` | Five finite 0…1 values for stages 0…4. |
| `min_hunger_restored` | Minimum restoration after penalties, 0…10, default 0.5. The integer grant rounds the minimum upward: 0.5 means at least one point for an originally nutritious portion. Cannot exceed remaining hunger capacity. |
| `enable_saturation_penalty` | Controls default saturation penalties; without a specific profile, saturation follows the hunger multiplier. |
| `minimum_per_meal` | Floor for **positive** growth, 0…100, default 0.25. Formula output zero remains zero; setting 0 removes the floor. |
| `chewing_multipliers` | Five values 1…100: use-duration multipliers. |
| `nutrient_multipliers` | Five values 0…100: nutrition scale gain, not vanilla hunger. |
| `allow_food_buffs` | Five booleans controlling beneficial food-applied effects, not independent group/quota rewards. |
| `allowed_food_effects` | Effect IDs allowed despite a stage's beneficial-effect block. |
| `blocked_food_effects` | Always-blocked food effect IDs; overrides allowlist and can include harmful effects. |

Write decimal values such as `1.0` in global TOML numeric arrays: validation expects Doubles. Five-element arrays always use stage order 0, 1, 2, 3, 4.

| Stage | Accumulation | Hunger | Use duration | Nutrition | Beneficial food effects |
|---|---|---:|---:|---:|---|
| 0 | 0 ≤ M < 5 | 100% | ×1 | 100% | Yes |
| 1 | 5 ≤ M < 10 | 75% | ×1.5 | 100% | Yes |
| 2 | 10 ≤ M < 15 | 50% | ×2 | 100% | No |
| 3 | 15 ≤ M < 20 | 25% | ×2.5 | 50% | No |
| 4 | M ≥ 20 | 25% | ×3 | 0% | No |

Filters apply only within eating context. They do not remove existing unrelated effects or block separate mod rewards. Changing use duration does not guarantee that an animation mod with independently configured timing slows its animation; test those integrations.

The exact restoration calculation for originally nutritious food is:

> G = min(20, max(round(H × p_H), ⌈H_min⌉))
> F′ = min(20, F + G)
> S′ = min(F′, S_old + S × p_S)

F is current integer food level, p_H/p_S are stage hunger/saturation multipliers, and H_min is minimum hunger. H ≤ 0 gives zero hunger grant; the minimum does not create hunger from a non-nutritious portion. Nonnegative half values round upward. Recovery eligibility uses **F′ − F**, not the grant before capacity clamping.

Disabling global saturation penalties changes default p_S to 1. An explicit individual `saturation_multipliers` array overrides those defaults even with `enable_saturation_penalty=false`. Remove that override or set `[1,1,1,1,1]` to disable penalties for that profile.

### 2.5. Formula location and expression language

Every datapack must place the formula resource at **`data/ldiediet/formulas.json`**. `data/mypack/formulas.json` is not read as a replacement. The highest-priority file is loaded as a whole; files do not merge field by field. Missing fields in the selected file receive built-in defaults.

Six expression keys are supported: `cci_formula`, `monotony_increase`, `catalyst_formula`, `debuff_factor`, `recovery_formula`, and `nutrient_add`. Names and invocation phases are fixed. Expressions and parameters are editable; adding `my_formula` does not create a new processing phase.

| Feature | Syntax |
|---|---|
| Arithmetic | `+`, `-`, `*`, `/`, parentheses |
| Power | `x^2` or `pow(x, 2)` |
| Square root / absolute value | `sqrt(x)`, `abs(x)` |
| Natural logarithm | `ln(x)`, `log(x)`; both natural |
| Min / max | `min(a, b)`, `max(a, b)`; exactly two arguments |
| Constants | `pi`, `e` |
| Numbers | `0.25`, `1e-3`; decimal point |

No JavaScript, comparisons, `if`, ternary/logical operators, `exp`, `sin`, `floor`, or custom functions. Use `e^x` for an exponential and `min(upper, max(lower, x))` for clamping. Variable names are case-sensitive: `M` exists; arbitrary `m` does not.

The mathematical symbols below are for reading. JSON expressions use the exact variable names and ordinary `*`, `/`, `^`.

### 2.6. Habituation growth in full

Define:

> c = min(4, max(1, C))
> Q = H + S
> β = b × B / 10
> F(Q) = 0.85 + 0.30 × Q / (Q + 10)

C is the eaten food's CCI, b is global `base_increase`, and B is profile `base_monotony`. An explicit `food_value` replaces H + S. Default raw growth:

> g(Q, C) = 5 × β × F(Q) / (125/9 − 3 × (c − 1))

Effective growth and new state:

> ΔM = 0 if Q ≤ 0 or g ≤ 0
> ΔM = max(g, μ) if Q > 0 and g > 0
> M′ = min(M_max, M + ΔM)

μ is TOML `Monotony.minimum_per_meal`; M_max is `monotony_cap`. State is stored as a float, so boundaries are reached through sequential float-rounded steps.

Exact expression:

```json
{
  "monotony_increase": "base_increase * 5 * (0.85 + 0.30 * (hunger + saturation) / (hunger + saturation + 10)) / (125 / 9 - 3 * (min(4, max(1, cci)) - 1))"
}
```

- F(Q) smoothly ranges from 0.85 toward 1.15. Huge food values cannot produce unlimited growth, while weak food does not require thousands of portions solely because of its nutritional value.
- Higher CCI decreases the denominator, making complex food reach stages sooner.
- `base_increase` scales growth globally; `base_monotony` scales individual food.
- The floor μ applies **after** evaluation. If reducing coefficients leaves portion counts unchanged, check whether growth is capped from below at 0.25.
- `monotony_cap` limits accumulated habituation, not recovery portions directly.

With Q = 10, b = 1, B = 10, and thresholds 5/10/15/20:

| CCI | Approximate growth / portion | Stage 1 | Stage 2 | Stage 3 | Stage 4 |
|---|---:|---:|---:|---:|---:|
| 1 | 0.3600 | 14 | 28 | 42 | 56 |
| 2 | 0.4592 | 11 | 22 | 33 | 44 |
| 3 | 0.6338 | 8 | 16 | 24 | 32 |
| 4 | 1.0227 | 5 | 10 | 15 | 20 |

These are uninterrupted repetitions starting at M = 0, without recovery or external hooks. Counts are not universal for all food at a given CCI: Q, profile scaling, and custom expressions matter.

All growth variables:

| Variable | Value |
|---|---|
| `base_increase` | Already b × B / 10, not just the global file value. |
| `hunger` | Original hunger; explicit `food_value` replaces it. |
| `saturation` | Original absolute saturation; 0 when `food_value` is explicit. |
| `cci` | This portion's CCI. |
| `M`, `monotony` | Same value: accumulation before the portion. Allows state-dependent growth. |
| `k_norm` | Configurable constant; unused by the current default expression. |

To ignore nutritional value and use linear CCI-dependent growth:

```json
{
  "monotony_increase": "base_increase * (0.3 + 0.2 * (min(4, max(1, cci)) - 1))"
}
```

This is an alternative balance, not a default. If growth depends on M, forecast counts require simulation rather than simply ceiling(threshold / gain).

### 2.7. Recovery: two foods, three expressions

**A** is the other food just eaten; **B** is the food being recovered. C_A/C_B are their CCI values, M_B is B's current accumulation, and j is the next qualifying other-portion number for B.

One A portion separately updates **all recorded foods with positive accumulation**, except A's own FoodKey. Different foods with identical CCI still recover each other: “different” means a different FoodKey, not different CCI.

Default expressions clamp CCI:

> a = min(4, max(1, C_A))
> b = min(4, max(1, C_B))

**Step 1 — relative recovery strength, `catalyst_formula`:**

> K(A, B) = 1 + 0.25 × (a − b)

```json
{
  "catalyst_formula": "1 + 0.25 * (min(4, max(1, cci)) - min(4, max(1, cci_b)))"
}
```

| Target B CCI ↓ / eaten A CCI → | 1 | 2 | 3 | 4 |
|---|---:|---:|---:|---:|
| 1 | 1.00 | 1.25 | 1.50 | 1.75 |
| 2 | 0.75 | 1.00 | 1.25 | 1.50 |
| 3 | 0.50 | 0.75 | 1.00 | 1.25 |
| 4 | 0.25 | 0.50 | 0.75 | 1.00 |

Each CCI point changes strength by 25% of the **equal-CCI baseline**, not repeated multiplication by 1.25. CCI 2 therefore helps CCI 1 at 125%, whereas CCI 1 helps CCI 2 at 75%. Recovery portion counts are not exactly inversely proportional because state and acceleration change.

**Step 2 — target recoverability, `debuff_factor`:**

> t = (b − 1) / 3
> D(M_B, C_B) = (0.125 + 0.0056 × M_B²) / ((1 − t)/0.94 + t/2.4)

```json
{
  "debuff_factor": "(0.125 + 0.0056 * monotony_b^2) / ((1 - (min(4, max(1, cci)) - 1) / 3) / 0.94 + ((min(4, max(1, cci)) - 1) / 3) / 2.4)"
}
```

At this stage `cci` means **B**, not A. The inverse denominator multiplier is 0.94 for CCI 1 and 2.4 for CCI 4; inverse values interpolate between them. The numerator's positive constant permits reaching zero rather than approaching it forever. Its quadratic term accelerates removal of high accumulation; recovery weakens near zero.

**Step 3 — acceleration after sustained variety and new state, `recovery_formula`:**

> A(j) = 1 + α × max(0, j − d)
> M_B′ = max(0, M_B − r × K(A,B) × D(M_B,C_B) × A(j))

r is `base_recovery`, d is `recovery_delay`, and α is `recovery_acceleration`.

```json
{
  "recovery_formula": "max(0, monotony_b - base_recovery * catalyst_a * debuff_factor * (1 + recovery_acceleration * max(0, recovery_meals - recovery_delay)))"
}
```

Defaults are d = 20 and α = 0.08. Portions 1…20 use ×1; the 21st uses ×1.08, the 22nd ×1.16, and the 30th ×1.8. Each increment is 8% of the baseline, not compounding. Setting acceleration to 0 disables it; delay 0 starts acceleration on the first qualifying portion.

**`recovery_formula` returns the new accumulation**, not the loss. `"0.2"` would immediately set the state close to zero. To subtract 0.2, write `"max(0, monotony_b - 0.2)"`. The result is additionally clamped to 0…old state, so recovery cannot increase accumulation. A nonpositive catalyst prevents reduction for that record.

First step from M_B = 6, r = 1, without acceleration:

| Target B | Eaten A | Catalyst | First-portion reduction | New accumulation |
|---|---|---:|---:|---:|
| CCI 1 | CCI 1 | 1.00 | 0.3070 | 5.6930 |
| CCI 1 | CCI 2 | 1.25 | 0.3838 | 5.6162 |
| CCI 4 | CCI 1 | 0.25 | 0.1960 | 5.8040 |
| CCI 4 | CCI 4 | 1.00 | 0.7838 | 5.2162 |

The next step uses the **new** M_B. Dividing 6 by the first loss does not give the exact portion count. Switching other foods changes their catalyst on each step.

### 2.8. Recovery eligibility and other-portion counters

By default, another portion must have positive Q and actually restore at least one **whole** hunger point after penalties/capacity limits. Eating in creative at full hunger does not qualify. Fractional restoration settings do not guarantee a whole food-level increase.

```json
{
  "recovery_mode": "active",
  "recovery_requires_hunger": true,
  "recovery_min_hunger_restored": 1,
  "recovery_reset_on_eat": true,
  "recovery_delay": 20,
  "recovery_acceleration": 0.08
}
```

- `recovery_mode` supports only `active`. Waiting, sleeping, and offline time do not reduce accumulation.
- `recovery_requires_hunger: false` allows completed consumption at full hunger; positive Q is still required.
- `recovery_min_hunger_restored` is an integer ≥1, default 1, used only when hunger gain is required. Above 20 it excludes ordinary vanilla portions.
- `recovery_reset_on_eat: true` resets B's other-meal counter when B itself is eaten. `false` retains acceleration between returns to B.
- Full recovery to zero always clears B's counter.
- Counters are separate per FoodKey. Other foods may alternate and need not be unique among themselves; each must differ from the target B.
- Ineligible portions do not advance recovery counters. Excluded food does not participate. API cancellation may also stop a step.

Recovery depends on target CCI, eaten CCI, current accumulation, and other-portion number. After eligibility, default reduction is not scaled by A's nutritional value: Q only gates nutritious portions. Expressions do not receive `hunger` or `saturation` for recovery; use integration hooks for that extra behavior.

### 2.9. Every scalar formula setting

| Setting | Default | Purpose and limits |
|---|---:|---|
| `cci_min` | 1 | Positive lower CCI bound. |
| `cci_max` | 4 | Positive upper bound. Reversed bounds are swapped with diagnostics. |
| `base_increase` | 1 | Global growth scale, before profile scaling and the minimum floor. |
| `base_recovery` | 1 | Recovery scale if the expression references it; use a positive value for normal recovery. |
| `monotony_cap` | 24 | Positive accumulation cap, at most float maximum. Higher stage thresholds cannot be reached by eating. |
| `recovery_delay` | 20 | Nonnegative integer: other-portion count before extra acceleration. |
| `recovery_acceleration` | 0.08 | Nonnegative linear multiplier increment for each subsequent qualifying portion. |
| `recovery_requires_hunger` | true | Require actual hunger gain. |
| `recovery_min_hunger_restored` | 1 | Minimum whole hunger gain; integer ≥1. |
| `recovery_reset_on_eat` | true | Reset a product's other-meal counter when it is eaten. |
| `recovery_mode` | active | Only supported mode. |
| `k_norm` | 10 | Positive growth constant, **unused** by the current default expression. |
| `base_scale` | 10 | Positive recovery constant, **unused** by current defaults. |
| `offset_cat` | 1 | Available recovery constant. The default catalyst uses literal `1` instead. |
| `d_cci` | 1.5 | Available recovery constant, unused by current default expressions. |
| `d_monotony` | 0.02 | Available recovery constant, unused by current default expressions. |

Coefficients written **literally in expression strings** must be changed in those strings. Setting `k_norm` does not replace the literal `+ 10` in default growth. Replace that fragment with `+ k_norm` to parameterize it. Similarly, `offset_cat` affects a custom catalyst beginning `offset_cat + 0.25 * (...)`.

General CCI bounds control calculation, but default growth/recovery strings independently clamp CCI to **1…4**. For a 1…8 balance, change those expressions, denominators, and coefficients as well. Setting `cci_max: 8` alone makes values above 4 behave as 4 in default expressions.

### 2.10. Recovery and nutrition variable reference

| Recovery variable | Meaning |
|---|---|
| `monotony_b` | Target B's current accumulation. |
| `cci_a` | Eaten A's CCI at every step. |
| `cci_b` | Target B's CCI at every step. |
| `cci` | **A in `catalyst_formula`; B in `debuff_factor` and `recovery_formula`.** Explicit `cci_a`/`cci_b` are clearer for custom expressions. |
| `recovery_meals` | Current qualifying other-portion number, starting at 1. |
| `recovery_delay`, `recovery_acceleration` | Acceleration settings. |
| `base_recovery`, `offset_cat`, `d_cci`, `base_scale`, `d_monotony` | Constants from the formula file. |
| `catalyst_a` | Catalyst result, available only in factor and recovery expressions. |
| `debuff_factor` | Factor result, available only in `recovery_formula`. |

`nutrient_add` receives only:

| Variable | Meaning |
|---|---|
| `base_n` | Q × this scale's weight × active group nutrition multipliers. |
| `stage_multiplier` | This portion's stage nutrition multiplier. |
| `M`, `monotony` | Accumulation **before** the portion. |

> ΔN = base_n × stage_multiplier

```json
{"nutrient_add": "base_n * stage_multiplier"}
```

This expression receives no CCI, hunger, scale name, or current scale fill. Use food weights or `NutrientPre` for scale-specific behavior. Unlike recovery, it returns an **increment**, not the final stored value.

### 2.11. Complete copyable default formula file

`data/ldiediet/formulas.json`:

```json
{
  "recovery_mode": "active",
  "cci_min": 1.0,
  "cci_max": 4.0,
  "base_increase": 1.0,
  "base_recovery": 1.0,
  "monotony_cap": 24.0,
  "recovery_delay": 20,
  "recovery_acceleration": 0.08,
  "recovery_requires_hunger": true,
  "recovery_min_hunger_restored": 1,
  "recovery_reset_on_eat": true,
  "k_norm": 10.0,
  "base_scale": 10.0,
  "offset_cat": 1.0,
  "d_cci": 1.5,
  "d_monotony": 0.02,
  "cci_formula": "average_ingredients_cci + 0.4 * sqrt(max(0, ingredients_count - 1)) + stages_weight",
  "monotony_increase": "base_increase * 5 * (0.85 + 0.30 * (hunger + saturation) / (hunger + saturation + 10)) / (125 / 9 - 3 * (min(4, max(1, cci)) - 1))",
  "catalyst_formula": "1 + 0.25 * (min(4, max(1, cci)) - min(4, max(1, cci_b)))",
  "debuff_factor": "(0.125 + 0.0056 * monotony_b^2) / ((1 - (min(4, max(1, cci)) - 1) / 3) / 0.94 + ((min(4, max(1, cci)) - 1) / 3) / 2.4)",
  "recovery_formula": "max(0, monotony_b - base_recovery * catalyst_a * debuff_factor * (1 + recovery_acceleration * max(0, recovery_meals - recovery_delay)))",
  "nutrient_add": "base_n * stage_multiplier"
}
```

For slower recovery, start by reducing `base_recovery`. Increase the catalyst's 0.25 to distinguish complex other foods more strongly, but test every pair: excessive coefficients make catalysts nonpositive. To shorten the near-zero tail, increase the factor's constant 0.125 or acceleration. To change growth, edit `base_increase`, profile `base_monotony`, or the growth expression, not recovery settings.

The cap controls maximum accumulated depth. Delay/acceleration control returning after sustained variety. With no passive recovery, you cannot guarantee recovery after exactly seven game days: the player may eat no eligible portions in that time. Balance is expressed through portions and eligibility.

### 2.12. Item, tag, and NBT-specific stages

`data/mypack/food_stage_profiles/special_fruit.json`:

```json
{
  "priority": 100,
  "items": ["minecraft:apple", "#minecraft:fox_food"],
  "nbt": "{Flavor:'special'}",
  "stages": {
    "thresholds": [6, 12, 18, 24],
    "hunger_multipliers": [1, 0.8, 0.6, 0.4, 0.2],
    "saturation_multipliers": [1, 0.7, 0.5, 0.3, 0.1],
    "chewing_multipliers": [1, 1.2, 1.5, 2, 2.5],
    "nutrient_multipliers": [1, 1, 0.8, 0.5, 0],
    "allow_food_buffs": [true, true, false, false, false],
    "minimum_hunger_restored": 0,
    "allowed_food_effects": ["minecraft:night_vision"],
    "blocked_food_effects": ["minecraft:poison"]
  }
}
```

| Field | Behavior |
|---|---|
| `priority` | Higher first; ties ordered by definition ID. |
| `items` | Item IDs or `#` item tags; any match suffices. |
| `nbt` | Optional partial SNBT, in addition to item/tag matching. |
| `stages` | Selected policy fields. Omitted fields inherit global settings. |
| `thresholds` | Four strictly increasing positive integer accumulation thresholds. |
| `hunger_multipliers` | Five 0…1 values. |
| `saturation_multipliers` | Five independent 0…1 values. Explicit arrays replace defaults, including global disabled-penalty defaults. |
| `chewing_multipliers` | Five 1…100 values. |
| `nutrient_multipliers` | Five 0…100 values. |
| `allow_food_buffs` | Five booleans. |
| `minimum_hunger_restored` | 0…20. Its name differs from global TOML `min_hunger_restored`. |
| `allowed_food_effects`, `blocked_food_effects` | Replace global lists for this food. Empty lists clear inherited lists. Blocklist still wins. |

Only the **first matching profile** applies. Profiles do not stack. A partial high-priority profile inherits global settings, not the next matching profile's fields.

Stage profiles do **not** create a new FoodKey. Independent NBT counters require `food_profiles.nbt_filters`. Combine them when needed: food profiles control identity/CCI/composition, stage profiles control thresholds and penalties.

### 2.13. Complete exclusions and fixed stages

Use `is_exclusion: true` or `data/ldiediet/tags/items/excluded_foods.json`:

```json
{
  "replace": false,
  "values": ["minecraft:golden_apple"]
}
```

`replace: false` appends to other tag definitions; `true` replaces the previously assembled list. Tags affect every variant of an item. Complete exclusion takes priority over fixed stages and shows stage 0.

For fixed stages, create `data/mypack/exclusions/fixed_foods.json`:

```json
{
  "overrides": {
    "minecraft:rotten_flesh": 3
  }
}
```

This is **not complete exclusion**. The item is processed, but its effective stage is fixed instead of following accumulation. Values are 0…4. Prefer stage profiles for special food that should still progress; fixed stages suit permanently penalized or permanently fresh items.

### 2.14. Validate formulas and forecasts

```text
/reload
/ldiediet validate
/ldiediet get_monotony @s minecraft:apple
/ldiediet set_monotony @s minecraft:apple 6
```

In survival, reduce hunger, eat another nutritious food, and inspect the new accumulation. For full-hunger creative tests, explicitly disable the hunger requirement. Test equal CCI, extreme 1→4/4→1 pairs, weak foods, NBT variants, and the acceleration boundary.

`validate` checks field/variable names, types, ranges, syntax, domain errors, and division by zero, including intermediate calculations. It samples seven CCI values between configured bounds, several food values, recipe sizes, and states. Recovery simulations start at 6, stage thresholds, and the cap. Unreachable stages or no full recovery within 4096 portions produce warnings. These are bounded numerical checks, not proof for every possible expression.

Malformed expression syntax falls back to the built-in expression with diagnostics. Recovery evaluation failure skips reduction rather than clearing the food. Fix diagnostics instead of treating fallback as configuration.

The journal forecasts portions using the same stepwise core with current profile, CCI, and growth floor. Beyond 4096 steps it shows `>4096`; unreachable boundaries show `∞`. Forecasting does not invoke third-party `Pre` hooks for imaginary future meals, so world-dependent scripts and cancellation cannot be guaranteed by the displayed count.

## 3. Food groups

### 3.1. What a group represents

A group combines food membership with consumption-based rewards. It counts qualifying portions in recent history, not protein/vegetable percentages. Food may belong to multiple groups and replenish multiple scales simultaneously.

Groups can require repeated or unique foods, consecutive or interrupted sequences, independent reward slots, climate/biome/achievement conditions, and secret discovery. `nutrients` does not assign membership; `items` does.

### 3.2. Complete group example

`data/mypack/food_groups/breakfast.json` creates **`mypack:breakfast`**:

```json
{
  "name": "Breakfast",
  "name_key": "mypack.group.breakfast",
  "active_effect_text": "Well fed",
  "active_effect_text_key": "mypack.group.breakfast.active",
  "icon": "minecraft:bread",
  "order": 10,
  "tooltip_icon": "● ",
  "tooltip_icon_color": "gold",
  "hide_activation_message": false,
  "secret": false,
  "items": ["minecraft:bread", "minecraft:apple", "#forge:vegetables"],
  "threshold": 3,
  "require_unique": true,
  "flag": "-c",
  "advancement": "mypack:breakfast",
  "effects": [
    {
      "duration_type": "fixed",
      "duration_ticks": 1200,
      "effect_type": "attribute",
      "effect_id": "minecraft:generic.movement_speed",
      "operation": "multiply_base",
      "value": 0.1
    },
    {
      "duration_type": "fixed",
      "duration_ticks": 1200,
      "effect_type": "mob_effect",
      "effect_id": "minecraft:haste",
      "amplifier": 0
    }
  ]
}
```

Three distinct qualifying foods consecutively grant speed and Haste for 1200 ticks, one minute at 20 TPS. Define the achievement separately at `data/mypack/advancements/breakfast.json`, or omit `advancement` if not needed.

### 3.3. Every group field

| Field | Meaning, defaults, and options |
|---|---|
| `name` | Literal fallback name; defaults to the ID path. |
| `name_key` | Explicit name translation key. Without it, automatic `ldiediet.group.<namespace>.<path>.name` is available. |
| `active_effect_text` | Fallback active reward text; default empty. |
| `active_effect_text_key` | Explicit translation key; otherwise automatic `ldiediet.group.<namespace>.<path>.active`. |
| `icon` | **Item ID** for the card, e.g. `minecraft:bread`. Otherwise uses achievement icon or a suitable item. Not a PNG path. |
| `order` | Ascending card order, default 0. Does not control reward calculation priority. |
| `tooltip_icon` | Text symbol for item membership, default `● `. Custom symbols must exist in the client font. |
| `tooltip_icon_color` | Minecraft named color, default `aqua`; e.g. `gold`, `green`, `red`, `light_purple`, `gray`. Not `#RRGGBB`. |
| `hide_activation_message` | `true` hides activation notification; default `false`. Does not disable rewards or hide the card. |
| `secret` | `true` conceals real name, icon, progress, and item membership until discovered; default `false`. |
| `items` | Item IDs and/or `#` item tags. Use a nonempty list for new groups. |
| `threshold` | Positive required portion/unique-FoodKey count. Practical maximum 100 due to history capacity. |
| `require_unique` | `true` by default: distinct FoodKeys. `false`: every matching portion, including repeats. |
| `flag` | `-o` by default: intervening food allowed. `-c`: qualifying portions must be consecutive. |
| `advancement` | Optional separately defined achievement ID; grants remaining criteria on activation. |
| `effects` | Independent reward slots, each with its own type, conditions, and lifetime. Secret discovery requires at least one slot to actually become active. |

Slashes in group paths become dots in automatic translation keys: `mypack:daily/breakfast` uses `ldiediet.group.mypack.daily.breakfast.name`.

`items` does not accept item SNBT. Variants have separate history through food profiles, but membership remains item/tag-based. Membership refreshes after reload; direct and tagged inclusion of the same item does not double membership.

Custom tag `data/mypack/tags/items/breakfast_foods.json`:

```json
{
  "replace": false,
  "values": ["minecraft:bread", "minecraft:apple", "minecraft:carrot"]
}
```

Then use `"items": ["#mypack:breakfast_foods"]`. This separates catalog edits from rewards. In an item tag, optional mod items use the standard object `{"id":"othermod:food","required":false}`. Do not put such objects in a group's `items`, which accepts strings.

### 3.4. History, uniqueness, repeat activations

These settings belong to `[General]`:

```toml
reward_history_length = 30
reset_history_on_death = true
reset_groups_on_death = true
```

| Setting | Behavior |
|---|---|
| `reward_history_length` | Last 1…100 portions for group calculations; default 30. Separate from lifetime journal counts. |
| `reset_history_on_death` | Clears group history on death, default `true`; does not clear habituation, lifetime counts, or discoveries. |
| `reset_groups_on_death` | Ends group rewards/activation state on death, default `true`. Until-death modes end regardless of this switch. |

| `flag` | Unique? | Three-portion example |
|---|---|---|
| `-c` | false | Bread, bread, bread qualifies if all are members. |
| `-c` | true | Bread, apple, carrot qualifies; bread, bread, apple counts only two kinds. |
| `-o` | false | Three matching portions within available history; other food may intervene. |
| `-o` | true | Three different matching FoodKeys within history, allowing interruptions. |

Nonmembers break a consecutive sequence. In open sequences they still occupy history and can eventually push matching entries out. Uniqueness recognizes configured variants, not every arbitrary NBT difference.

A threshold above history capacity is unreachable. A 40-portion group needs history ≥40; a 40-unique-food group also needs enough available FoodKeys. The validator finds empty tags and insufficient ordinary items but cannot fully prove all custom variant availability.

Live activations prevent automatic repeated rewards every meal. Once activation ends or is canceled, its consumed history window closes. Reactivation requires new matching meals, not reuse of old entries. Each group has its own history cursor.

With `reset_groups_on_death: false`, fixed rewards may retain remaining activation state, but `until_death` and `until_death_intermittent` still end. History-intermittent rewards also depend on their quota remaining in history; separately clearing history may end them.

### 3.5. Reward types and every slot field

| Slot field | Behavior |
|---|---|
| `duration_type` | Lifetime mode, default `fixed`; modes below. |
| `effect_type` | Explicit reward type; avoid legacy inference for new data. |
| `effect_id` | Status, attribute, nutrition key, or overlay ID according to type. |
| `value` | Numeric attribute/health/nutrition multiplier amount, default 0. |
| `operation` | Attribute/health operation: `addition` by default, `multiply_base`, `multiply_total`. |
| `amplifier` | Status level minus one: 0 = I, 1 = II. |
| `duration_ticks` | Fixed lifetime in ticks, default 0; use a positive value for a working timed reward. |
| `interval_active_ticks` | Intermittent active phase `[min,max]` or `[ticks]`. |
| `interval_pause_ticks` | Corresponding pause interval. |
| `requires_active_groups` | All listed live group activations must exist. |
| `cancel_on_groups` | Activation of any listed group cancels **this slot**. |
| `duration_multipliers` | Map of group ID to positive lifetime multiplier when present at start. |
| `conditions` | World/integration conditions described in 3.7. |
| `continuous_conditions` | Rechecks conditions/dependencies while running; default `false`. |

| `effect_type` | Configuration example | Meaning |
|---|---|---|
| `mob_effect` | `effect_id: "minecraft:haste"`, `amplifier: 0` | Haste I or another registered effect. |
| `attribute` | `effect_id: "minecraft:generic.armor"`, `value: 2`, `operation: "addition"` | +2 armor; other operations give percentage changes. |
| `health` | `effect_id: "health"`, `value: 2` | Compatible shorthand for maximum health; two points are a heart, not immediate healing. |
| `nutrient_multiplier` | `effect_id: "mypack:protein"`, `value: 0.5` | +50% future gain for that scale. Use `all` or `*` for all scales. |
| `overlay` | `effect_id: "mypack:warm_view"` | Postprocessing resource `assets/mypack/shaders/post/warm_view.json`, delivered through a resource pack. |

Nutrition multipliers use max(0, 1 + value). Thus 0.5 gives ×1.5, −0.5 gives ×0.5, and −1 gives ×0. Multiple applicable sources multiply; two 0.5 rewards give ×2.25. They do not add stored nutrition or speed habituation recovery.

Legacy `effect_type: "diet"` maps to `nutrient_multiplier`; its `diet:` effect-ID prefix is removed. Legacy `type` may substitute for `duration_type`; a legacy overlay's string `value` may carry its resource ID. Prefer explicit modern fields and numeric values for new packs.

One group can combine attributes, statuses, and nutrition multipliers. Slots are independent. Canceling one need not cancel others. Status levels are not artificially added into level III from two level-I sources; the active vanilla result is retained. Attribute contributions stack as in 1.3, with source details in the journal.

### 3.6. Lifetimes, dependencies, startup conditions

| Mode | Behavior | Required configuration |
|---|---|---|
| `fixed` | Runs for a set number of ticks after starting. | Positive `duration_ticks`. |
| `until_death` | Continues until death or explicit cancellation. | Optional conditions/dependencies. Fixed duration does not define its lifetime. |
| `until_death_intermittent` | Alternates active phases and pauses until death. | Active and pause intervals. |
| `history_intermittent` | Alternates while history still meets the quota; ends when the quota is lost. | Both intervals and a reachable group quota. |

20 ticks are one second at 20 TPS. `[200,400]` chooses a phase between those bounds; `[200]` makes it fixed. Use positive intervals with minimum ≤ maximum.

Haste I for 10…20 seconds with five-second pauses:

```json
{
  "duration_type": "history_intermittent",
  "effect_type": "mob_effect",
  "effect_id": "minecraft:haste",
  "amplifier": 0,
  "interval_active_ticks": [200, 400],
  "interval_pause_ticks": [100, 100]
}
```

Dependencies check a **live activation**, not merely known/discovered membership or necessarily another slot's currently active phase. Waiting/intermittent activations may count as live. Use custom predicates/API for more specific external requirements.

Example dependencies:

```json
{
  "duration_type": "fixed",
  "duration_ticks": 1200,
  "effect_type": "attribute",
  "effect_id": "minecraft:generic.movement_speed",
  "operation": "multiply_base",
  "value": 0.1,
  "requires_active_groups": ["mypack:balanced_breakfast"],
  "cancel_on_groups": ["mypack:heavy_dinner"],
  "duration_multipliers": {"mypack:fruit_day": 1.5},
  "continuous_conditions": true
}
```

Use full IDs. Short group references resolve relative to their own group's namespace. Duration multipliers are captured at startup, not recalculated retroactively.

With `continuous_conditions: false`, conditions gate startup. A fixed slot that cannot start expires without waiting. Waiting-capable slots may start later after periodic checks. With continuous conditions enabled, a running reward stops applying when requirements fail but its timer **continues**. It can resume only while its activation remains live; this does not freeze time.

Avoid mandatory circular startup with no initial path: A requires B and B requires A. The validator warns about possible cycles. Evaluate **slots** separately: another independent reward can provide a startup path, so not every cycle is a complete deadlock. Original and derived packs retain the original mutual links.

### 3.7. Every world condition

Groups use `effects[].conditions`; nutrition quotas use `world_conditions`. Their shared format is:

```json
{
  "logic": "AND",
  "biomes": ["minecraft:plains", "#minecraft:is_forest"],
  "dimensions": ["minecraft:overworld"],
  "time": {"min": 13000, "max": 23000},
  "weather": "clear",
  "temperature": {"min": 0.2, "max": 1.0},
  "advancements": ["minecraft:story/root"],
  "custom": [
    {
      "id": "mypack:quest_complete",
      "args": {"quest": "healthy_diet"},
      "description_key": "mypack.condition.healthy_diet"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `logic` | Default `AND` requires every configured category and custom condition; `OR` needs any. An empty condition object passes. |
| `biomes` | Biome IDs or `#` biome tags; any listed match suffices. |
| `dimensions` | Dimension IDs, any match; no dimension tags here. |
| `time.min`, `time.max` | Inclusive day ticks, 0…23999. Defaults 0/23999. Minimum above maximum wraps across midnight. |
| `weather` | `clear`, `rain` without thunder, or `thunder`. Tests world weather, not whether raindrops hit a sheltered player. |
| `temperature.min`, `temperature.max` | Inclusive base-biome-temperature bounds; not Celsius or body temperature. |
| `cold_sweat_state` | `freezing`, `cold`, `normal`, `hot`, `overheating`. Requires Cold Sweat; absent integration fails. |
| `season` | `spring`, `summer`, `autumn`, `winter`. Requires Serene Seasons; absent integration fails. |
| `advancements` | All listed achievements must be complete. This list is one category under outer AND/OR. |
| `custom` | Each entry is a separate registered predicate. |
| `custom[].id` | Handler registered through `IntegrationApi.registerCondition`. |
| `custom[].args` | Arbitrary JSON argument object; defaults empty. |
| `custom[].description_key` | Translated journal description; does not implement the test itself. |

For winter **or** cold body state, use OR with `season` and `cold_sweat_state`. For location, time, **and** quest completion, use AND. Arbitrary nested Boolean trees are not part of this format; implement complex combinations in one custom predicate.

Missing/failing predicates return false. Validation reports missing handlers, biomes, dimensions, or achievements. Use continuous conditions for group rewards that must remain biome-restricted; nutrition quotas already recheck world conditions during refresh.

### 3.8. Secret groups, achievements, and icons

With `secret: true`, a concealed card shows enchanting glyphs and unknown progress; item tooltips do not reveal membership. History and rewards still work.

A group unlocks when one reward slot **actually becomes active**, not simply when portion count reaches the threshold. If all slots are waiting, it remains concealed. Discovery survives death, reward expiry, and relogging. API can separately reveal a group for a quest without granting its reward.

Achievement for the example, `data/mypack/advancements/breakfast.json`:

```json
{
  "display": {
    "icon": {"item": "minecraft:bread"},
    "title": {"translate": "mypack.advancement.breakfast.title"},
    "description": {"translate": "mypack.advancement.breakfast.description"},
    "background": "minecraft:textures/gui/advancements/backgrounds/stone.png",
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": false,
    "hidden": false
  },
  "criteria": {"diet_reward": {"trigger": "minecraft:impossible"}}
}
```

An `impossible` criterion lets group logic grant the achievement without an independent vanilla trigger. Achievement JSON does not create the group. For a custom achievement tab, provide normal root/parent relationships.

Group icons use item IDs, e.g. `minecraft:apple`. A resource pack can change that item's appearance, generally affecting it elsewhere too. Scale icons use direct texture paths and can be changed independently of items.

### 3.9. Use cases and verification

| Design | Implementation |
|---|---|
| Eat any five vegetables | Vegetable tag, threshold 5, nonunique, `-o`. |
| Try seven fish types | Fish list/tag, threshold 7, unique. |
| Three spicy meals consecutively | `-c`, repetition policy as desired. |
| Warm soup in winter | Soup membership and winter condition; continuous checking if needed. |
| Secret End cuisine | Secret group with End foods and an effective reward; optionally dimension-restricted. |
| Meat group cancels vegetarian reward | Vegetarian slot lists meat group in `cancel_on_groups`. This reacts to group activation, not any single meat item; use meal hooks for the latter. |
| Breakfast boosts fruit nutrition | `nutrient_multiplier` targeting `fruits`, value 0.5. |
| Quest-dependent reward | Registered custom condition or `GroupAttempt`. |
| Different rewards at 80% and 100% group progress | Groups have no arbitrary threshold-tier array. Use different groups/quotas plus cancellation/dependencies, or API. Percentage tiers are directly available on **nutrition scales**. |

```text
/reload
/ldiediet validate
/ldiediet group @s mypack:breakfast
/ldiediet group @s mypack:breakfast grant
/ldiediet clear_history @s
```

The inspection command explains group state. `grant` attempts rewards with normal conditions but does not simulate meals or prove quota reachability. Test actual sequences, expiry, reactivation, canceling groups, death, and discovery in a world copy.

## 4. Translations, KubeJS, and API

### 4.1. Translations and resource packs

`assets/mypack/lang/ru_ru.json` contains Russian localization values:

```json
{
  "mypack.nutrition.protein": "Белки",
  "mypack.rule.balanced": "Сбалансированный рацион",
  "mypack.group.breakfast": "Завтрак",
  "mypack.group.breakfast.active": "Сыт и бодр",
  "mypack.condition.healthy_diet": "Завершён квест «Здоровое питание»",
  "mypack.advancement.breakfast.title": "Сытный завтрак",
  "mypack.advancement.breakfast.description": "Попробуйте три разных продукта для завтрака"
}
```

`assets/mypack/lang/en_us.json`:

```json
{
  "mypack.nutrition.protein": "Protein",
  "mypack.rule.balanced": "Balanced diet",
  "mypack.group.breakfast": "Breakfast",
  "mypack.group.breakfast.active": "Well fed",
  "mypack.condition.healthy_diet": "Complete the Healthy Diet quest",
  "mypack.advancement.breakfast.title": "A hearty breakfast",
  "mypack.advancement.breakfast.description": "Try three different breakfast foods"
}
```

Add other Minecraft language files as needed. Explicit translation keys connect server definitions to client language resources. Literal names/text remain readable fallbacks. Achievements use standard Minecraft `{"translate":"..."}` components.

Groups without explicit keys can use automatic names:

```json
{
  "ldiediet.group.mypack.breakfast.name": "Завтрак",
  "ldiediet.group.mypack.breakfast.active": "Сыт и бодр"
}
```

A key beginning `ldiediet` can live in `assets/mypack/lang/ru_ru.json`: active language files merge. Exact key text and resource priority matter.

Override interface text through existing `ldiediet.journal.*`, `ldiediet.tooltip.*`, `ldiediet.condition.*`, and `ldiediet.actionbar.*` keys. The title is `ldiediet.journal.title`. Preserve the original number/order of formatting placeholders such as `%s`. Use Latin letters for `ldiediet.journal.secret_name`, rendered with enchanting font `minecraft:alt`.

```toml
[UI]
show_detailed_tooltip = true
show_stage_actionbar = true
```

Client `show_detailed_tooltip` controls food-stage/nutrition details; `show_stage_actionbar` controls stage notifications. Neither changes server balance. Test both languages, secret groups, and long names.

### 4.2. When scripting is needed

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

### 4.3. Every player-state API method

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
| `consume(player, stack, hunger, saturation, callback)` | Runs an external portion; details in 4.6. |
| `reloadRecipeAdapters(player)` | Refreshes recipe indexing without overwriting saved CCI. |

Numbers must be finite. Unknown IDs, empty stacks, invalid stages/counters, and calls outside the server thread throw exceptions. Setting accumulation/nutrition does not record a meal or increment consumption count. Use `consume` for an actual feeding mechanic.

### 4.4. Forge events: cancellation, modification, observation

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

### 4.5. External predicates and quests

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

### 4.6. External feeding: blocks, machines, abilities

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

### 4.7. Machine and external recipes for CCI

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

### 4.8. Validation and distribution

JSON schemas are in `docs/schemas`:

| Schema | Data |
|---|---|
| `nutrition-scale.schema.json` | Scales and thresholds. |
| `nutrition-rule.schema.json` | Compound quotas. |
| `food-profile.schema.json` | One datapack item profile. |
| `food-config.schema.json` | Configuration wrapper with `overrides`. |
| `food-stage-profile.schema.json` | Individual stage policy. |
| `food-group.schema.json` | Groups and reward slots. |
| `world-condition.schema.json` | World/custom predicates. |
| `formulas.schema.json` | Formula fields; expressions also need in-game validation. |

Use `vscode-settings.example.json` for editor association. `$schema` helps the editor, not gameplay. Schemas validate structure, not registry presence or balance reachability. Run validation with actual installed mods.

1. Load the datapack, run `/reload` and `/ldiediet validate`, and read the complete log if chat truncates diagnostics.
2. Test below/at/above each boundary, tier replacement at 100%, natural decay, and death.
3. Test real meals, equal/extreme CCI pairs, custom/empty profiles, and NBT variants.
4. Test group sequences, interruptions, history limits, reactivation, secrets, and waiting conditions.
5. Test Pre cancellation, no double meals, registrations after restart, and behavior without optional external mods.
6. Check both languages and a separate multiplayer client; loading server JSON does not install custom client images.

Ship server JSON configuration and a TOML default template through the appropriate modpack directories. Deliver datapacks to worlds with your selected pack-loading method. Datapack ZIPs may be published independently. Custom resource packs must reach clients. Do not make a personal test world the only way to obtain settings.

Starter examples: `examples/author-datapack`, `examples/author-resourcepack`, `examples/food-config-fragment.json`, `docs/examples/integration_datapack`, and `docs/examples/integration_resources`. They are opt-in and do not activate just because they exist in source. See the [Developer API Reference](docs/INTEGRATION_API.md) for a standalone integration reference.
