"""Disposable CI only.

Minimal, pure-Python Anvil (.mca) region-file reader -- just enough to answer one yes/no question:
does any generated chunk in this region file contain a given block-id substring anywhere in its
(decompressed) chunk data. No full NBT parsing: a block-state palette entry's own id is stored as
plain UTF-8 text directly in the chunk's NBT payload, so a substring search over the decompressed
bytes is sufficient and far simpler than writing a real NBT reader, for this one bounded purpose
(MINECRAFT-500 / Wroosbit review 52785: a no-smoothing witness for a real generated singleplayer
save, where no RCON is available since there is no live server to query).
"""
import struct
import zlib
from pathlib import Path


def region_contains(mca_path: Path, needle: bytes) -> bool:
    data = mca_path.read_bytes()
    if len(data) < 8192:
        return False
    for i in range(1024):
        # Standard Anvil header: 1024 (offset:3 bytes big-endian sectors, sector_count:1 byte) entries,
        # followed by 1024 (timestamp:4 bytes) entries. Offset/size are in 4096-byte sectors.
        entry = data[i * 4:i * 4 + 4]
        sector_offset = int.from_bytes(entry[0:3], 'big')
        sector_count = entry[3]
        if sector_offset == 0 and sector_count == 0:
            continue
        start = sector_offset * 4096
        if start + 5 > len(data):
            continue
        length = struct.unpack('>I', data[start:start + 4])[0]
        compression = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        try:
            if compression == 2:
                decompressed = zlib.decompress(payload)
            elif compression == 1:
                import gzip
                decompressed = gzip.decompress(payload)
            else:
                continue
        except Exception:
            continue
        if needle in decompressed:
            return True
    return False


def scan_region_dir(region_dir: Path, needle: bytes) -> bool:
    for mca_path in sorted(region_dir.glob('*.mca')):
        if region_contains(mca_path, needle):
            return True
    return False
