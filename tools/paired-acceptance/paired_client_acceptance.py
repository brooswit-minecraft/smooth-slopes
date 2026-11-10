#!/usr/bin/env python3
"""Disposable CI only.

MINECRAFT-500 / Wroosbit review 52785 + self-review 52783: every prior acceptance run boots a
dedicated SERVER with the coordinated Dynamic Terrain + Layered Slopes + Smooth Slopes set, never
a CLIENT -- the ticket requires both. A client boot also exercises the ONE launch path the
dedicated-server-only per-world migration fix could not cover: a singleplayer/integrated-server
save, chosen interactively, has no file that names it in advance the way server.properties names a
dedicated server's save -- so the per-world migration now scans every save under game_dir/saves/
at mod construction (see EarlyPerWorldLegacyMigration), and this is what actually exercises that
path end to end, live.

--quickPlaySingleplayer refuses to create a new world (confirmed live by reading
QuickPlay.joinSingleplayerWorld/LevelStorageSource.levelExists): it only opens an EXISTING save,
erroring out to a disconnect screen otherwise. So this first bootstraps a real, valid save (level
.dat, region files) the same way every other acceptance script here already does -- a real
dedicated server boot -- then relocates it under game_dir/saves/ for the client to open. The
bootstrap boots with worldgenSmoothing disabled GLOBALLY (never via the per-world override this
test is actually about) purely so its own unavoidable spawn-chunk pregeneration cannot contaminate
the later no-smoothing witness; the per-world legacy file is seeded ONLY after the bootstrap server
has stopped, so the client -- not the bootstrap -- is the one whose mod construction genuinely
performs the per-world migration for the first time.

After the client has generated real terrain and is stopped:
  1. No mod-loading crash occurred (title screen was reached is not enough on its own -- this
     fixture needs to actually reach and play into a world to exercise the migration at all).
  2. The per-world override actually migrated: <save>/serverconfig/smoothslopes-server.toml has
     worldgenSmoothing=false, captured before NeoForge's own per-world config correction could
     strip the legacy key (the exact race finding 52785 is about) -- and genuinely produced by the
     CLIENT's own construction, since no own-file existed going into the client boot.
  3. A no-smoothing witness: the save's own generated region files (read directly, no RCON -- there
     is no live server to query once the client has stopped, and this script deliberately never
     reopens the save under a server afterward) contain no dynamicterrain:layered_* block anywhere,
     while genuinely containing ordinary terrain blocks (a positive control -- real terrain was
     actually generated, not an empty/crashed save).

Usage: paired_client_acceptance.py <smooth-slopes-repo-dir> <dynamic-terrain-jar-path>
"""
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mca_scan import scan_region_dir  # noqa: E402

SAVE_NAME = "paired-client-acceptance"
RELATIVE_SAVE_PATH = f"saves/{SAVE_NAME}"
LAYERED_NEEDLE = b"dynamicterrain:layered_"
PLAIN_NEEDLE = b"minecraft:grass_block"
RCON_PASSWORD = "acceptance"
READY_MARKER = re.compile(r"Loaded \d+ advancements|Preparing spawn area|Time elapsed:")
CRASH_MARKERS = re.compile(r"ModLoadingException|Failed to start|crash-reports")
SUCCESS_MARKER = "Changed the block at"


def rcon(host, port, password, *commands):
    import socket
    import struct
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


def read_worldgen_smoothing(path: Path):
    if not path.is_file():
        return None
    match = re.search(r"(?m)^\s*worldgenSmoothing\s*=\s*(true|false)\s*$", path.read_text())
    return match.group(1) == "true" if match else None


def bootstrap_save(repo_dir: Path, dt_jar_path: Path, game_dir: Path):
    """Creates a real, valid save at game_dir/saves/SAVE_NAME via a real dedicated server boot --
    the only way to get a genuinely valid level.dat without hand-writing NBT. Smoothing disabled
    GLOBALLY (not per-world) purely so its own unavoidable spawn-chunk pregeneration cannot
    contaminate the later no-smoothing witness; this never touches the per-world override this
    test is actually about."""
    config_dir = game_dir / 'config'
    config_dir.mkdir(parents=True, exist_ok=True)
    (config_dir / 'smoothslopes-server.toml').write_text("worldgenSmoothing = false\n")
    game_dir.mkdir(parents=True, exist_ok=True)
    (game_dir / 'eula.txt').write_text('eula=true\n')
    port, rcon_port = 25720, 25721
    (game_dir / 'server.properties').write_text(
        "server-ip=127.0.0.1\n"
        f"server-port={port}\n"
        "online-mode=false\n"
        "enforce-secure-profile=false\n"
        f"level-name={RELATIVE_SAVE_PATH}\n"
        "level-type=minecraft:normal\n"
        "level-seed=0\n"
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
    log_path = Path.cwd() / 'paired-client-acceptance-bootstrap-server.log'
    log = open(log_path, 'w')
    process = subprocess.Popen(
        ['./gradlew', 'runPairedServer', '--no-daemon', '-Dorg.gradle.jvmargs=-Xmx2G',
         f'-PdtJarPath={dt_jar_path}'],
        cwd=repo_dir, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    deadline = time.monotonic() + 300
    try:
        while time.monotonic() < deadline:
            text = log_path.read_text(errors='replace')
            if re.search(r'Done \(', text):
                break
            if re.search(r'Failed to start the minecraft server|ModLoadingException', text):
                raise RuntimeError(f"bootstrap server failed to start, see {log_path}:\n{text[-3000:]}")
            if process.poll() is not None:
                raise RuntimeError(f"bootstrap server exited before readiness, see {log_path}")
            time.sleep(2)
        else:
            raise RuntimeError(f"bootstrap server startup timeout, see {log_path}")
        rcon('127.0.0.1', rcon_port, RCON_PASSWORD, "stop")
        process.wait(timeout=90)
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=20)
        log.close()

    save_dir = game_dir / RELATIVE_SAVE_PATH
    if not (save_dir / 'level.dat').is_file():
        raise RuntimeError(f"bootstrap did not produce a valid save at {save_dir}")
    return save_dir


def main():
    repo_dir = Path(sys.argv[1]).resolve()
    dt_jar_path = Path(sys.argv[2]).resolve()
    game_dir = repo_dir / 'run'
    save_dir = game_dir / RELATIVE_SAVE_PATH
    if save_dir.exists():
        raise RuntimeError(f"refusing to reuse an existing save at {save_dir}")

    bootstrap_save(repo_dir, dt_jar_path, game_dir)

    # The real test starts here: ownFile must be absent and legacyFile must be the only thing
    # present, so the CLIENT's own mod construction is genuinely the first thing to migrate it.
    legacy_dir = save_dir / 'serverconfig'
    legacy_dir.mkdir(parents=True, exist_ok=True)
    own_file = legacy_dir / 'smoothslopes-server.toml'
    if own_file.exists():
        raise RuntimeError(f"bootstrap unexpectedly left {own_file} behind -- migration would not "
                            f"be genuinely tested")
    (legacy_dir / 'dynamicterrain-server.toml').write_text("worldgenSmoothing = false\n")

    # LIBGL_ALWAYS_SOFTWARE forces Mesa's llvmpipe software rasterizer: confirmed live that
    # without it, GLFW/LWJGL hangs indefinitely trying to create a GL context against Xvfb's own
    # (headless, no real GPU) GLX implementation -- the client log goes completely silent right
    # after the initial resource/texture-atlas load, forever, with no crash and no further
    # progress at all, never reaching quickplay's own joinSingleplayerWorld logic.
    client_env = dict(os.environ)
    client_env['LIBGL_ALWAYS_SOFTWARE'] = '1'
    log_path = Path.cwd() / 'paired-client-acceptance.log'
    log = open(log_path, 'w')
    process = subprocess.Popen(
        ['xvfb-run', '-a', './gradlew', 'runClient', '--no-daemon',
         '-Dorg.gradle.jvmargs=-Xmx2G', f'-PdtJarPath={dt_jar_path}', f'-PquickPlay={SAVE_NAME}'],
        cwd=repo_dir, stdout=log, stderr=subprocess.STDOUT, start_new_session=True, env=client_env)

    deadline = time.monotonic() + 420
    reached_world = False
    try:
        while time.monotonic() < deadline:
            text = log_path.read_text(errors='replace')
            if CRASH_MARKERS.search(text):
                raise RuntimeError(f"client failed to load, see {log_path}:\n{text[-4000:]}")
            if READY_MARKER.search(text):
                reached_world = True
                break
            if process.poll() is not None:
                raise RuntimeError(f"client exited before reaching the world, see {log_path}")
            time.sleep(2)
        if not reached_world:
            raise RuntimeError(f"client never reached the world within the time budget, see {log_path}")

        # Let real worldgen actually happen around spawn before stopping.
        time.sleep(30)
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=90)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
        log.close()

    migrated = read_worldgen_smoothing(own_file)
    if migrated is not False:
        raise RuntimeError(
            f"per-world legacy worldgenSmoothing=false did not migrate for the integrated-server "
            f"save {save_dir}: smoothslopes-server.toml has worldgenSmoothing={migrated}")

    region_dir = save_dir / 'region'
    if not scan_region_dir(region_dir, PLAIN_NEEDLE):
        raise RuntimeError(
            f"no ordinary terrain block found in {region_dir} -- worldgen may not have actually "
            f"run, which would make the no-smoothing witness below meaningless either way")
    if scan_region_dir(region_dir, LAYERED_NEEDLE):
        raise RuntimeError(
            f"per-world legacy worldgenSmoothing=false migrated correctly on disk, but a "
            f"dynamicterrain:layered_* block was still found in {region_dir}")

    print('{"passed": true, "save": "%s"}' % SAVE_NAME)


if __name__ == '__main__':
    main()
