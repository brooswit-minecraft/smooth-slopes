bump: patch

### Fixed
- Worldgen smoothing's structure protection now excludes each structure's own PIECES
  (`StructurePiece.getBoundingBox()`), not the whole structure's outer bounding box
  (`StructureStart.getBoundingBox()`, the union of every piece's box). For structures with
  winding, scattered pieces -- mineshafts especially -- the outer box was dramatically larger
  than anything actually built, falsely excluding large areas of ordinary surface terrain from
  smoothing even though no structure block was anywhere near.
