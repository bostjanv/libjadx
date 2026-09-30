#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    printf 'Usage: %s JADX_GUI INPUT_PROJECT OUTPUT_PROJECT GUI_CONFIG\n' "$0" >&2
    exit 2
fi
gui_path=$1
input_project=$2
output_project=$3
gui_config=$4
for tool in xvfb-run xdotool xclip import; do command -v "$tool" >/dev/null; done
[[ -x "$gui_path" && -f "$input_project" && -f "$gui_config" ]]
[[ $("$gui_path" --version) == 1.5.6 ]]
mkdir -p "$(dirname "$output_project")"
rm -f "$output_project"

xvfb-run -a -s '-screen 0 1280x1024x24' bash -c '
set -euo pipefail
gui_path=$1
input_project=$2
output_project=$3
gui_config=$4
output_dir=$(dirname "$output_project")
gui_log=$output_dir/actual-gui.log
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=$output_dir" \
    "$gui_path" --config "$gui_config" --select-class probe.LocalShapes "$input_project" >"$gui_log" 2>&1 &
gui_pid=$!
trap '\''kill "$gui_pid" 2>/dev/null || true'\'' EXIT
window_id=""
for _ in $(seq 1 60); do
    window_id=$(xdotool search --onlyvisible --name "jadx-gui" 2>/dev/null | head -n 1 || true)
    if [[ -n "$window_id" ]] && grep -q "Loaded classes:" "$gui_log"; then break; fi
    sleep 1
done
[[ -n "$window_id" ]]
grep -q "Loaded classes:" "$gui_log"
eval "$(xdotool getwindowgeometry --shell "$window_id")"

# Capture the actual editor text. Save/reopen alone would not prove what the GUI
# generated under this global configuration. Selection and clipboard are read-only.
editor_text=$output_dir/gui-source.java
for _ in $(seq 1 30); do
    xdotool mousemove "$((X + WIDTH * 3 / 4))" "$((Y + HEIGHT / 2))" click 1
    xdotool key ctrl+a ctrl+c
    sleep 1
    xclip -selection clipboard -o >"$editor_text" 2>/dev/null || true
    if grep -q "class LocalShapes" "$editor_text"; then break; fi
done
grep -q "class LocalShapes" "$editor_text"
import -window root "$output_dir/gui-source.png"

xdotool mousemove "$((X + 20))" "$((Y + 10))" click 1
sleep 2
xdotool mousemove --sync "$((X + 82))" "$((Y + 166))"
sleep 1
xdotool click 1
dialog_id=""
for _ in $(seq 1 20); do
    dialog_id=$(xdotool search --onlyvisible --name "Save project" 2>/dev/null | head -n 1 || true)
    if [[ -n "$dialog_id" ]]; then break; fi
    sleep 1
done
[[ -n "$dialog_id" ]]
eval "$(xdotool getwindowgeometry --shell "$dialog_id")"
xdotool mousemove "$((X + WIDTH / 2))" "$((Y + HEIGHT - 93))" click 1
xdotool key ctrl+a
# Single-input GUI suggests the input directory even when the native project is
# elsewhere. An absolute destination keeps each diagnostic in its own folder.
xdotool type --delay 5 "$output_project"
xdotool mousemove "$((X + WIDTH - 110))" "$((Y + HEIGHT - 23))" click 1
for _ in $(seq 1 30); do
    if [[ -f "$output_project" ]]; then exit 0; fi
    sleep 1
done
exit 1
' _ "$gui_path" "$input_project" "$output_project" "$gui_config"
