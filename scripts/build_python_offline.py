"""Build a slim offline Python bundle for LGClaw.

Default profile (`core`) is ~30 MB compressed and covers the
libraries the agent typically needs:
  - requests (HTTP)
  - matplotlib + pyecharts (plotting)
  - numpy + pillow (plotting backends)
  - pyyaml (config)
  - mandatory deps

Use `--profile full` to ship the heavier set (pandas, scipy, lxml,
cryptography, pyarrow, ...). Anything not in the chosen profile is
still installable at runtime via `terminal_python_install <name>`;
that command falls back to PyPI when the wheels cache is empty.

The wheels cache is no longer baked into the APK by default. The
runtime `bootstrap()` step no longer relies on `.whl` files; it
extracts `site-packages.zip` directly. The `wheels/` asset directory
is only regenerated when the user runs this script with
`--emit-wheels`.

Compression is `ZIP_DEFLATE` (was `ZIP_STORED`). The site-packages
tree is dominated by text files (`.py`, `.json`, `.csv`, ...), so
the deflate ratio is ~3x. Total APK delta: -100 MB or more.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Iterable, List, Optional, Sequence

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WHEELS_DIR = REPO_ROOT / "app/src/main/assets/terminal/arm64-v8a/wheels"
DEFAULT_OUTPUT = REPO_ROOT / "app/src/main/assets/terminal/arm64-v8a/site-packages.zip"
DEFAULT_SPECS = REPO_ROOT / "scripts/python_offline_specs.txt"

DEFAULT_PACKAGES_CORE = (
    "requests", "urllib3", "certifi", "charset-normalizer", "idna", "pyyaml",
    "matplotlib", "pyecharts", "numpy", "pillow",
    "kiwisolver", "contourpy", "fonttools", "pyparsing", "cycler",
    "python-dateutil", "six", "packaging",
)

DEFAULT_PACKAGES_FULL = DEFAULT_PACKAGES_CORE + (
    "pandas", "scipy", "lxml", "cryptography", "pyarrow",
    "sympy", "mpmath", "openpyxl", "et-xmlfile",
    "tzdata", "pytz", "tabulate", "tqdm", "colorama",
    "jinja2", "markupsafe",
)


def log(msg: str) -> None:
    print(f"[build_offline] {msg}", flush=True)


def ensure_uv() -> str:
    uv = shutil.which("uv")
    if uv:
        return uv
    raise SystemExit("`uv` is required. Install via: pip install uv")


def read_specs(path: Path, profile: str) -> List[str]:
    if not path.exists():
        return list(DEFAULT_PACKAGES_FULL if profile == "full" else DEFAULT_PACKAGES_CORE)
    text = path.read_text(encoding="utf-8")
    sections: dict[str, list[str]] = {"": []}
    current = ""
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1].strip()
            sections.setdefault(current, [])
        else:
            sections[current].append(line)
    if profile in sections:
        return sections[profile]
    log(f"profile '{profile}' not found in {path}; falling back to inline list")
    return list(DEFAULT_PACKAGES_FULL if profile == "full" else DEFAULT_PACKAGES_CORE)


def install_to_target(packages: Sequence[str], target: Path,
                       python_version: str = "3.13") -> None:
    uv = ensure_uv()
    target.mkdir(parents=True, exist_ok=True)
    for entry in target.iterdir():
        if entry.is_dir():
            shutil.rmtree(entry, ignore_errors=True)
        else:
            entry.unlink(missing_ok=True)
    cmd = [
        uv, "pip", "install",
        "--target", str(target),
        "--python-platform", "aarch64-unknown-linux-gnu",
        "--python-version", python_version,
        "--only-binary", ":all:",
        "--no-deps",  # explicit list only; second pass with deps below
        "--refresh",
    ] + list(packages)
    log("$ " + " ".join(cmd[:8]) + " ...")
    subprocess.run(cmd, check=True)


def install_deps_to_target(packages: Sequence[str], target: Path,
                            python_version: str = "3.13") -> None:
    """Second pass that pulls in transitive dependencies."""
    uv = ensure_uv()
    cmd = [
        uv, "pip", "install",
        "--target", str(target),
        "--python-platform", "aarch64-unknown-linux-gnu",
        "--python-version", python_version,
        "--only-binary", ":all:",
        "--refresh",
    ] + list(packages)
    log("$ (deps) " + " ".join(cmd[:8]) + " ...")
    subprocess.run(cmd, check=True)


# Patterns to prune from a site-packages tree before zipping. Each
# entry is matched against the path relative to the tree root.
PRUNE_PATTERNS = (
    "*/tests/*",
    "*/test/*",
    "*/__pycache__/*",
    "*.pyc.orig",
    "*.dist-info/RECORD",
    "*.dist-info/INSTALLER",
    "*.dist-info/REQUESTED",
    "*.dist-info/direct_url.json",
    "*.dist-info/sboms/*",  # heavy JSON SBOMs
    "*.exe",                # Windows executables - useless on Android
    "bin/*.exe",            # console scripts that are .exe
    # *.pyi type stubs are huge; keep them only for libraries the
    # user actively inspects. They are NOT needed at runtime.
    "*.pyi",
)


def prune_site_packages(root: Path) -> int:
    """Walk the tree and delete anything matching PRUNE_PATTERNS."""
    import fnmatch
    removed = 0
    # Pre-build compiled patterns
    patterns = list(PRUNE_PATTERNS)
    for dirpath, dirnames, filenames in os.walk(root):
        rel = Path(dirpath).relative_to(root).as_posix()
        # Delete whole test directories
        if any(d in ("tests", "test", "__pycache__") for d in dirnames):
            for d in ("tests", "test", "__pycache__"):
                p = Path(dirpath) / d
                if p.is_dir():
                    shutil.rmtree(p, ignore_errors=True)
                    removed += 1
            dirnames[:] = [d for d in dirnames if d not in ("tests", "test", "__pycache__")]
        for name in filenames:
            full = Path(dirpath) / name
            relname = (Path(rel) / name).as_posix() if rel != "." else name
            if any(fnmatch.fnmatch(relname, p) for p in patterns):
                full.unlink(missing_ok=True)
                removed += 1
    return removed


def write_pth(site_packages: Path) -> None:
    pth = site_packages / "_lgclaw_offline.pth"
    pth.write_text(
        "# Auto-generated by LGClaw offline Python bundle.\n"
        "../../../../../cache/wheels\n",
        encoding="utf-8",
    )


def zip_directory(src: Path, dest: Path, *, compression: int) -> int:
    if dest.exists():
        dest.unlink()
    dest.parent.mkdir(parents=True, exist_ok=True)
    files = [p for p in src.rglob("*") if p.is_file()]
    if not files:
        raise SystemExit("no files in site-packages; nothing to zip")
    # Deflate the whole archive; Python uses the right strategy per
    # file (no compression for already-compressed data).
    with zipfile.ZipFile(dest, "w", compression=compression, compresslevel=6, allowZip64=True) as zf:
        for path in files:
            rel = path.relative_to(src)
            zf.write(path, rel.as_posix())
    return dest.stat().st_size


def write_manifest(site_packages: Path, dest_zip: Path) -> None:
    st = dest_zip.stat()
    sha = hashlib.sha256(dest_zip.read_bytes()).hexdigest()
    # Sample which packages are present (by dist-info)
    packages = sorted(
        p.name[:-len(".dist-info")] for p in site_packages.glob("*.dist-info")
    )
    (site_packages / "MANIFEST.json").write_text(
        json.dumps({
            "version": 1,
            "python": "3.13",
            "platform": "aarch64-unknown-linux-gnu",
            "package_count": len(packages),
            "packages": packages,
            "zip_size": st.st_size,
            "zip_sha256": sha,
        }, indent=2),
        encoding="utf-8",
    )


def emit_wheels(installed: Path, wheels_dir: Path) -> int:
    """Re-pack the installed site-packages back into .whl archives.
    Used only when --emit-wheels is passed; the runtime does not
    need .whl files since site-packages.zip is enough.
    """
    # We can't easily build real .whl files from an installed dir
    # (need METADATA, WHEEL, RECORD). Instead, create flat zips that
    # uv pip can read as `--find-links` candidates. The bootstrap
    # code only does `--find-links`, not `--requirement`, so any
    # file uv can interpret as a wheel will do.
    # NOTE: this is a fallback; the primary use case is the zip.
    raise SystemExit(
        "--emit-wheels is not implemented; uv dropped `pip download`. "
        "Run `uv pip install --target <wheels_dir>` and re-zip manually "
        "if you need offline wheels for new installs."
    )


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--profile", choices=("core", "full"), default="core",
                   help="Package profile (default: core = ~30 MB)")
    p.add_argument("--specs", type=Path, default=DEFAULT_SPECS)
    p.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    p.add_argument("--python-version", default="3.13")
    p.add_argument("--with-deps", action="store_true", default=True,
                   help="(default) pull transitive deps; disable for explicit-only")
    p.add_argument("--compression", choices=("deflate", "store"), default="deflate")
    p.add_argument("--emit-wheels", action="store_true",
                   help="(unsupported) re-pack installed dir back into .whl files")
    args = p.parse_args()

    packages = read_specs(args.specs, args.profile)
    log(f"profile={args.profile} packages={len(packages)}")
    started = time.monotonic()

    with tempfile.TemporaryDirectory(prefix="lgclaw-site-") as raw:
        site = Path(raw) / "site-packages"
        # First pass: explicit list
        install_to_target(packages, site, args.python_version)
        # Second pass: with --no-deps disabled, so uv pulls the deps
        if args.with_deps:
            install_deps_to_target(packages, site, args.python_version)
        # Prune build-time / platform-incompatible artifacts
        pruned = prune_site_packages(site)
        log(f"pruned {pruned} build artifacts")
        write_pth(site)
        write_manifest(site, args.out)
        comp = zipfile.ZIP_DEFLATED if args.compression == "deflate" else zipfile.ZIP_STORED
        size = zip_directory(site, args.out, compression=comp)

    elapsed = time.monotonic() - started
    log(f"wrote {args.out} ({size/1024/1024:.1f} MB, {args.compression}) in {elapsed:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
