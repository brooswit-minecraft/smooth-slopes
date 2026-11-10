#!/usr/bin/env python3
"""Disposable CI only.

MINECRAFT-500 / Jira review 52702/52703/52752: a coinstalled GameTest proves this mod and Layered
Slopes boot and resolve blocks together, but it does not prove anything about actual chunk
generation under FreeTerraForged's own noise settings. This boots a real dedicated server (no
client needed) twice, same seed, with the exact FreeTerraForged 1.0.0 default preset applied as a
real generated/applied datapack (see FreeTerraForgedPresetFixture, which produces it headlessly via
FreeTerraForged's own public API -- no GUI involved): once with worldgenSmoothing enabled, once
disabled. It then forces generation of a grid of columns and checks, over RCON, for the one thing
that can only exist if Smooth Slopes actually smoothed a surface: a `dynamicterrain:layered_*`
block. Vanilla/FreeTerraForged chunk generation never places one on its own.

Usage: ftf_acceptance.py <mod-repo-dir> <ftf-datapack-dir>
"""
import json
import os
import re
import signal
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

RCON_PASSWORD = "acceptance"
SEED = 500500500
# A grid of columns around spawn, wide enough that at least a few should land on open ground
# rather than tree canopy (which this script's air-boundary probe cannot see through -- those
# columns just read as inconclusive, not wrong, since the same grid is used for both runs). Kept
# deliberately small: find_top_nonair below is a worst-case O(TOP_Y-BOTTOM_Y) linear scan (2 RCON
# round trips per Y checked), run once per column, and this whole scan runs twice (enabled/disabled).
GRID_RANGE = range(-16, 17, 8)  # 5x5 = 25 columns
# The most common natural-surface materials with a registered layered form (dynamicterrain
# namespace -- Layered Slopes' preserved pre-split registry name, per its own README). Kept to the
# three most common temperate-biome surface blocks, not the full material list, to bound RCON cost.
LAYERED_SURFACE_BLOCKS = ["layered_dirt", "layered_grass", "layered_stone"]
# Plain (unlayered) counterparts, purely diagnostic: if a column shows one of these but never its
# layered_* form, that is real evidence terrain generated normally but smoothing never touched it
# (config/biome-modifier gap), not evidence the sampling grid missed the surface.
PLAIN_SURFACE_BLOCKS = ["dirt", "grass_block", "stone", "sand"]
TOP_Y = 160
BOTTOM_Y = 0
SUCCESS_MARKER = "Changed the block at"  # see dynamic-terrain's own legacy_save_acceptance.py
MARKER_POS = (0, 250, 0)


def rcon(host, port, password, *commands):
    sock = socket.create_connection((host, port), timeout=10)
    try:
        def send(request_id, packet_type, payload):
            body = struct.pack('<ii', request_id, packet_type) + payload.encode() + b'\x00\x00'
            sock.sendall(struct.pack('<i', len(body)) + body)

        def recv():
            length = struct.unpack('<i', sock.recv(4))[0]
            data = b''
            while len(data) < length:
                chunk = sock.recv(length - len(data))
                if not chunk:
                    raise RuntimeError("RCON connection closed early")
                data += chunk
            request_id, packet_type = struct.unpack('<ii', data[:8])
            return request_id, packet_type, data[8:-2].decode(errors='replace')

        send(1, 3, password)
        request_id, _, _ = recv()
        if request_id == -1:
            raise RuntimeError("RCON auth failed")
        results = []
        for index, command in enumerate(commands, start=2):
            send(index, 2, command)
            _, _, response = recv()
            results.append(response)
        return results
    finally:
        sock.close()


def write_server_properties(game_dir, port, rcon_port, level_name, worldgen_smoothing):
    game_dir.mkdir(parents=True, exist_ok=True)
    (game_dir / 'eula.txt').write_text('eula=true\n')
    (game_dir / 'server.properties').write_text(
        "server-ip=127.0.0.1\n"
        f"server-port={port}\n"
        "online-mode=false\n"
        "enforce-secure-profile=false\n"
        f"level-name={level_name}\n"
        "level-type=minecraft:normal\n"
        f"level-seed={SEED}\n"
        "gamemode=creative\n"
        "spawn-protection=0\n"
        "generate-structures=true\n"
        "view-distance=10\n"
        "simulation-distance=10\n"
        "max-players=1\n"
        "enable-rcon=true\n"
        f"rcon.port={rcon_port}\n"
        f"rcon.password={RCON_PASSWORD}\n"
        "broadcast-rcon-to-ops=false\n"
    )
    config_dir = game_dir / 'config'
    config_dir.mkdir(parents=True, exist_ok=True)
    (config_dir / 'smoothslopes-server.toml').write_text(
        f"worldgenSmoothing = {str(worldgen_smoothing).lower()}\n")


def seed_datapack(game_dir, level_name, datapack_dir):
    world_datapacks = game_dir / level_name / 'datapacks' / datapack_dir.name
    world_datapacks.mkdir(parents=True, exist_ok=True)
    subprocess.run(['cp', '-r', str(datapack_dir) + '/.', str(world_datapacks)], check=True)


def run_server(cwd, log_path, ready_deadline=420):
    log = open(log_path, 'w')
    process = subprocess.Popen(
        ['./gradlew', 'runServer', '--no-daemon', '-Dorg.gradle.jvmargs=-Xmx2G'],
        cwd=cwd, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    deadline = time.monotonic() + ready_deadline
    while time.monotonic() < deadline:
        text = Path(log_path).read_text(errors='replace')
        if re.search(r'Done \(', text):
            return process, log
        if re.search(r'Failed to start the minecraft server|ModLoadingException', text):
            raise RuntimeError(f"server failed to start, see {log_path}:\n{text[-3000:]}")
        if process.poll() is not None:
            raise RuntimeError(f"server exited before readiness, see {log_path}")
        time.sleep(2)
    raise RuntimeError(f"server startup timeout, see {log_path}")


def stop_server(process, log, port):
    try:
        rcon('127.0.0.1', port, RCON_PASSWORD, "stop")
    except Exception:
        pass
    try:
        process.wait(timeout=90)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
    log.close()


def block_matches(port, x, y, z, block_id):
    """True if the block at (x,y,z) matches block_id, via the same fail-closed marker-flip oracle
    dynamic-terrain's own legacy_save_acceptance.py uses: `execute if ... run <command>` is the
    only vanilla command form that unambiguously signals a match over RCON (a bare `execute if
    block ...` with no `run` clause is incomplete syntax, not a boolean query, and would have
    returned a parse error on every call -- confirmed live, not assumed, after the first real
    attempt here used exactly that bare form and silently got a constant result)."""
    mx, my, mz = MARKER_POS
    rcon('127.0.0.1', port, RCON_PASSWORD, f"setblock {mx} {my} {mz} minecraft:air")
    response = rcon('127.0.0.1', port, RCON_PASSWORD,
                     f"execute if block {x} {y} {z} {block_id} "
                     f"run setblock {mx} {my} {mz} minecraft:emerald_block")[0]
    return SUCCESS_MARKER in response


def find_top_nonair(port, x, z):
    """Linear scan from TOP_Y down to the first non-air block. NOT a binary search: FreeTerraForged
    generates overhangs/caves, so a column's air/solid pattern is not monotonic in Y, which a
    binary search silently assumes. Trees/leaves are not air either, so a wooded column reads as
    the top of its canopy, not the ground -- an inconclusive column, not a wrong one, since the
    same grid is used for both the enabled and disabled run."""
    for y in range(TOP_Y, BOTTOM_Y - 1, -1):
        if not block_matches(port, x, y, z, "minecraft:air"):
            return y
    return None


def column_surface(port, x, z):
    """@return (top_y, layered_name_or_None, plain_name_or_None) for diagnostic purposes."""
    top = find_top_nonair(port, x, z)
    if top is None:
        return None, None, None
    for dy in (0, -1, 1):  # smoothing can report the surface one cell either side of the raw top
        y = top + dy
        for name in LAYERED_SURFACE_BLOCKS:
            if block_matches(port, x, y, z, f"dynamicterrain:{name}"):
                return y, name, None
    for dy in (0, -1, 1):
        y = top + dy
        for name in PLAIN_SURFACE_BLOCKS:
            if block_matches(port, x, y, z, f"minecraft:{name}"):
                return y, None, name
    return top, None, None


def scan(port):
    forceload_min_x, forceload_max_x = min(GRID_RANGE), max(GRID_RANGE)
    rcon('127.0.0.1', port, RCON_PASSWORD,
         f"forceload add {forceload_min_x} {forceload_min_x} {forceload_max_x} {forceload_max_x}")
    # Heuristic: FreeTerraForged's noise pipeline is heavier than vanilla's, so give the
    # forceloaded 7x7-chunk area real time to actually finish generating before sampling it.
    time.sleep(30)
    hits = []
    plain_only = []
    for x in GRID_RANGE:
        for z in GRID_RANGE:
            y, layered_name, plain_name = column_surface(port, x, z)
            print(f"column ({x},{z}): top_y={y} layered={layered_name} plain={plain_name}", flush=True)
            if layered_name:
                hits.append({"pos": [x, y, z], "block": f"dynamicterrain:{layered_name}"})
            elif plain_name:
                plain_only.append({"pos": [x, y, z], "block": f"minecraft:{plain_name}"})
    rcon('127.0.0.1', port, RCON_PASSWORD,
         f"forceload remove {forceload_min_x} {forceload_min_x} {forceload_max_x} {forceload_max_x}")
    return hits, plain_only


def main():
    repo_dir = Path(sys.argv[1]).resolve()
    datapack_dir = Path(sys.argv[2]).resolve()
    workdir = Path.cwd()

    runs = [("enabled", 25580, 25590, True), ("disabled", 25581, 25591, False)]
    results = {}
    for label, port, rcon_port, worldgen_smoothing in runs:
        level_name = f"ftf-acceptance-{label}"
        game_dir = repo_dir / 'run'
        if (game_dir / level_name).exists():
            raise RuntimeError(f"refusing to reuse an existing world at {game_dir / level_name}")
        write_server_properties(game_dir, port, rcon_port, level_name, worldgen_smoothing)
        seed_datapack(game_dir, level_name, datapack_dir)
        process, log = run_server(repo_dir, workdir / f'ftf-acceptance-{label}-server.log')
        try:
            results[label] = scan(rcon_port)
        finally:
            stop_server(process, log, rcon_port)

    enabled_hits, enabled_plain = results["enabled"]
    disabled_hits, disabled_plain = results["disabled"]
    if not enabled_hits:
        raise RuntimeError(
            "worldgenSmoothing=true produced no layered block anywhere in the sampled grid -- "
            "either smoothing did not run under the FreeTerraForged preset, or the sampled grid "
            "missed every eligible column (widen GRID_RANGE or check the preset's terrain band). "
            f"{len(enabled_plain)} column(s) found a plain (unlayered) surface instead "
            f"(sample: {enabled_plain[:5]}) -- terrain generated normally, so if that count is "
            "nonzero the gap is smoothing not applying, not sampling missing the ground.")
    if disabled_hits:
        raise RuntimeError(
            f"worldgenSmoothing=false still produced {len(disabled_hits)} layered block(s) -- "
            f"the config is not actually gating worldgen smoothing: {disabled_hits[:3]}")

    Path('ftf-acceptance-result.json').write_text(json.dumps({
        "passed": True,
        "seed": SEED,
        "enabled_hits": len(enabled_hits),
        "disabled_hits": len(disabled_hits),
        "sample_enabled_hits": enabled_hits[:5],
    }, indent=2))
    print(json.dumps({"passed": True, "enabled_hits": len(enabled_hits), "disabled_hits": len(disabled_hits)}))


if __name__ == '__main__':
    main()
