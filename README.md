# Aether Realism

A configurable, AAA-inspired visual overhaul for **Minecraft Java 1.21.11** on **Fabric**. It keeps Minecraft's
block-based look and vanilla gameplay, and adds dynamic lighting, animated foliage and water, atmospheric fog,
light scattering, god rays, bloom, color grading, richer weather and ambient particles. It also has a full
in-game settings screen.

Everything is **client-side**. It works in single-player and on any server without server installation, and it
never changes blocks, mobs, survival balance or server gameplay.

> **Status: first release, not yet build-tested.** The code was written against the real 1.21.11 APIs, read from
> the Fabric API, Sodium, Iris, LambDynamicLights and Parchment sources and the vanilla 1.21.11 shaders. The
> environment it was written in could not download Minecraft or Fabric, though. It has not been compiled or
> launched yet. See [Testing status](#testing-status) for what to check first.

---

## Features and how they are implemented

"Implemented" means the feature is real code that changes the rendered game. Limitations are stated plainly.

| Area | Feature | Status | How |
|---|---|---|---|
| Lighting | Dynamic lights (held torches/lanterns/glowstone etc., dropped items, burning mobs, blazes, lit TNT, fireworks, lightning) | Implemented | Raises vanilla's block-light lookup near sources, and re-meshes chunk sections around moving lights. Falloff is continuous with distance. Radius, intensity, max sources, update rate and smoothing are configurable. |
| Lighting | Dynamic light on entities | Implemented | `EntityRenderer#getBlockLightLevel` hook. |
| Lighting | Per-light colors | **Not possible in the vanilla pipeline** | Vanilla terrain has one block-light color. Colored lights need a shader pack. |
| Lighting | Exposure, brightness, contrast, saturation, color styles, split toning, vignette | Implemented | Post-processing chain. |
| Lighting | Bloom (thresholded, soft knee) | Implemented | Bright pass + separable Gaussian. The HUD is unaffected. |
| Lighting | Ambient occlusion | Experimental | Screen-space AO from the depth buffer, on top of vanilla smooth lighting. Requires *Advanced → Experimental features*. |
| Lighting | Cave / night atmosphere | Implemented | Post-processing tone and shadow shaping. |
| Nature | Grass, ferns, leaves, flowers, crops, saplings and underwater plants sway; roots stay anchored | Implemented* | GPU vertex animation in the bundled terrain shader. Plant models are tagged through the Fabric Renderer API. |
| Nature | Separate on/off and strength per category, wind strength, storm response | Implemented* | Re-meshes terrain when changed. Storms re-mesh nearby terrain gradually. |
| Nature | Wind direction/strength changing gradually, gusts | Implemented | On the GPU for vegetation, and in a CPU wind model for particles and snow. |
| Nature | Fixed wind direction, vegetation sway speed slider | **Not available** | The vanilla terrain shader has no input for live parameters. Direction wanders automatically. The *Wind speed* slider affects particles and weather only. |
| Nature | Distance-based animation quality | Implemented* | Animation fades out between 45% and 70% of render distance. |
| Weather | Rain splashes, ripples on water, canopy drips, rain-on-leaves ambience | Implemented | Vanilla particles, distance-limited and frustum-tested. |
| Weather | Storm fog, darker desaturated storms, cinematic storms | Implemented | Fog and post-processing. |
| Weather | Lightning flashes lighting the scene and fog | Implemented | Detected from lightning-bolt entities, with a flicker envelope. |
| Weather | Wind-driven snow, blizzard visibility | Implemented | Extra snowflakes with wind velocity, plus blizzard fog. |
| Weather | Puddles | **Not implemented** | Replaced by a screen-space wet look (darker, glossier surfaces). No puddle decals. |
| Weather | Slanted vanilla rain streaks | **Not implemented** | Vanilla rain rendering is unchanged. |
| Atmosphere | Distance fog shaped by weather, biome (forest/swamp/ocean/mountain/plains), caves, morning mist and valleys | Implemented | Rewrites vanilla's fog uniforms every frame, with smooth easing. Separate per-biome sliders. |
| Atmosphere | Soft horizon (no hard fog wall) | Implemented | Longer render-distance fog gradient. |
| Atmosphere | Height fog pooling in valleys | Implemented | Analytic exponential height fog, reconstructed from the depth buffer. |
| Atmosphere | "Volumetric-style" fog | Implemented (approximation) | Ray-marched drifting 3D noise with sun-phase scattering. **Not true volumetric lighting:** there are no shadowed light volumes. |
| Atmosphere | God rays (sun and moon) | Implemented | Screen-space radial blur of visible sky, occluded by terrain via depth. |
| Atmosphere | Underwater visibility | Implemented | Scales vanilla's water fog. |
| Water | Animated waves | Implemented* | Surface vertices displaced in the terrain shader. Block-boundary vertices are anchored, so no cracks appear. |
| Water | Ripple shimmer, clearer water | Implemented* | Terrain fragment shader. |
| Water | Caustics, underwater refraction | Implemented | Post-processing while submerged. |
| Water | Water droplets after swimming | Implemented | Particles. |
| Water | Screen-space reflections | **Not available** | Post-processing cannot tell which pixels are water in the vanilla pipeline. Use an Iris shader pack. |
| Particles | Falling leaves (foliage-tinted, cherry and pale-oak variants), fireflies, sunlit dust, pollen, cave dust, windblown debris, fire/smoke/embers, glowing-block sparkles | Implemented | Vanilla particle types only, so resource packs re-texture them. There is a shared budget, a distance limit, a frustum test, and each type has its own toggle. |
| Sky | Sunrise/sunset glow, sun halo and horizon haze, moonlight scattering, night tone | Implemented | Post-processing and fog tint. |
| Sky | Enhanced clouds / stars | **Not implemented** | Vanilla clouds and stars are kept; grading and fog tint them. |
| Dimensions | Nether / End haze; Overworld-only weather, sun and god rays | Implemented | Each dimension keeps its identity. |
| Performance | Monitor (FPS, frame time avg/worst, particles, lights, preset) | Implemented | HUD element. |
| Performance | Presets: Potato, Performance, Balanced, High, Ultra, Cinematic, Custom | Implemented | Presets set the actual settings. |
| Performance | LOD, frustum culling, particle cap, light cap, distant-light update throttling, low-performance mode, instant disable (F9) | Implemented | |

\* Requires the bundled **"Aether Realism Shaders"** resource pack (enabled by default) and does **not** work with
Sodium (see [COMPATIBILITY.md](COMPATIBILITY.md)).

## Requirements

- Minecraft Java Edition **1.21.11**
- Fabric Loader **0.17.3+** (developed against 0.20.1)
- Fabric API **0.141.6+1.21.11** or newer for 1.21.11
- Java **21**

## Installation

1. Install Fabric Loader for 1.21.11 (https://fabricmc.net/use/).
2. Put `aether-realism-<version>.jar` and Fabric API into `.minecraft/mods/`.
3. Launch the game. Check **Options → Resource Packs** that **Aether Realism Shaders** is in the *Selected*
   column. It is enabled by default and drives the foliage and water animation. Put it **above** packs that replace
   `terrain.vsh`/`terrain.fsh`, or disable it if you prefer the other pack.

## Using it

- **Settings screen:** *Options → Video Settings →* **Aether Realism...** (top right), or press **F10** in game.
- **Panic switch:** **F9** toggles every effect instantly.
- **Performance monitor:** toggle it in *Overview*, or bind *Toggle performance monitor* in Controls.

All key bindings can be changed in *Options → Controls → Key Binds → Aether Realism*.

### The settings screen

- Eleven categories: Overview, Lighting, Nature, Weather, Atmosphere, Water, Particles, Sky, Performance,
  Presets, Advanced.
- Toggle switches, sliders, dropdowns and buttons. Every row has a tooltip with a description and its performance
  impact (the colored dot).
- **Search** (Ctrl+F) across all settings.
- Changes are **staged**:
  - **Apply** saves and applies them immediately.
  - **Cancel** / Esc discards them.
  - **Done** applies and closes.
  - "unsaved changes" is shown in the header while there are staged edits.
- **Reset category** restores the defaults of the current category. **Reset all...** asks for confirmation first.
- Expensive effects (volumetric fog, god rays, SSAO) ask for confirmation before being enabled.
- Keyboard:
  - Tab moves between the search box, categories, settings and buttons.
  - ↑/↓ selects a row, ←/→ changes its value (Shift for bigger slider steps), Enter/Space activates.
  - Ctrl+PgUp/PgDn switches category. Ctrl+S applies.
- The layout adapts to the window size. On narrow windows the sidebar collapses to icons and long labels are
  truncated with "..." instead of overlapping.

Most changes apply instantly. Settings baked into terrain (vegetation, water waves, terrain shader layer) trigger
one automatic chunk rebuild. No restart is ever needed.

## Configuration file

Settings are saved to `config/aether_realism.json` and loaded at startup.

- Values are validated and clamped.
- New settings get defaults.
- A malformed file is backed up to `aether_realism.json.broken` and replaced with defaults.
- `customItemLights` maps item ids to light levels (0–15), e.g. `"minecraft:blaze_rod": 10`.

**If a configuration causes problems:**

- press **F9**, or
- use *Advanced → Reset everything*, or
- delete the config file, or
- start once with the JVM argument `-Daether_realism.safe=true` (all effects off for that session; the file stays
  untouched until you press Apply).

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/aether-realism-<version>.jar`. You need Java 21 and internet access to
`maven.fabricmc.net`, Mojang's servers and Maven Central. `./gradlew runClient` starts a development client.

Versions are pinned in `gradle.properties` and match the official `fabric-example-mod` 1.21.11 branch: Loom
`1.19-SNAPSHOT` (`net.fabricmc.fabric-loom-remap`), Gradle wrapper from the same branch, and Mojang mappings.

## Architecture

```
com.aetherrealism
├── AetherRealismClient     client entrypoint: config, keys, events, built-in pack, model plugin
├── config                  AetherConfig (all settings), ConfigManager (load/validate/save), Preset, Quality, ColorStyle
├── gui                     AetherSettingsScreen, SettingsRegistry (every row), Setting model, Theme
│   └── widget              CategoryList, SettingsList (toggles/sliders/dropdowns), FlatButton, ToggleSwitch
├── render                  PostEffectManager (post chain + float parameter texture), RenderCapture, TerrainRebuildScheduler
├── lighting                DynamicLightManager
├── vegetation              FoliageModelPlugin + SwayBlockStateModel (FRAPI vertex tagging), FoliageSettings, WindSystem
├── weather                 WeatherEffects
├── atmosphere              AtmosphereController (fog model, sun, lightning, post parameters)
├── water                   WaterRenderHandler + WaterMarkingVertexConsumer (fluid API wrapper)
├── particles               AmbientParticleSystem, ParticleBudget
├── performance             PerformanceMonitor
├── compat                  CompatManager (Sodium/Iris detection, shader-pack suspension)
└── mixin                   LevelRenderer (matrices + post), FogRenderer (fog), BrightnessGetter + EntityRenderer (lights)
```

Resources:

- `resourcepacks/aether_shaders`: the built-in pack overriding vanilla `core/terrain.vsh`/`terrain.fsh`. It is
  vanilla 1.21.11 plus decoding of the animation markers.
- `assets/aether_realism/post_effect/aether.json` and `shaders/post/*`: the six-pass post chain (atmosphere,
  bloom extract, two blur passes, god rays, composite).
- `assets/aether_realism/shaders/include/params.glsl`: decoding of the live parameter texture.

### Two techniques worth knowing about

**Animation markers.** The vanilla terrain shader has no per-block attribute and no hook for live parameters.
Foliage and water vertices are therefore tagged in the alpha channel of their vertex color, which is always 255
on cutout geometry. The bundled shader decodes that value, animates the vertex and restores alpha.
See `FoliageSettings` for the encoding.

**Live post-processing parameters.** Vanilla post chains only accept constant uniforms from JSON. The mod
therefore writes the raw IEEE-754 bits of 64 floats into a 64×1 RGBA8 texture each frame, and the shaders decode
them exactly with `uintBitsToFloat`. The floats include the inverse view-projection matrix, sun direction, fog
color and every slider.

## Testing status

Not yet done, because this environment could not download Minecraft or Fabric:

- `./gradlew build`, a client launch, and the in-game checks (categories, persistence, resolutions, weather,
  dimensions, Sodium/Iris).

Signatures were verified against 1.21.11 sources wherever possible. If something fails, the most likely places are:

1. **`assets/aether_realism/post_effect/aether.json`, the `Params` texture input.** The field names (`location`,
   `width`, `height`, `bilinear`) and the texture lookup path were not verifiable. The mod registers the dynamic
   texture under both `aether_realism:params` and `aether_realism:textures/effect/params.png`. If the chain fails
   to load, the log says so, post effects are skipped and everything else keeps working.
2. **Vanilla constants and methods not used by any reference mod:**
   - `ParticleTypes.TINTED_LEAVES` with `ColorParticleOption`
   - `DustParticleOptions(int, float)`
   - `Minecraft#getFps`
   - `Level#getMinSectionY`
3. **The `BrightnessGetter` lambda target** (`method_68890` / `lambda$static$0`). It is copied from
   LambDynamicLights 1.21.11. If it does not match, dynamic lights stop affecting terrain without crashing.

All mixin injectors use `require = 0`, and effect code runs inside error guards that log each distinct error once.
A missed hook therefore disables one effect instead of crashing the game.

## License

MIT. See [LICENSE](LICENSE). No third-party mods are bundled.
