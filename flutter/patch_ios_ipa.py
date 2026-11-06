"""Normalize Runner.app for iOS 14.0+ TrollStore and sideload compatibility."""
from pathlib import Path
import plistlib
import struct
import subprocess
import sys


def patch_plists(app_dir: Path) -> None:
    for plist_path in app_dir.rglob("*.plist"):
        try:
            raw = plist_path.read_bytes()
            fmt = plistlib.FMT_BINARY if raw.startswith(b"bplist00") else plistlib.FMT_XML
            data = plistlib.loads(raw)
            if not isinstance(data, dict):
                continue
            min_ver = data.get("MinimumOSVersion")
            if isinstance(min_ver, str):
                parts = [int(x) for x in min_ver.split(".") if x.isdigit()]
                if parts and parts[0] > 14:
                    data["MinimumOSVersion"] = "14.0"
                    plist_path.write_bytes(plistlib.dumps(data, fmt=fmt))
        except Exception:
            pass


def patch_macho(bin_path: Path) -> None:
    if not bin_path.is_file() or bin_path.is_symlink():
        return
    raw = bin_path.read_bytes()
    if len(raw) < 32:
        return
    magic = struct.unpack_from("<I", raw, 0)[0]
    if magic in (0xBEBAFECA, 0xCAFEBABE):
        subprocess.run(
            ["lipo", "-thin", "arm64", str(bin_path), "-output", str(bin_path)],
            check=True,
        )
        raw = bin_path.read_bytes()
        magic = struct.unpack_from("<I", raw, 0)[0]
    if magic != 0xFEEDFACF:
        return
    buf = bytearray(raw)
    ncmds = struct.unpack_from("<I", buf, 16)[0]
    pos = 32
    changed = False
    for _ in range(ncmds):
        if pos + 8 > len(buf):
            break
        cmd, cmdsize = struct.unpack_from("<II", buf, pos)
        if cmdsize < 8 or pos + cmdsize > len(buf):
            break
        if cmd == 0x32 and cmdsize >= 24:  # LC_BUILD_VERSION
            platform, minos, sdk, ntools = struct.unpack_from("<IIII", buf, pos + 8)
            if minos > 0x000E0000:
                struct.pack_into("<I", buf, pos + 12, 0x000E0000)
                changed = True
        pos += cmdsize
    if changed:
        bin_path.write_bytes(bytes(buf))


def codesign_bundle(app_dir: Path) -> None:
    fw_dir = app_dir / "Frameworks"
    if fw_dir.exists():
        for item in sorted(fw_dir.iterdir()):
            if item.suffix in (".framework", ".dylib"):
                subprocess.run(
                    ["codesign", "--force", "--sign", "-", "--timestamp=none", str(item)],
                    check=True,
                )
    entitlements = app_dir.parent / "trollstore.entitlements"
    entitlements.write_bytes(
        plistlib.dumps(
            {
                "application-identifier": "TROLLSTORE.com.pilipala.marcoMovie",
                "com.apple.developer.team-identifier": "TROLLSTORE",
                "get-task-allow": True,
            },
            fmt=plistlib.FMT_XML,
        )
    )
    subprocess.run(
        [
            "codesign",
            "--force",
            "--sign",
            "-",
            "--timestamp=none",
            "--entitlements",
            str(entitlements),
            str(app_dir),
        ],
        check=True,
    )


def main() -> None:
    app_dir = Path(sys.argv[1]).resolve()
    patch_plists(app_dir)
    patch_macho(app_dir / "Runner")
    fw_dir = app_dir / "Frameworks"
    if fw_dir.exists():
        for fw in sorted(fw_dir.glob("*.framework")):
            binary = fw / fw.stem
            if binary.exists():
                patch_macho(binary)
    codesign_bundle(app_dir)


if __name__ == "__main__":
    main()
