#!/usr/bin/env bash
# Run from the checkout; populate uv's cache first for offline installations.
set -euo pipefail
cd "$(dirname "$0")/.."
uv sync --frozen --all-groups
uv run python scripts/check_generated.py
uv run ruff format --check .
uv run ruff check .
uv run mypy src/libjadx
uv run pytest -q tests/unit tests/integration
uv build
