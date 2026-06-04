"""Remove the wheels/ directory - it duplicated the site-packages
content and added ~140 MB of dead weight. The runtime does not
need it: site-packages.zip is the source of truth.
"""
import shutil
from pathlib import Path

wheels = Path(r"D:\plamclaw\PalmClaw-modded\app\src\main\assets\terminal\arm64-v8a\wheels")
if wheels.exists():
    size = sum(f.stat().st_size for f in wheels.rglob("*") if f.is_file())
    shutil.rmtree(wheels)
    print(f"removed {wheels} ({size / 1024 / 1024:.1f} MB)")
else:
    print("not present")
