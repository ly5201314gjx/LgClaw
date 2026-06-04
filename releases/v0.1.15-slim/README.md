# LGClaw v0.1.15-slim

Slim debug APK with preinstalled Python wheels for offline use.

## What ships

| Asset                    | Size   | Notes                                       |
|--------------------------|--------|---------------------------------------------|
| rootfs.zip               | 114 MB | Termux rootfs + CPython 3.13 + uv           |
| offline-debs.zip         | 62 MB  | Termux .deb packages                        |
| toolchain.zip            | 29 MB  | base toolchain files                        |
| site-packages.zip        | 36 MB  | 23 preinstalled Python packages (DEFLATED)  |

## What it gets you

```bash
python3 -c "import requests, matplotlib, pyecharts, numpy, pillow; print('OK')"
```

No more 10-25 second `pip install` round-trips on a mobile network.

## Pre-installed packages

certifi, charset-normalizer, contourpy, cycler, fonttools, idna, jinja2,
kiwisolver, markupsafe, matplotlib, numpy, packaging, pillow, prettytable,
pyecharts, pyparsing, python-dateutil, pyyaml, requests, simplejson, six,
urllib3, wcwidth

## Install

```bash
adb install -r LGClaw-Pro-debug-v0.1.15-slim.apk
```

## Verify

```bash
sha256sum -c LGClaw-Pro-debug-v0.1.15-slim.apk.sha256
```

## Profile options

If you need more (pandas / scipy / lxml / cryptography / pyarrow / sympy /
mpmath / openpyxl / tzdata / pytz / tabulate / tqdm / colorama / jinja2 /
markupsafe), rebuild from source:

```bash
py scripts/build_python_offline.py --profile full
./gradlew assembleDebug
```

The `full` profile adds ~110 MB to site-packages.zip.
