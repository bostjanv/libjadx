#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
exec "${RELEASE_PYTHON:-python3}" tests/release/qualify.py "$@"
