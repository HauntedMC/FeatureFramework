#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

version="$(
  ./mvnw -q -ntp -DforceStdout help:evaluate -Dexpression=project.version \
    | awk '/^[0-9]+\.[0-9]+\.[0-9]+$/ { print; exit }'
)"
if [[ -z "$version" ]]; then
  echo "Unable to resolve the FeatureFramework project version." >&2
  exit 1
fi

local_repository="$(mktemp -d)"
consumer_directory="$(mktemp -d)"
cleanup() {
  rm -rf "$local_repository" "$consumer_directory"
}
trap cleanup EXIT

./mvnw -B -ntp \
  -pl featureframework-bom -am \
  -DskipTests \
  -Dmaven.repo.local="$local_repository" \
  install

parent_pom="$local_repository/nl/hauntedmc/featureframework/featureframework-parent/$version/featureframework-parent-$version.pom"
bom_pom="$local_repository/nl/hauntedmc/featureframework/featureframework-bom/$version/featureframework-bom-$version.pom"

for installed_pom in "$parent_pom" "$bom_pom"; do
  if [[ ! -f "$installed_pom" ]]; then
    echo "Expected installed consumer POM was not created: $installed_pom" >&2
    exit 1
  fi
  if grep -Fq '${revision}' "$installed_pom"; then
    echo "Installed consumer POM still contains an unresolved \${revision}: $installed_pom" >&2
    exit 1
  fi
done

cat >"$consumer_directory/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>nl.hauntedmc.featureframework.acceptance</groupId>
    <artifactId>bom-consumer</artifactId>
    <version>1.0.0</version>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>nl.hauntedmc.featureframework</groupId>
                <artifactId>featureframework-bom</artifactId>
                <version>$version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>nl.hauntedmc.featureframework</groupId>
            <artifactId>featureframework-theme-api</artifactId>
        </dependency>
    </dependencies>
</project>
EOF

effective_pom="$consumer_directory/effective-pom.xml"
./mvnw -B -ntp \
  -f "$consumer_directory/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  help:effective-pom \
  -Doutput="$effective_pom"

if ! awk -v expected="$version" '
  /<artifactId>featureframework-theme-api<\/artifactId>/ { candidate = 1; next }
  candidate && /<version>/ {
    value = $0
    sub(/^.*<version>/, "", value)
    sub(/<\/version>.*$/, "", value)
    if (value == expected) {
      found = 1
    }
    candidate = 0
  }
  END { exit found ? 0 : 1 }
' "$effective_pom"; then
  echo "External BOM consumer did not receive featureframework-theme-api:$version from dependency management." >&2
  exit 1
fi

echo "Verified external consumption of featureframework-bom:$version."
