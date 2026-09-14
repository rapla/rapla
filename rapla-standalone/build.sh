#!/usr/bin/env bash
# PRD 054 — WSL convenience wrapper for build.ps1.
#
# Invokes the Windows-native PowerShell build pipeline from a WSL shell.
# Requires the rapla repo to be reachable from Windows — either:
#   - cloned on the Windows side at e.g. C:\git\rapla\        (recommended)
#   - or accessed via \\wsl$\... from the Windows PowerShell  (slower)
#
# Pass-through: every arg is forwarded to build.ps1.
#   ./build.sh              # full pipeline, unsigned
#   ./build.sh -Sign        # full pipeline, signed via YubiKey
#   ./build.sh -SkipMaven   # skip Maven step

set -euo pipefail

# Resolve our own location (rapla-standalone/) and translate to Windows path.
here="$(cd "$(dirname "$0")" && pwd)"
case "$here" in
    /mnt/[a-z]/*)
        # Repo lives on the Windows filesystem mounted via WSL — fast both ways.
        drive="${here:5:1}"
        rest="${here:7}"
        win_here="${drive^^}:\\$(echo "$rest" | tr '/' '\\')"
        ;;
    *)
        # Repo lives on the WSL filesystem — Windows reaches it via \\wsl$\.
        # Slow for cargo builds; first-time setup of rapla on Windows side
        # is strongly recommended for routine work.
        distro="$(wslvar WSL_DISTRO_NAME 2>/dev/null || echo "$WSL_DISTRO_NAME")"
        win_here="\\\\wsl$\\$distro$(echo "$here" | tr '/' '\\')"
        echo "warning: rapla is on the WSL filesystem ($here)"
        echo "         cargo build will be ~5-10× slower than from C:\\..."
        echo "         see rapla-standalone/README.md for the fast-path setup"
        ;;
esac

# Forward all args to build.ps1 inside the Windows-side path.
exec powershell.exe -ExecutionPolicy Bypass -File "$win_here\\build.ps1" "$@"
