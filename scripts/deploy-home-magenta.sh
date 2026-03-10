#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

DEPLOY_HOME="${MAGENTA_HOME:-${HOME}/.magenta}"
DEPLOY_CONFIG_ROOT="${DEPLOY_HOME}/configs"
DEPLOY_WORKSPACE_ROOT="${DEPLOY_HOME}/root"
DEPLOY_JAR_PATH="${DEPLOY_HOME}/Magenta2-1.0-SNAPSHOT.jar"

SOURCE_CONFIG_ROOT="${REPO_ROOT}/configs"
SOURCE_JAR_PATH="${REPO_ROOT}/target/Magenta2-1.0-SNAPSHOT.jar"

DEPLOY_MAGENTA_CONFIG="${DEPLOY_CONFIG_ROOT}/magenta.yaml"
SYNC_CONFIGS="${MAGENTA_DEPLOY_SYNC_CONFIGS:-false}"

if [[ "${1:-}" == "--sync-configs" ]]; then
  SYNC_CONFIGS="true"
fi

echo "Building Magenta2 jar..."
(
  cd "${REPO_ROOT}"
  mvn -q -DskipTests package
)

if [[ ! -f "${SOURCE_JAR_PATH}" ]]; then
  echo "ERROR: Built jar not found at ${SOURCE_JAR_PATH}" >&2
  exit 1
fi

echo "Preparing deploy directories in ${DEPLOY_HOME}..."
mkdir -p "${DEPLOY_HOME}" "${DEPLOY_CONFIG_ROOT}" "${DEPLOY_WORKSPACE_ROOT}"

echo "Copying jar..."
cp "${SOURCE_JAR_PATH}" "${DEPLOY_JAR_PATH}"

if [[ ! -f "${DEPLOY_MAGENTA_CONFIG}" ]]; then
  echo "No deployed config found; bootstrapping configs once..."
  SYNC_CONFIGS="true"
fi

if [[ "${SYNC_CONFIGS}" == "true" ]]; then
  echo "Syncing configs (replace mode)..."
  rm -rf "${DEPLOY_CONFIG_ROOT}"
  mkdir -p "${DEPLOY_CONFIG_ROOT}"
  cp -a "${SOURCE_CONFIG_ROOT}/." "${DEPLOY_CONFIG_ROOT}/"

  if [[ ! -f "${DEPLOY_MAGENTA_CONFIG}" ]]; then
    echo "ERROR: Missing deployed config ${DEPLOY_MAGENTA_CONFIG}" >&2
    exit 1
  fi

  echo "Setting deployed workspace root to ${DEPLOY_WORKSPACE_ROOT}..."
  tmp_magenta="$(mktemp)"
  if ! awk -v ws="${DEPLOY_WORKSPACE_ROOT}" '
    BEGIN { updated = 0 }
    /^  workspaceRoot:[[:space:]]*/ {
      print "  workspaceRoot: \"" ws "\""
      updated = 1
      next
    }
    { print }
    END {
      if (!updated) {
        exit 11
      }
    }
  ' "${DEPLOY_MAGENTA_CONFIG}" > "${tmp_magenta}"; then
    rm -f "${tmp_magenta}"
    echo "ERROR: Failed to update workspaceRoot in ${DEPLOY_MAGENTA_CONFIG}" >&2
    exit 1
  fi
  mv "${tmp_magenta}" "${DEPLOY_MAGENTA_CONFIG}"
else
  echo "Skipping config sync; keeping existing deployed configs unchanged."
fi

echo "Deploy complete."
echo "Jar:    ${DEPLOY_JAR_PATH}"
echo "Config: ${DEPLOY_MAGENTA_CONFIG}"
echo "Root:   ${DEPLOY_WORKSPACE_ROOT}"
