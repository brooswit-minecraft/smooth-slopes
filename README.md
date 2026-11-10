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
only read, never rewritten or deleted.

**Registry identifiers — why this is safe to rename:** `smooth_surface`
moves from the `dynamicterrain` namespace to `smoothslopes`
(`neoforge:add_features`/`worldgen/configured_feature`/`worldgen/placed_feature`).
Unlike the layered *blocks* themselves (which Layered Slopes deliberately
kept under the legacy `dynamicterrain` namespace because block ids are
saved per-chunk), a worldgen *feature* id is never persisted in a save —
it only exists while a chunk is actively being generated — so renaming it
carries no save-compatibility risk. Blocks this feature already placed in
existing chunks keep resolving exactly as before, through Layered Slopes'
preserved `dynamicterrain:layered_*` ids.

## Test coverage — what's covered, what isn't

Headless (no game bootstrap): `SmoothColumnRulesTest`/`SurfaceSmootherTest`/
`StructureBoundsTest`/`StructureProtectionTest` cover the pure worldgen
decisions ported unchanged from Dynamic Terrain. `MigrationPolicyTest`/
`LegacyConfigMigrationTest` cover the config-migration decision and its
file-reading glue, including a missing, key-less, or unreadable legacy
file.

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

**Still not covered, and not manually playtested in this PR:** actual
chunk-generation output (`SmoothSurfaceFeature.place()` itself needs a
real `WorldGenLevel` during chunk generation, which the GameTest framework
does not provide — the pure decision it delegates to is covered above
instead), and the exact FreeTerraForged-generated-preset acceptance
(real generated datapack, disposable world, before/after surface
evidence) requested on MINECRAFT-500 — tracked as a remaining gap on the
ticket rather than claimed here.

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
