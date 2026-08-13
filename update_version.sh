#!/usr/bin/env bash
set -euo pipefail

readonly POM_FILE="pom.xml"
readonly VERSION_PROPERTY="revision"
readonly TIMESTAMP_PROPERTY="project.build.outputTimestamp"
readonly VERSIONS_PLUGIN="org.codehaus.mojo:versions-maven-plugin:2.18.0"
readonly MODULES=(
  featureframework-testkit
  featureframework-mockito-testkit
  featureframework-api
  featureframework-shared
  featureframework-paper
  featureframework-velocity
)

die() {
  echo "Error: $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage: ./update_version.sh <major|minor|patch>

Bumps FeatureFramework's reactor revision and reproducible-build timestamp, verifies the
complete platform-acceptance gate, then creates a local release commit and annotated tag.
USAGE
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || die "${path} not found."
}

resolve_version() {
  local module="${1:-}"
  local -a module_args=()
  local version
  if [[ -n "$module" ]]; then
    module_args=(-pl "$module")
  fi
  version="$(
    ./mvnw -q -ntp "${module_args[@]}" -DforceStdout help:evaluate -Dexpression=project.version \
      | awk '/^[0-9]+\.[0-9]+\.[0-9]+$/ { print; exit }'
  )"
  [[ -n "$version" ]] || die "Unable to resolve a semantic Maven version${module:+ for ${module}}."
  printf '%s\n' "$version"
}

bump_semver() {
  local semver="$1"
  local bump_type="$2"
  local major minor patch
  [[ "$semver" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] \
    || die "Current version must be semantic (X.Y.Z), got '${semver}'."
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  case "$bump_type" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
    *) usage; exit 1 ;;
  esac
  printf '%s.%s.%s\n' "$major" "$minor" "$patch"
}

update_build_timestamp() {
  local timestamp="$1"
  local temporary_file
  temporary_file="$(mktemp "${POM_FILE}.XXXXXX")"
  awk -v timestamp="$timestamp" '
    BEGIN { replaced = 0 }
    {
      if (!replaced && $0 ~ /<project\.build\.outputTimestamp>[^<]+<\/project\.build\.outputTimestamp>/) {
        sub(/<project\.build\.outputTimestamp>[^<]+<\/project\.build\.outputTimestamp>/,
            "<project.build.outputTimestamp>" timestamp "</project.build.outputTimestamp>")
        replaced = 1
      }
      print
    }
    END { if (!replaced) exit 2 }
  ' "$POM_FILE" >"$temporary_file" || {
    rm -f "$temporary_file"
    die "Could not update ${TIMESTAMP_PROPERTY} in ${POM_FILE}."
  }
  mv "$temporary_file" "$POM_FILE"
}

if [[ $# -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
  usage
  exit 0
fi
[[ $# -eq 1 ]] || { usage; exit 1; }
[[ "$1" == "major" || "$1" == "minor" || "$1" == "patch" ]] || { usage; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Run this script inside the repository."

REPOSITORY_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPOSITORY_ROOT"
for required in "$POM_FILE" "mvnw"; do
  require_file "$required"
done
[[ -z "$(git status --porcelain)" ]] || die "Working tree is not clean. Commit or stash changes first."

current_version="$(resolve_version)"
new_version="$(bump_semver "$current_version" "$1")"
new_tag="v${new_version}"
git rev-parse -q --verify "refs/tags/${new_tag}" >/dev/null 2>&1 && die "Tag ${new_tag} already exists."

echo "Current version: ${current_version}"
echo "Bumping to: ${new_version}"
./mvnw -B -ntp "${VERSIONS_PLUGIN}:set-property" \
  -Dproperty="${VERSION_PROPERTY}" -DnewVersion="${new_version}" -DgenerateBackupPoms=false
update_build_timestamp "$(date -u +%Y-%m-%dT00:00:00Z)"

resolved="$(resolve_version)"
[[ "$resolved" == "$new_version" ]] || die "Resolved reactor version '${resolved}', expected '${new_version}'."
for module in "${MODULES[@]}"; do
  resolved="$(resolve_version "$module")"
  [[ "$resolved" == "$new_version" ]] || die "Resolved ${module} version '${resolved}', expected '${new_version}'."
done

echo "==> Verifying FeatureFramework"
./mvnw -B -ntp -Pplatform-acceptance verify
git diff --check

git add "$POM_FILE"
git commit -m "Bump version to ${new_tag} for release"
git tag --annotate "$new_tag" --message "Release ${new_tag}"

echo "Version updated locally."
echo "Next step: git push origin HEAD && git push origin ${new_tag}"
