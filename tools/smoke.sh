#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
./build.sh >/dev/null || { echo "build failed" >&2; exit 1; }
. tools/smoke-lib.sh

dir="$(mktemp -d)"; mkdir -p "$dir/dist/a"
printf 'x' >"$dir/dist/a/app.jar"
printf '{"operation": "copy", "pattern": "dist/**/*.jar", "target": "release", "flatten": "true"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "copy failed: $(cat "$dir/output.json")"
[ -f "$dir/release/app.jar" ] || fail "flattened copy missing"

dir="$(mktemp -d)"; printf 'x' >"$dir/keep.txt"; printf 'y' >"$dir/drop.txt"
printf '{"operation": "delete", "pattern": "drop.txt"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "delete failed"
[ ! -f "$dir/drop.txt" ] && [ -f "$dir/keep.txt" ] || fail "delete touched the wrong file"

dir="$(mktemp -d)"; printf 'x' >"$dir/a.txt"
printf '{"operation": "copy", "pattern": "a.txt", "target": "../escape"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 && fail "a target outside the workspace must be refused"

dir="$(mktemp -d)"
printf '{"operation": "mkdir", "target": "out/nested"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "mkdir failed"
[ -d "$dir/out/nested" ] || fail "mkdir"

echo "smoke ok: copy+flatten, delete, workspace guard, mkdir"
