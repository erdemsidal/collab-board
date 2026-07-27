#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != true ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# Determine the project base directory
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd -- "$(dirname -- "$0")" && pwd -P)}"

# Read maven-wrapper.properties
MAVEN_WRAPPER_PROPERTIES="${MAVEN_PROJECTBASEDIR}/.mvn/wrapper/maven-wrapper.properties"
if [ ! -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  echo "Error: Cannot find $MAVEN_WRAPPER_PROPERTIES" >&2
  exit 1
fi

# Parse properties
distributionUrl=$(awk -F '=' '/^distributionUrl=/ {print $2}' "$MAVEN_WRAPPER_PROPERTIES" | tr -d '\r')

if [ -z "$distributionUrl" ]; then
  echo "Error: Could not find distributionUrl in $MAVEN_WRAPPER_PROPERTIES" >&2
  exit 1
fi

# Determine Maven home
MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
MAVEN_HOME="${MAVEN_USER_HOME}/wrapper/dists"

# Extract distribution name from URL
distributionUrlName=$(basename "$distributionUrl" .zip)
MAVEN_HOME="${MAVEN_HOME}/${distributionUrlName}"

# Check if Maven is already downloaded
if [ -x "${MAVEN_HOME}/bin/mvn" ]; then
  exec "${MAVEN_HOME}/bin/mvn" "$@"
fi

# Download Maven
echo "Downloading from: $distributionUrl"
echo "Downloading to: $MAVEN_HOME"

mkdir -p "$MAVEN_HOME"
DOWNLOAD_FILE="${MAVEN_HOME}/${distributionUrlName}.zip"

# Use curl or wget for downloading
if command -v curl > /dev/null 2>&1; then
  if [ "${MVNW_USERNAME-}" ] && [ "${MVNW_PASSWORD-}" ]; then
    curl ${MVNW_VERBOSE:+-v} -fsSL -u "${MVNW_USERNAME}:${MVNW_PASSWORD}" -o "$DOWNLOAD_FILE" "$distributionUrl"
  else
    curl ${MVNW_VERBOSE:+-v} -fsSL -o "$DOWNLOAD_FILE" "$distributionUrl"
  fi
elif command -v wget > /dev/null 2>&1; then
  if [ "${MVNW_USERNAME-}" ] && [ "${MVNW_PASSWORD-}" ]; then
    wget ${MVNW_VERBOSE:+-v} --http-user="${MVNW_USERNAME}" --http-password="${MVNW_PASSWORD}" -O "$DOWNLOAD_FILE" "$distributionUrl"
  else
    wget ${MVNW_VERBOSE:+-v} -O "$DOWNLOAD_FILE" "$distributionUrl"
  fi
else
  echo "Error: Neither curl nor wget found. Please install one of them." >&2
  exit 1
fi

# Extract
echo "Extracting Maven..."
unzip -qo "$DOWNLOAD_FILE" -d "$MAVEN_HOME"

# Move contents from inner directory
for dir in "${MAVEN_HOME}"/apache-maven-*/; do
  if [ -d "$dir" ]; then
    cp -rf "$dir"* "$MAVEN_HOME/"
    rm -rf "$dir"
  fi
done

# Clean up
rm -f "$DOWNLOAD_FILE"

# Verify
if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  echo "Error: Maven installation failed" >&2
  exit 1
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
