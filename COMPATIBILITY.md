# Compatibility

The table shows what each feature needs and how it behaves next to other mods. It is based on how each piece
is implemented. It has **not** been tested in game yet (see the README's *Testing status*).

| Feature group | Hook | Vanilla + Fabric API | Sodium | Iris, no shader pack | Iris with shader pack |
|---|---|---|---|---|---|
| Settings GUI, presets, config, HUD monitor | Fabric screen/HUD/key APIs | ✔ | ✔ | ✔ | ✔ |
| Foliage sway, water waves, water shimmer | FRAPI model wrapper + fluid API → bundled `terrain.vsh/fsh` | ✔ | ✘ disabled automatically (Sodium uses its own terrain shaders) | ✔ unless Sodium is also present | ✘ suspended (the pack draws terrain) |
| Dynamic lights on terrain | `LevelRenderer.BrightnessGetter` + section re-mesh | ✔ | ⚠ likely no effect: Sodium has its own light pipeline | same as the renderer underneath | ✘ suspended |
| Dynamic lights on entities | `EntityRenderer#getBlockLightLevel` | ✔ | ✔ | ✔ | ✘ suspended |
| Fog shaping (weather, biome, cave, underwater) | `FogRenderer#setupFog` → `updateBuffer` args | ✔ | ✔ Sodium reads the same fog values, so its terrain shows our fog | ✔ | ✘ suspended (shader packs ignore vanilla fog) |
| Post chain (grading, bloom, god rays, height/volumetric fog, SSAO, caustics, flashes) | `LevelRenderer#renderLevel` return → vanilla `PostChain` | ✔ | ✔ | ✔ | ✘ suspended |
| Weather and ambient particles | vanilla particle types via `ClientLevel#addParticle` | ✔ | ✔ | ✔ | ✔ the pack renders them |

✔ works · ⚠ partial or unverified · ✘ not active

"Suspended" means: while Iris reports an active shader pack, Aether Realism switches those parts off so the two
never fight over the image. You can change this with *Advanced → Pause with shader packs*. Iris is queried
through its public API (`net.irisshaders.iris.api.v0.IrisApi`) by reflection, so Iris is never required at
runtime.

## Recommended setups

- **Best visuals without a shader pack:** Fabric API + Aether Realism, without Sodium. You get everything,
  including foliage and water animation.
- **Best FPS:** Fabric API + Sodium + Aether Realism. You lose foliage/water animation and probably terrain
  dynamic lights. Fog, post-processing, weather and particles all work.
- **Shader-pack users:** Iris with a shader pack provides its own lighting, water and sky. Aether Realism steps
  back automatically but still provides the GUI, particles and weather particles.

## Resource packs

- Ordinary resource packs (textures, models, sounds) are fully compatible. Every particle the mod spawns is a
  vanilla particle type, and vegetation uses the pack's own models.
- Packs that replace `assets/minecraft/shaders/core/terrain.vsh` or `terrain.fsh` conflict with the bundled
  **Aether Realism Shaders** pack. Whichever is higher in the list wins. If the other pack wins, animated foliage
  and water stop and the mod keeps working otherwise.
- The bundled pack declares pack format 75 (1.21.11 only). If its shaders failed to compile, vanilla's resource
  reload would fall back and report it, and you can disable the pack in *Options → Resource Packs*.

## Other client mods

- **Other dynamic-light mods** (e.g. LambDynamicLights) hook the same light lookup. Running both is harmless but
  redundant. Turn off *Lighting → Dynamic lighting* in one of them.
- **Mods that replace the fluid renderer for water** stay in control of rendering. Aether Realism only wraps the
  registered handler to tag vertices.
- **Mods that add their own post-processing** run independently. The combined look may be too strong. Lower
  exposure, contrast or bloom here if so.

## Multiplayer

Everything is client-side and needs nothing on the server. Biome fog uses biome ids, which the server sends to
the client. No registries are modified and no packets are sent.
