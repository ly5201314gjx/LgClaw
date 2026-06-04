"""Report per-package size in a uv pip install --target directory."""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    print("usage: package_sizes.py <site_packages_dir>")
    sys.exit(1)

root = Path(sys.argv[1])
# Each package has a .dist-info/ directory
total = 0
rows = []
for dist_info in sorted(root.glob("*.dist-info")):
    name = dist_info.name.replace(".dist-info", "")
    # Find the package module(s) (top-level dirs/files in site-packages)
    pkg_size = 0
    metadata = dist_info / "METADATA"
    if metadata.exists():
        for line in metadata.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("Name:"):
                name = line.split(":", 1)[1].strip()
                break
    # The actual code dir is at the same level as dist-info, named after the
    # distribution or with a different import name.
    pkg_size = 0
    # Try matching by dist-info name
    for entry in root.iterdir():
        if entry == dist_info:
            continue
        if entry.name.startswith(name + "-") or entry.name == name:
            if entry.is_dir():
                pkg_size += sum(f.stat().st_size for f in entry.rglob("*") if f.is_file())
        elif entry.is_dir() and name.lower().replace("-", "_") == entry.name.lower().replace("-", "_"):
            pkg_size += sum(f.stat().st_size for f in entry.rglob("*") if f.is_file())
    # Plus the dist-info itself
    pkg_size += sum(f.stat().st_size for f in dist_info.rglob("*") if f.is_file())
    rows.append((pkg_size, name))

rows.sort(reverse=True)
print(f"{'PACKAGE':<35} {'SIZE':>10}")
print("-" * 47)
for size, name in rows:
    print(f"{name:<35} {size/1024/1024:>9.1f} MB")
    total += size
print("-" * 47)
print(f"{'TOTAL':<35} {total/1024/1024:>9.1f} MB")
