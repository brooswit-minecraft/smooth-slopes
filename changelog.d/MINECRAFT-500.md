bump: minor

### Added
- Extract Dynamic Terrain's worldgen-only surface smoothing (`SmoothSurfaceFeature`,
  `SmoothColumnRules`, `SurfaceSmoother`, `StructureBounds`, `StructureProtection`) into this
  standalone mod, depending on Layered Slopes for the registered layered-block family.
- `worldgenSmoothing` server config key, migrating Dynamic Terrain's own pre-split value (both the
  global default and any per-world `serverconfig` override) so an explicit opt-out is never
  silently re-enabled by the upgrade.
- Coinstalled GameTest evidence that this mod and Layered Slopes boot and resolve blocks together.
- Hosted CI acceptance against the real FreeTerraForged 1.0.0 release: its default preset is
  generated as a real applied datapack headlessly (no GUI), then a dedicated server boots twice
  (worldgenSmoothing on/off, same seed) and is checked over RCON for an actual layered block --
  proof smoothing really ran under FreeTerraForged's own noise settings, not just co-loading.
