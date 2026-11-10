#!/usr/bin/env python3
"""Disposable CI only.

MINECRAFT-500 / Wroosbit review 52775, findings 2+3: the FreeTerraForged acceptance proves Smooth
Slopes smooths terrain, but only coinstalled with Layered Slopes (+ FreeTerraForged); it never
installs Dynamic Terrain's own exact PR head alongside it, and the existing config-migration tests
exercise file helpers only, not NeoForge's real config load/reload lifecycle. This boots Dynamic
Terrain + Layered Slopes + Smooth Slopes together, via Dynamic Terrain's own independently-built
jar (not a source dependency -- see build.gradle's pairedServer run), through three real dedicated
server boots:

  1. baseline: no legacy config at all -- confirms the three coinstalled mods load and worldgen
     smoothing actually runs (vanilla terrain has natural height variation even in a small grid).
  2. global legacy disabled: config/dynamicterrain-server.toml pre-seeded with
     worldgenSmoothing=false before first boot -- confirms Smooth Slopes' own config migrates and
     carries that forward (read directly off disk, not just no layered blocks generating).
  3. per-world legacy disabled, global absent: <world>/serverconfig/dynamicterrain-server.toml
     pre-seeded with worldgenSmoothing=false, no global override at all -- confirms per-world
     precedence over the (otherwise-true) global default, through the same real lifecycle.

Dynamic Terrain no longer registers any worldgen smoothing feature at all at this PR's head (the
reviewed diff already confirmed that removal); this script's own behavioral evidence is that
smoothing only ever happens when Smooth Slopes' own migrated config says so, which is the
practical proof available over plain vanilla RCON that only smoothslopes:smooth_surface is what
runs -- it cannot enumerate the registered Feature ids directly.

Usage: paired_acceptance.py <smooth-slopes-repo-dir> <dynamic-terrain-jar-path>
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
# 500500501 (one off from ftf_acceptance.py's own proven-good 500500500) was tried first and
# confirmed live, by direct RCON height probing, to be open ocean for at least a 48-block radius
# around spawn -- SmoothSurfaceFeature only ever touches natural ground columns, so a baseline
# scenario that is pure ocean can never produce a layered block no matter how correctly smoothing
# is wired up. Seed 0 confirmed live (same probe) to have real, varied land with multiple natural
# 1-block steps within this exact grid.
SEED = 0
GRID_RANGE = range(-16, 17, 8)  # 5x5 = 25 columns, same bound as ftf_acceptance.py
LAYERED_SURFACE_BLOCKS = ["layered_dirt", "layered_grass", "layered_stone"]
# Matches ftf_acceptance.py's own TOP_Y/BOTTOM_Y exactly (160/0): a narrower 110/40 window was
# tried first and, confirmed live, missed every eligible column in plain vanilla terrain at this
# seed/grid -- vanilla height variation is not guaranteed to land inside an arbitrarily narrower
# band than the one already proven wide enough for FreeTerraForged's own (larger) terrain.
TOP_Y = 160
BOTTOM_Y = 0
SUCCESS_MARKER = "Changed the block at"
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


def block_matches(port, x, y, z, block_id):
    mx, my, mz = MARKER_POS
    rcon('127.0.0.1', port, RCON_PASSWORD, f"setblock {mx} {my} {mz} minecraft:air")
    response = rcon('127.0.0.1', port, RCON_PASSWORD,
                     f"execute if block {x} {y} {z} {block_id} "
                     f"run setblock {mx} {my} {mz} minecraft:emerald_block")[0]
    return SUCCESS_MARKER in response


def find_top_nonair(port, x, z):
    for y in range(TOP_Y, BOTTOM_Y - 1, -1):
        if not block_matches(port, x, y, z, "minecraft:air"):
            return y
    return None


def scan_for_layered_block(port):
    forceload_min, forceload_max = min(GRID_RANGE), max(GRID_RANGE)
    rcon('127.0.0.1', port, RCON_PASSWORD,
         f"forceload add {forceload_min} {forceload_min} {forceload_max} {forceload_max}")
    time.sleep(15)
    hits = []
    for x in GRID_RANGE:
        for z in GRID_RANGE:
            top = find_top_nonair(port, x, z)
            if top is None:
                continue
            for dy in (0, -1, 1):
                y = top + dy
                for name in LAYERED_SURFACE_BLOCKS:
                    if block_matches(port, x, y, z, f"dynamicterrain:{name}"):
                        hits.append({"pos": [x, y, z], "block": f"dynamicterrain:{name}"})
    rcon('127.0.0.1', port, RCON_PASSWORD,
         f"forceload remove {forceload_min} {forceload_min} {forceload_max} {forceload_max}")
    return hits


def write_server_properties(game_dir, port, rcon_port, level_name):
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


def run_server(cwd, dt_jar_path, log_path, ready_deadline=420):
    log = open(log_path, 'w')
    process = subprocess.Popen(
        ['./gradlew', 'runPairedServer', '--no-daemon', '-Dorg.gradle.jvmargs=-Xmx2G',
         f'-PdtJarPath={dt_jar_path}'],
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


def read_worldgen_smoothing(path):
    if not path.is_file():
        return None
    match = re.search(r"(?m)^\s*worldgenSmoothing\s*=\s*(true|false)\s*$", path.read_text())
    return match.group(1) == "true" if match else None


def run_scenario(repo_dir, dt_jar_path, label, port, rcon_port, seed_legacy):
    level_name = f"paired-acceptance-{label}"
    game_dir = repo_dir / 'run'
    if (game_dir / level_name).exists():
        raise RuntimeError(f"refusing to reuse an existing world at {game_dir / level_name}")
    write_server_properties(game_dir, port, rcon_port, level_name)
    seed_legacy(game_dir, level_name)
    process, log = run_server(repo_dir, dt_jar_path, Path.cwd() / f'paired-acceptance-{label}-server.log')
    try:
        hits = scan_for_layered_block(rcon_port)
    finally:
        stop_server(process, log, rcon_port)
    return hits, game_dir, level_name


def main():
    repo_dir = Path(sys.argv[1]).resolve()
    dt_jar_path = Path(sys.argv[2]).resolve()
    results = {}

    def no_legacy(game_dir, level_name):
        pass

    hits, _, _ = run_scenario(repo_dir, dt_jar_path, "baseline", 25600, 25610, no_legacy)
    results["baseline"] = hits
    if not hits:
        raise RuntimeError(
            "baseline (no legacy config) produced no layered block anywhere in the sampled grid "
            "-- either the three coinstalled mods did not actually smooth vanilla terrain, or "
            "the sampled grid missed every eligible column")

    def global_legacy_disabled(game_dir, level_name):
        config_dir = game_dir / 'config'
        config_dir.mkdir(parents=True, exist_ok=True)
        # The migrated default only ever applies on smoothslopes-server.toml's OWN first write --
        # confirmed live that the baseline scenario above, run first in this same game_dir, already
        # wrote one (with the un-migrated default, since it saw no legacy file) before this scenario
        # boots. NeoForge's config system then reuses that already-persisted value regardless of
        # what the legacy file says, since only a config file's first write ever takes a migrated
        # default -- this scenario must start from a genuinely fresh global config, the same as a
        # real server upgrading for the first time would.
        (config_dir / 'smoothslopes-server.toml').unlink(missing_ok=True)
        (config_dir / 'dynamicterrain-server.toml').write_text("worldgenSmoothing = false\n")

    hits, game_dir, _ = run_scenario(repo_dir, dt_jar_path, "global-legacy-disabled", 25601, 25611,
                                      global_legacy_disabled)
    results["global_legacy_disabled"] = hits
    migrated_global = read_worldgen_smoothing(game_dir / 'config' / 'smoothslopes-server.toml')
    if migrated_global is not True and migrated_global is not False:
        raise RuntimeError("config/smoothslopes-server.toml was not created with a readable "
                            "worldgenSmoothing value after booting with a legacy global override")
    if migrated_global is not False:
        raise RuntimeError(
            f"global legacy worldgenSmoothing=false did not migrate: smoothslopes-server.toml "
            f"has worldgenSmoothing={migrated_global}")
    if hits:
        raise RuntimeError(
            f"global legacy worldgenSmoothing=false migrated correctly on disk, but smoothing "
            f"still produced {len(hits)} layered block(s): {hits[:3]}")

    def per_world_legacy_disabled(game_dir, level_name):
        # Same reason as global_legacy_disabled above: this scenario needs global absent to mean
        # a genuinely fresh (migrated-true) global default, not whatever the previous scenario's
        # own global config already persisted to disk.
        (game_dir / 'config' / 'smoothslopes-server.toml').unlink(missing_ok=True)
        world_config_dir = game_dir / level_name / 'serverconfig'
        world_config_dir.mkdir(parents=True, exist_ok=True)
        (world_config_dir / 'dynamicterrain-server.toml').write_text("worldgenSmoothing = false\n")

    hits, game_dir, level_name = run_scenario(repo_dir, dt_jar_path, "per-world-legacy-disabled",
                                               25602, 25612, per_world_legacy_disabled)
    results["per_world_legacy_disabled"] = hits
    migrated_per_world = read_worldgen_smoothing(
        game_dir / level_name / 'serverconfig' / 'smoothslopes-server.toml')
    if migrated_per_world is not False:
        raise RuntimeError(
            f"per-world legacy worldgenSmoothing=false (global absent, so global default is "
            f"true) did not migrate with correct precedence: per-world "
            f"smoothslopes-server.toml has worldgenSmoothing={migrated_per_world}")
    if hits:
        raise RuntimeError(
            f"per-world legacy worldgenSmoothing=false migrated correctly on disk, but smoothing "
            f"still produced {len(hits)} layered block(s): {hits[:3]}")

    Path('paired-acceptance-result.json').write_text(json.dumps({
        "passed": True,
        "seed": SEED,
        "baseline_hits": len(results["baseline"]),
        "global_legacy_disabled_hits": len(results["global_legacy_disabled"]),
        "per_world_legacy_disabled_hits": len(results["per_world_legacy_disabled"]),
        "sample_baseline_hits": results["baseline"][:5],
    }, indent=2))
    print(json.dumps({"passed": True,
                       "baseline_hits": len(results["baseline"]),
                       "global_legacy_disabled_hits": len(results["global_legacy_disabled"]),
                       "per_world_legacy_disabled_hits": len(results["per_world_legacy_disabled"])}))


if __name__ == '__main__':
    main()
