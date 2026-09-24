#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
	printf 'Usage: %s JADx_GUI INPUT_PROJECT OUTPUT_PROJECT\n' "$0" >&2
	exit 2
fi

gui_path=$1
input_project=$2
output_project=$3
output_dir=$(dirname "$output_project")
output_name=$(basename "$output_project")

command -v xvfb-run >/dev/null
command -v xdotool >/dev/null
[[ -x "$gui_path" ]]
[[ -f "$input_project" ]]
mkdir -p "$output_dir"
rm -f "$output_project"

xvfb-run -a -s '-screen 0 1280x1024x24' bash -c '
set -euo pipefail
gui_path=$1
input_project=$2
output_name=$3
output_project=$4
output_dir=$(dirname "$output_project")
gui_log=/tmp/libjadx-gui-roundtrip.log
: > "$gui_log"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=$output_dir" "$gui_path" "$input_project" >"$gui_log" 2>&1 &
gui_pid=$!
cleanup() {
	kill "$gui_pid" 2>/dev/null || true
}
trap cleanup EXIT

window_id=""
for _ in $(seq 1 60); do
	window_id=$(xdotool search --onlyvisible --name "jadx-gui" 2>/dev/null | head -n 1 || true)
	if [[ -n "$window_id" ]]; then
		break
	fi
	sleep 1
done
[[ -n "$window_id" ]]
eval "$(xdotool getwindowgeometry --shell "$window_id")"
for _ in $(seq 1 60); do
	if grep -q "Loaded classes:" "$gui_log"; then
		break
	fi
	sleep 1
done
grep -q "Loaded classes:" "$gui_log"
sleep 1

# Open File > Save project as. The GUI opens its native project directory in
# the chooser; entering only the output basename exercises native path rules.
xdotool mousemove "$((X + 20))" "$((Y + 10))" click 1
sleep 2
xdotool mousemove --sync "$((X + 82))" "$((Y + 166))"
sleep 1
xdotool click 1

dialog_id=""
for _ in $(seq 1 20); do
	dialog_id=$(xdotool search --onlyvisible --name "Save project" 2>/dev/null | head -n 1 || true)
	if [[ -n "$dialog_id" ]]; then
		break
	fi
	sleep 1
done
[[ -n "$dialog_id" ]]
eval "$(xdotool getwindowgeometry --shell "$dialog_id")"
xdotool mousemove "$((X + WIDTH / 2))" "$((Y + HEIGHT - 93))" click 1
xdotool key ctrl+a
xdotool type --delay 20 "$output_name"
xdotool mousemove "$((X + WIDTH - 110))" "$((Y + HEIGHT - 23))" click 1

for _ in $(seq 1 30); do
	if [[ -f "$output_project" ]]; then
		exit 0
	fi
	sleep 1
done
exit 1
' _ "$gui_path" "$input_project" "$output_name" "$output_project"
