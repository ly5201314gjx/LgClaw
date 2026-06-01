#!/usr/bin/env python3
import io
import lzma
import os
import shutil
import stat
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path


OLD_ROOT = b"/data/data/com.termux"
NEW_ROOT = b"/data/data/com.lgclaw"
OLD_PREFIX = b"/data/data/com.termux/files/usr"
NEW_PREFIX = b"/data/data/com.lgclaw/files/usr"


def read_ar_members(path: Path):
    data = path.read_bytes()
    if not data.startswith(b"!<arch>\n"):
        raise ValueError(f"{path} is not an ar archive")
    offset = 8
    while offset + 60 <= len(data):
        header = data[offset:offset + 60]
        name = header[:16].decode("utf-8", "replace").strip().rstrip("/")
        size = int(header[48:58].decode("ascii").strip())
        start = offset + 60
        payload = data[start:start + size]
        yield name, payload
        offset = start + size + (size % 2)


def prefix_relative_name(name: str) -> str:
    cleaned = name.lstrip("./").replace("\\", "/")
    for prefix in (
        "data/data/com.termux/files/usr/",
        "data/data/com.lgclaw/files/usr/",
    ):
        if cleaned.startswith(prefix):
            return cleaned[len(prefix):]
    return cleaned


def safe_target(root: Path, name: str) -> Path:
    cleaned = prefix_relative_name(name)
    target = (root / cleaned).resolve()
    root_resolved = root.resolve()
    if root_resolved not in target.parents and target != root_resolved:
        raise ValueError(f"unsafe archive path: {name}")
    return target


def extract_zip_tree(zip_path: Path, root: Path, symlinks: list[tuple[str, str]]):
    with zipfile.ZipFile(zip_path) as zf:
        for info in zf.infolist():
            name = info.filename
            if not name or name.endswith("/"):
                safe_target(root, name).mkdir(parents=True, exist_ok=True)
                continue
            if name == "SYMLINKS.txt":
                text = zf.read(info).decode("utf-8", "replace")
                for raw in text.splitlines():
                    if "←" not in raw:
                        continue
                    link_target, link_path = raw.split("←", 1)
                    symlinks.append((link_target.strip(), link_path.strip()))
                continue
            target = safe_target(root, name)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(zf.read(info))
            mode = (info.external_attr >> 16) & 0o777
            if mode:
                os.chmod(target, mode)


def extract_deb_data(deb_path: Path, root: Path, symlinks: list[tuple[str, str]]):
    data_member = None
    for name, payload in read_ar_members(deb_path):
        if name.startswith("data.tar"):
            data_member = name, payload
            break
    if data_member is None:
        raise ValueError(f"{deb_path} has no data.tar member")

    name, payload = data_member
    if name.endswith(".xz"):
        tar_bytes = lzma.decompress(payload)
    else:
        tar_bytes = payload

    with tarfile.open(fileobj=io.BytesIO(tar_bytes), mode="r:*") as tf:
        for member in tf.getmembers():
            if member.isdir():
                safe_target(root, member.name).mkdir(parents=True, exist_ok=True)
                continue
            if member.issym() or member.islnk():
                link_path = "./" + prefix_relative_name(member.name)
                symlinks.append((member.linkname, link_path))
                continue
            if not member.isfile():
                continue
            target = safe_target(root, member.name)
            target.parent.mkdir(parents=True, exist_ok=True)
            source = tf.extractfile(member)
            if source is None:
                continue
            with source, target.open("wb") as out:
                shutil.copyfileobj(source, out)
            os.chmod(target, member.mode & 0o777)


def patch_prefixes(root: Path):
    replacements = [(OLD_ROOT, NEW_ROOT), (OLD_PREFIX, NEW_PREFIX)]
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        size = path.stat().st_size
        if size <= 0 or size > 128 * 1024 * 1024:
            continue
        data = path.read_bytes()
        patched = data
        for old, new in replacements:
            if len(old) == len(new):
                patched = patched.replace(old, new)
        if patched != data:
            path.write_bytes(patched)


def mark_known_executables(root: Path):
    executable_roots = ["bin", "libexec", "lib/apt", "lib/dpkg"]
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(root).as_posix()
        if any(rel.startswith(prefix + "/") for prefix in executable_roots) or rel.endswith(".sh"):
            path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def write_rootfs_zip(root: Path, symlinks: list[tuple[str, str]], output: Path):
    if output.exists():
        output.unlink()
    output.parent.mkdir(parents=True, exist_ok=True)
    seen_links = []
    seen = set()
    for target, link in symlinks:
        patched_target = target.replace(OLD_ROOT.decode(), NEW_ROOT.decode()).replace(
            OLD_PREFIX.decode(), NEW_PREFIX.decode()
        )
        key = (patched_target, link)
        if key not in seen:
            seen.add(key)
            seen_links.append(key)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        zf.writestr(
            "SYMLINKS.txt",
            "\n".join(f"{target}←{link}" for target, link in seen_links) + "\n",
        )
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(rel)
            info.external_attr = (path.stat().st_mode & 0o777) << 16
            with path.open("rb") as fh:
                zf.writestr(info, fh.read(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)


def main():
    repo = Path(__file__).resolve().parents[1]
    asset_dir = repo / "app/src/main/assets/terminal/arm64-v8a"
    toolchain_zip = asset_dir / "toolchain.zip"
    deb_dir = repo / "build/terminal-debs/debs"
    output = asset_dir / "rootfs.zip"
    if not toolchain_zip.exists():
        raise SystemExit(f"missing {toolchain_zip}")
    if not deb_dir.exists():
        raise SystemExit(f"missing {deb_dir}")

    with tempfile.TemporaryDirectory(prefix="lgclaw-rootfs-") as tmp:
        root = Path(tmp) / "rootfs"
        root.mkdir(parents=True)
        symlinks: list[tuple[str, str]] = []
        extract_zip_tree(toolchain_zip, root, symlinks)
        debs = sorted(deb_dir.glob("*.deb"))
        if not debs:
            raise SystemExit(f"no deb files in {deb_dir}")
        for index, deb in enumerate(debs, 1):
            print(f"[{index}/{len(debs)}] {deb.name}")
            extract_deb_data(deb, root, symlinks)
        patch_prefixes(root)
        mark_known_executables(root)
        write_rootfs_zip(root, symlinks, output)
        print(f"wrote {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
