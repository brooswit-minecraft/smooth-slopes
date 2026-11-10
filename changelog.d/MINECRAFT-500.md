bump: minor

### Added
- Extract Dynamic Terrain's worldgen-only surface smoothing (`SmoothSurfaceFeature`,
  `SmoothColumnRules`, `SurfaceSmoother`, `StructureBounds`, `StructureProtection`) into this
  standalone mod, depending on Layered Slopes for the registered layered-block family.
- `worldgenSmoothing` server config key, migrating Dynamic Terrain's own pre-split value (both the
  global default and any per-world `serverconfig` override) so an explicit opt-out is never
  silently re-enabled by the upgrade.
- Coinstalled GameTest evidence that this mod and Layered Slopes boot and resolve blocks together.
