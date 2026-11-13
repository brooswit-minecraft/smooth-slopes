bump: minor

### Changed
- Worldgen smoothing's structure-footprint guard has been removed entirely (MINECRAFT-500 live
  investigation, 53461/53467/53502): it previously excluded a structure's bounding box from
  smoothing, first as one box for the whole structure, then (MINECRAFT-516) as a tighter box per
  piece. Neither was ever something the user asked for -- it was inherited unquestioned from
  Dynamic Terrain's pre-extraction code. Material, cover and support checks are unchanged;
  smoothing now applies uniformly regardless of nearby structures. Newly generated terrain only;
  this never retroactively edits existing chunks.
