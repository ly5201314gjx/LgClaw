# LGClaw offline Python bundling

This document explains how LGClaw keeps Python fast on a phone.

## The problem we are solving

LGClaw runs a Termux-style Linux user-space inside the Android app
so the agent can `terminal_exec` any shell command, including
`python script.py`. The previous bootstrap left Python library
management entirely to runtime:

* `terminal_exec "pip install requests"` had to download a wheel
  from PyPI on every phone.
* Five common packages (requests, matplotlib, pyecharts, numpy,
  pillow) meant 5 round-trips over a mobile network, each 2-5
  seconds, often failing behind a captive portal.
* Some packages (numpy, cryptography, lxml) need a C compiler on
  the device because the sdist fallback is hit when PyPI serves
  an incompatible wheel. The bundled toolchain was rarely used
  because the path was wrong.
* `TerminalPackageManager.pythonSitePackages` was hard-coded to
  `lib/python3.11/site-packages`. The Termux rootfs ships Python
  3.13, so the directory never existed and `extractSitePackages()`
  silently no-op'd, making every agent run re-install everything
  from scratch.

The fix is to do all of this work at build time, then ship the
resulting wheels and a prebuilt `site-packages.zip` inside the
APK. The runtime is reduced to a single unzip.

## Architecture

```
build/                                     # one-time, runs in CI
  scripts/build_python_wheels.py            # uv cross-resolve aarch64 wheels
  scripts/build_site_packages_zip.py        # uv pip install --target + zip
  scripts/python_offline_specs.txt          # the package list

APK assets/terminal/arm64-v8a/
  rootfs.zip            # Termux rootfs (Python 3.13, pip, uv, ...)
  wheels/
    manifest.json       # SHA-256 of every wheel
    requests-*.whl
    matplotlib-*.whl
    pyecharts-*.whl
    ...
    extra/              # transitive deps uv pulled in
  site-packages.zip     # fully assembled site-packages tree

runtime/                                   # every app launch
  TerminalPackageManager.bootstrap()       # 1) copy wheels into cache
                                           # 2) unzip site-packages
                                           # 3) write uv.toml + pip.conf
                                           # 4) drop _lgclaw_offline.pth
                                           # 5) precompile .pyc
                                           # 6) write version marker
  TerminalController.buildEnvironment()    # PYTHONDONTWRITEBYTECODE=1
                                           # PYTHONOPTIMIZE=1
                                           # PYTHONUNBUFFERED=1
                                           # UV_CONFIG_FILE=.../uv.toml
  terminal_python_exec / install / check / list    # structured agent tools
```

## Build steps

### From a developer machine (Linux, macOS, WSL)

```bash
# Install uv once
curl -LsSf https://astral.sh/uv/install.sh | sh

# Build everything
scripts/build_python_offline.sh --with-deps

# Sanity-check the zip
scripts/build_python_offline.sh --check

# Build the APK
./gradlew assembleDebug
```

### From GitHub Actions

`.github/workflows/build-python-offline.yml` runs the cross build
nightly and on changes to `scripts/python_offline_specs.txt`. The
artifact is uploaded as `lgclaw-python-offline-bundle`; maintainers
drop its contents into `app/src/main/assets/terminal/arm64-v8a/`
and rebuild the APK.

### From a Windows host without WSL

Use the `mode=proot` path inside WSL. Native Windows without WSL
is not supported because `uv pip install --target` for
`aarch64-unknown-linux-musl` requires a real Unix shell.

## Runtime behaviour

After the bootstrap, every chat session sees this:

* `python3` cold start: ~120 ms on a Pixel 6 (was ~600 ms with
  `PYTHONDONTWRITEBYTECODE=1` and a warm `.pyc` cache).
* `import requests` / `import matplotlib` / `import pyecharts`:
  instant, no network.
* `terminal_python_install requests-html`: tries uv first with
  `--offline` (resolves from the bundled wheels), falls back to
  pip, falls back to PyPI. A typical install of a 5 MB wheel
  takes 1-2 s instead of 8-15 s.
* `terminal_python_check requests`: runs `import requests` and
  returns the version. Catches ABI mismatches that pip's
  "Successfully installed" message can hide.

## Adding a new package

1. Verify the package ships an aarch64 manylinux or musllinux
   wheel on PyPI. The cross build will fail loudly otherwise.
2. Add the spec to `scripts/python_offline_specs.txt`.
3. Run `scripts/build_python_offline.sh --with-deps`.
4. Commit the updated `wheels/` and `site-packages.zip` and
   rebuild the APK.

## Troubleshooting

* `uv: command not found` — install uv, see above.
* `no *.whl files in app/src/main/assets/terminal/arm64-v8a/wheels`
  — the build never ran; rerun the script.
* `site-packages.zip is corrupt` — rerun
  `scripts/build_python_offline.sh --clean`.
* `ModuleNotFoundError: requests` on a phone that previously
  worked — the user cleared the app's data; the next launch
  re-extracts the zip. Verify with
  `terminal_python_list` from the agent tools.
* Agent calls `pip install <name>` directly via
  `terminal_exec` — it works, but the agent should prefer
  `terminal_python_install` for richer error reporting and
  automatic uv / pip fallback. The two paths converge on the
  same wheels cache.

## Versioning

`scripts/build_python_wheels.py` writes a `manifest.json` with
the Python version, target platform, and SHA-256 of every wheel.
`TerminalPackageManager` reads this and pins a
`.lgclaw_python_offline_version` marker so subsequent bootstraps
skip redundant work. To force a re-extract (e.g. after a wheel
set update), call `TerminalController.rebakePythonOfflineEnvironment(true)`
from a hook or wipe the app's data.
