#!/usr/bin/env bash
set -euo pipefail

root_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
acceptance_directory="$root_directory/featureframework-platform-acceptance"
if [[ -n "${PLATFORM_ACCEPTANCE_WORK_DIRECTORY:-}" ]]; then
    work_directory="$PLATFORM_ACCEPTANCE_WORK_DIRECTORY"
    created_work_directory=false
else
    work_directory="$(mktemp -d)"
    created_work_directory=true
fi
keep_work_directory="${PLATFORM_ACCEPTANCE_KEEP_WORK_DIRECTORY:-false}"
active_pids=()

cleanup() {
    local exit_code=$?
    for pid in "${active_pids[@]:-}"; do
        kill "$pid" >/dev/null 2>&1 || true
    done
    if [[ $exit_code -ne 0 ]]; then
        find "$work_directory" -maxdepth 3 -name '*.log' -type f \
            -print -exec tail -n 250 {} \; >&2 || true
    fi
    if [[ "$keep_work_directory" == "true" || "$created_work_directory" != "true" ]]; then
        echo "Platform acceptance logs retained in $work_directory" >&2
    else
        rm -rf "$work_directory"
    fi
    exit "$exit_code"
}
trap cleanup EXIT

fail() { echo "FeatureFramework acceptance failure: $*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"; }
property() { sed -n "s|.*<$1>\(.*\)</$1>.*|\1|p" "$root_directory/pom.xml" | head -n 1; }
wait_for_log() {
    local file=$1 expected=$2 platform=$3 deadline=$((SECONDS + 180))
    while (( SECONDS < deadline )); do
        grep -Eq -- "$expected" "$file" 2>/dev/null && return
        grep -Eq 'FEATUREFRAMEWORK_ACCEPTANCE_FAIL|Could not load plugin|Error occurred while enabling' \
            "$file" 2>/dev/null && fail "$platform reported an acceptance failure."
        sleep 1
    done
    fail "Timed out waiting for $platform log marker: $expected"
}
wait_for_exit() {
    local pid=$1 platform=$2 deadline=$((SECONDS + 45))
    while kill -0 "$pid" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
    kill -0 "$pid" 2>/dev/null && fail "$platform did not stop cleanly."
    wait "$pid" || fail "$platform process exited unsuccessfully."
}
download_runtime() {
    local project=$1 version=$2 build=$3 checksum=$4 destination=$5
    local metadata runtime_url
    metadata="$(curl --fail --silent --show-error --location \
        "https://fill.papermc.io/v3/projects/$project/versions/$version/builds")"
    runtime_url="$(jq --raw-output --argjson build "$build" \
        '.[] | select(.id == $build) | .downloads["server:default"].url' <<<"$metadata")"
    [[ -n "$runtime_url" && "$runtime_url" != "null" ]] \
        || fail "$project runtime build $build is unavailable."
    curl --fail --silent --show-error --location --output "$destination" "$runtime_url"
    [[ "$(sha256sum "$destination" | awk '{print $1}')" == "$checksum" ]] \
        || fail "$project runtime checksum mismatch."
}

run_paper() {
    local directory="$work_directory/paper"
    local plugin="$acceptance_directory/paper-plugin/target/featureframework-acceptance-paper.jar"
    [[ -f "$plugin" ]] || fail "Missing dummy Paper plugin: $plugin"
    mkdir -p "$directory/plugins"
    download_runtime paper "$(property paper.runtime.version)" "$(property paper.runtime.build)" \
        "$(property paper.runtime.sha256)" "$directory/paper.jar"
    cp "$plugin" "$directory/plugins/FeatureFrameworkAcceptancePaper.jar"
    printf '%s\n' 'eula=true' >"$directory/eula.txt"
    printf '%s\n' 'online-mode=false' 'spawn-protection=0' >"$directory/server.properties"
    mkfifo "$directory/console.in"
    (cd "$directory" && exec java -Xms256M -Xmx768M -jar paper.jar --nogui \
        <console.in >paper.log 2>&1) &
    local pid=$!
    active_pids+=("$pid")
    exec {paper_input}>"$directory/console.in"
    wait_for_log "$directory/paper.log" 'FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=paper' Paper
    printf 'stop\n' >&"$paper_input"
    wait_for_exit "$pid" Paper
    exec {paper_input}>&-
    wait_for_log "$directory/paper.log" 'FEATUREFRAMEWORK_ACCEPTANCE_STOPPED platform=paper' Paper
    active_pids=()
}

run_velocity() {
    local directory="$work_directory/velocity"
    local plugin="$acceptance_directory/velocity-plugin/target/featureframework-acceptance-velocity.jar"
    [[ -f "$plugin" ]] || fail "Missing dummy Velocity plugin: $plugin"
    mkdir -p "$directory/plugins"
    download_runtime velocity "$(property velocity.version)" "$(property velocity.runtime.build)" \
        "$(property velocity.runtime.sha256)" "$directory/velocity.jar"
    cp "$plugin" "$directory/plugins/FeatureFrameworkAcceptanceVelocity.jar"
    mkfifo "$directory/console.in"
    (cd "$directory" && exec java -Xms256M -Xmx768M -jar velocity.jar \
        <console.in >velocity.log 2>&1) &
    local pid=$!
    active_pids+=("$pid")
    exec {velocity_input}>"$directory/console.in"
    wait_for_log "$directory/velocity.log" 'FEATUREFRAMEWORK_ACCEPTANCE_PASS platform=velocity' Velocity
    printf 'end\n' >&"$velocity_input"
    wait_for_exit "$pid" Velocity
    exec {velocity_input}>&-
    wait_for_log "$directory/velocity.log" 'FEATUREFRAMEWORK_ACCEPTANCE_STOPPED platform=velocity' Velocity
    active_pids=()
}

for command in curl java jq sha256sum; do require "$command"; done
mkdir -p "$work_directory"
run_paper
run_velocity
echo "FeatureFramework Paper and Velocity platform acceptance passed."
