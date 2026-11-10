# smooth-slopes

Automatic world-generation smoothing extracted from Dynamic Terrain, depending on Layered Slopes.

NeoForge 1.21.1, Java 21. Mod ID: `smoothslopes`. MIT licensed.

Ships Dynamic Terrain's worldgen-only surface smoothing (MINECRAFT-500):
`SmoothSurfaceFeature` (the registered `top_layer_modification` feature),
`SmoothColumnRules`/`SurfaceSmoother` (the pure column/height decisions),
`StructureBounds`/`StructureProtection` (structure-footprint protection), and
`SmoothMaterials` (a worldgen-only base-block -> layered-block lookup). It
replaces 1-block steps on newly generated overworld surfaces with
[Layered Slopes](https://github.com/brooswit-minecraft/layered-slopes)
blocks, so slopes read as fractional ramps instead of staircases. **Only
newly generated chunks are ever touched; existing chunks are never
retroactively edited.** Manual grading, erosion, cave-ins and
driving-surface physics are **not** part of this mod — they stay in
[Dynamic Terrain](https://github.com/brooswit-minecraft/dynamic-terrain),
which no longer registers this feature once it depends on this mod.

## Install / upgrade pairing

Runs fully standalone with only Layered Slopes installed — **Dynamic
Terrain is not required**. `neoforge.mods.toml` declares `layeredslopes`
as a required dependency (not optional): this mod cannot place anything
without the real registered layered-block family.

**Config migration — why upgrading never silently re-enables smoothing:**
before this split, Dynamic Terrain's own `worldgenSmoothing` key (default
`true`) gated this feature from `config/dynamicterrain-server.toml`.
Dynamic Terrain no longer defines that key. This mod defines its own
`worldgenSmoothing` key in `config/smoothslopes-server.toml`, and on first
write (no existing `smoothslopes-server.toml` yet) its default is migrated
from Dynamic Terrain's own pre-split file if that file is still present:
an explicit `worldgenSmoothing = false` there carries forward as `false`
here (see `MigrationPolicy`/`LegacyConfigMigration`); anything else
defaults to the pre-split default of `true`. The legacy file itself is
only read, never rewritten or deleted. **Existing-config precedence:** this
default is only ever consulted by NeoForge the first time it creates
`smoothslopes-server.toml` — an already-existing file's real value always
wins, migrated or not, which is NeoForge's own config-loading behavior,
not something this mod re-implements.

**Per-world override migration (MINECRAFT-500 review, 52752):** NeoForge
also lets an operator override the global SERVER config for just one
world, via `<world save>/serverconfig/<modid>-server.toml` (resolved only
once that world is actually starting — see
`ServerLifecycleHooks.handleServerAboutToStart` in NeoForge itself). A
world could have had `worldgenSmoothing` disabled only for itself this
way, independent of the global default. `PerWorldConfigMigration`, run
from `SmoothSlopesMod`'s own `ServerAboutToStartEvent` listener (the
earliest point this world's override directory is known), migrates that
per-world legacy file into this mod's own per-world override exactly
like the global case: skipped entirely if this mod already has its own
per-world override for that world (operator-authored or migrated on a
previous start always wins), and never overwritten afterwards.

**Registry identifiers — why renaming this one is safe:** `smooth_surface`
moves from the `dynamicterrain` namespace to `smoothslopes`
(`neoforge:add_features`/`worldgen/configured_feature`/`worldgen/placed_feature`).
Unlike the layered *blocks* themselves (which Layered Slopes deliberately
kept under the legacy `dynamicterrain` namespace because block ids are
saved per-chunk), a worldgen *feature* id is not written into chunk data —
it is only resolved while a chunk is actively generating — so an existing
save's already-placed blocks keep resolving exactly as before, through
Layered Slopes' preserved `dynamicterrain:layered_*` ids, regardless of
this rename. This does **not** cover a third-party datapack or world
preset that references `dynamicterrain:smooth_surface` directly by id
(e.g. a custom biome's own feature list, or another pack's world-preset
JSON) — such a reference would need updating to `smoothslopes:smooth_surface`
by whoever maintains it, same as any upstream id rename; this mod has no
way to discover or migrate an arbitrary third party's own datapack.

## Test coverage — what's covered, what isn't

Headless (no game bootstrap): `SmoothColumnRulesTest`/`SurfaceSmootherTest`/
`StructureBoundsTest`/`StructureProtectionTest` cover the pure worldgen
decisions ported unchanged from Dynamic Terrain. `MigrationPolicyTest`/
`LegacyConfigMigrationTest` cover the global config-migration decision and
its file-reading glue, including a missing, key-less, or unreadable legacy
file. `PerWorldConfigMigrationTest` covers the per-world override
migration the same way, including that an existing per-world override of
this mod's own is never overwritten.

Real runtime evidence: `CoinstalledGameTests` (`ci.yml`'s "Run GameTests"
step, `./gradlew runGameTestServer` against a real NeoForge server with
Layered Slopes installed alongside this mod —
`scripts/check_gametest_count.py` fails closed unless both named tests
below are confirmed to have actually run and passed):
- `smoothMaterialsResolvesToTheRealLayeredSlopesBlocks` — this mod's
  worldgen-only lookup resolves vanilla dirt/grass/stone to Layered
  Slopes' real registered block instances in a live, coinstalled level.
- `smoothSurfaceFeatureIsRegistered` — `smoothslopes:smooth_surface` is a
  bound, registered `Feature`, and resolves to this mod's own
  `SmoothSurfaceFeature` class.

Exact-release FreeTerraForged acceptance (`ci.yml`'s `freeterraforged-acceptance`
job, `tools/freeterraforged-acceptance/ftf_acceptance.py`): FreeTerraForged
1.0.0's own default preset (`Presets.makeFTFDefault()`) is generated as a
real, applied datapack headlessly, via its own public API
(`Preset.buildPatch` + `DataGenUtil.createRegistryProvider` —
`FreeTerraForgedPresetFixture`, the exact mechanism
`PresetConfigScreen.exportAsDatapack` uses client-side, with no GUI
involved). A real dedicated server boots twice on that applied datapack,
same seed, once with `worldgenSmoothing` enabled and once disabled, and a
grid of columns around spawn is sampled over RCON. The disposable world's
own log confirms FreeTerraForged's real noise pipeline generated it (its
own shutdown report: `Total Chunks Generated: 242`), not vanilla's. The
enabled run found 5 `dynamicterrain:layered_grass` columns; the disabled
run, same seed and columns, found zero — e.g. `(16, -8)` is
`layered_grass` at y=68 enabled, plain `grass_block` at y=67 disabled.
That is a real, direct before/after result, not an inference from
co-loading.

**Still not covered, and not manually playtested in this PR:** actual
chunk-generation output beyond what the FreeTerraForged acceptance above
already samples (`SmoothSurfaceFeature.place()` itself needs a real
`WorldGenLevel` during chunk generation, which the GameTest framework does
not provide — the pure decision it delegates to is covered by the unit
tests above instead), and a genuine player-driven in-game playtest.

## Building

Requires a JDK 21 with `javac` on `JAVA_HOME`.

```sh
./gradlew build
```

## Releasing

Every PR that touches `src/` must add exactly one fragment under
`changelog.d/` (see `changelog.d/README.md`). Pushing to `main` with new
fragments tags a GitHub release automatically; nothing is committed back by
hand.
