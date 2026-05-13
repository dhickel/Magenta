#!/bin/bash
# docker-setup.sh — Install and verify container runtime for Magenta2 agent orchestration
#
# This script ensures a Docker API-compatible container runtime is available for
# Magenta2's docker-java agent execution. It prefers rootless Podman (ships with
# Fedora/RHEL) but also supports Docker Engine.
#
# What this script does:
#   1. Detects existing Podman or Docker installation
#   2. Installs Podman if missing (Fedora/RHEL via dnf, Debian/Ubuntu via apt)
#   3. Enables and starts the user-level socket for Docker API access
#   4. Pulls the configured agent image
#   5. Verifies end-to-end: container execution with bind mount writes (SELinux-safe)
#   6. Outputs the DOCKER_HOST env var needed at runtime
#
# Usage:
#   chmod +x .internal-dev/scripts/docker-setup.sh
#   ./.internal-dev/scripts/docker-setup.sh              # install if needed + verify
#   ./.internal-dev/scripts/docker-setup.sh --verify-only # verify only, no install
#   ./.internal-dev/scripts/docker-setup.sh --image python:3.12-slim  # custom agent image

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

AGENT_IMAGE="${MAGENTA_AGENT_IMAGE:-python:3.11-slim}"
VERIFY_ONLY=false
FAILURES=0

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --verify-only       Only verify existing setup, do not install
  --image <image>     Agent image to pull (default: python:3.11-slim)
  --help              Show this message

Environment:
  MAGENTA_AGENT_IMAGE     Agent image (overridden by --image)
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --verify-only) VERIFY_ONLY=true ;;
        --image) AGENT_IMAGE="$2"; shift ;;
        --help|-h) usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
    shift
done

check() {
    local desc="$1"; shift
    printf "  %-55s " "$desc"
    if "$@" &>/dev/null; then
        echo -e "${GREEN}PASS${NC}"
    else
        echo -e "${RED}FAIL${NC}"
        FAILURES=$((FAILURES + 1))
    fi
}

warn() {
    echo -e "${YELLOW}WARN:${NC} $*"
}

info() {
    echo -e "${CYAN}INFO:${NC} $*"
}

# ── Root check ──
if [ "$EUID" -eq 0 ]; then
    echo -e "${RED}Do not run this script as root.${NC} Rootless Podman runs under the user account."
    exit 1
fi

# ── Detect distribution ──
detect_distro() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        echo "$ID"
    elif [ -f /etc/fedora-release ]; then
        echo "fedora"
    else
        echo "unknown"
    fi
}

DISTRO=$(detect_distro)
RUNTIME=""

echo ""
echo "=== Magenta2 Container Runtime Setup ==="
echo ""

# ── Phase 1: Detect or install ──
echo -e "${CYAN}--- Phase 1: Runtime Detection / Installation ---${NC}"

if command -v podman &>/dev/null; then
    RUNTIME="podman"
    PODMAN_VERSION=$(podman version --format '{{.Version}}' 2>/dev/null || podman --version | awk '{print $NF}')
    echo -e "  Podman detected: ${GREEN}${PODMAN_VERSION}${NC}"
elif command -v docker &>/dev/null && docker info &>/dev/null; then
    RUNTIME="docker"
    DOCKER_VERSION=$(docker --version | awk '{print $3}' | tr -d ',')
    echo -e "  Docker detected: ${GREEN}${DOCKER_VERSION}${NC}"
elif [ "$VERIFY_ONLY" = true ]; then
    echo -e "  ${RED}No container runtime found and --verify-only is set.${NC}"
    exit 1
else
    echo "  No container runtime found. Installing Podman..."
    case "$DISTRO" in
        fedora|rhel|centos)
            info "Installing Podman via dnf..."
            sudo dnf install -y podman
            ;;
        ubuntu|debian)
            info "Installing Podman via apt..."
            sudo apt-get update -qq
            sudo apt-get install -y podman
            ;;
        *)
            echo -e "${RED}Cannot auto-install: unknown distribution '$DISTRO'.${NC}"
            echo "Install Podman manually: https://podman.io/getting-started/installation"
            exit 1
            ;;
    esac
    RUNTIME="podman"
    echo -e "  Podman installed: ${GREEN}$(podman --version)${NC}"
fi

# ── Phase 2: Enable and start user socket ──
echo ""
echo -e "${CYAN}--- Phase 2: Socket Setup ---${NC}"

if [ "$RUNTIME" = "podman" ]; then
    systemctl --user enable podman.socket 2>/dev/null || warn "Could not enable podman.socket"
    systemctl --user start podman.socket 2>/dev/null || warn "Could not start podman.socket"

    # Ensure lingering is enabled so socket survives logout
    if ! loginctl show-user "$USER" | grep -q 'Linger=yes'; then
        info "Enabling lingering for $USER (socket stays active after logout)"
        sudo loginctl enable-linger "$USER" 2>/dev/null || warn "Could not enable linger; socket may stop on logout"
    fi

    # Determine socket path
    XDG_RUNTIME="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
    DOCKER_SOCKET="${XDG_RUNTIME}/podman/podman.sock"
    DOCKER_HOST="unix://${DOCKER_SOCKET}"
elif [ "$RUNTIME" = "docker" ]; then
    # Docker socket is system-level
    DOCKER_SOCKET="/var/run/docker.sock"
    DOCKER_HOST="unix://${DOCKER_SOCKET}"
    if ! [ -S "$DOCKER_SOCKET" ]; then
        echo -e "${RED}Docker socket not found at $DOCKER_SOCKET. Is Docker running?${NC}"
        FAILURES=$((FAILURES + 1))
    fi
    # Check user is in docker group
    if ! groups | grep -q docker; then
        echo -e "${YELLOW}WARN: $USER not in docker group.${NC}"
        echo "  Run: sudo usermod -aG docker $USER"
        echo "  Then log out and back in."
    fi
fi

# ── Phase 3: Pull agent image ──
echo ""
echo -e "${CYAN}--- Phase 3: Agent Image ---${NC}"
echo "  Image: $AGENT_IMAGE"
if "$RUNTIME" pull "$AGENT_IMAGE" &>/dev/null; then
    echo -e "  ${GREEN}Image pulled successfully${NC}"
else
    echo -e "  ${RED}Failed to pull $AGENT_IMAGE${NC}"
    FAILURES=$((FAILURES + 1))
fi

# ── Phase 4: Verification ──
echo ""
echo -e "${CYAN}--- Phase 4: Verification ---${NC}"

echo "Basic checks:"
check "runtime binary" command -v "$RUNTIME"

if [ "$RUNTIME" = "podman" ]; then
    check "podman.socket active" bash -c "systemctl --user is-active podman.socket | grep -q active"
    check "podman.socket enabled" bash -c "systemctl --user is-enabled podman.socket | grep -q enabled"
fi
check "socket file exists" test -S "$DOCKER_SOCKET"

echo ""
echo "Docker API compatibility:"
API_RESPONSE=$(curl -s --unix-socket "$DOCKER_SOCKET" http://localhost/version 2>/dev/null || echo "")
if [ -n "$API_RESPONSE" ]; then
    API_VERSION=$(echo "$API_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('ApiVersion','unknown'))" 2>/dev/null || echo "unknown")
    echo -e "  API version ${GREEN}$API_VERSION${NC} at $DOCKER_HOST"
else
    echo -e "  ${RED}Docker API unreachable at $DOCKER_HOST${NC}"
    FAILURES=$((FAILURES + 1))
fi

echo ""
echo "Image check:"
check "agent image exists: $AGENT_IMAGE" "$RUNTIME" image exists "$AGENT_IMAGE"

echo ""
echo "Container execution test (with SELinux-safe bind mount):"
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

# Test with :rw,Z — the Z flag is required for SELinux enforcing hosts, no-op otherwise
CONTAINER_OUT=$("$RUNTIME" run --rm \
    -v "${TMPDIR}:/output:rw,Z" \
    "$AGENT_IMAGE" \
    bash -c "echo 'magenta2-container-test-passed' > /output/test.txt && cat /output/test.txt" 2>/dev/null || echo "CONTAINER_FAILED")

if [ "$CONTAINER_OUT" = "magenta2-container-test-passed" ] \
    && [ -f "${TMPDIR}/test.txt" ] \
    && grep -q "magenta2-container-test-passed" "${TMPDIR}/test.txt"; then
    echo -e "  container executed and wrote to bind mount ... ${GREEN}PASS${NC}"
    echo -e "  (SELinux :Z relabeling is active)"
else
    echo -e "  container execution or bind mount write ... ${RED}FAIL${NC}"
    echo -e "  Output: $CONTAINER_OUT"
    FAILURES=$((FAILURES + 1))
fi

# ── Phase 5: Runtime environment instructions ──
echo ""
echo -e "${CYAN}--- Runtime Configuration ---${NC}"
echo ""
echo "  Set this environment variable before starting Magenta2:"
echo ""
echo -e "    ${GREEN}export DOCKER_HOST=${DOCKER_HOST}${NC}"
echo ""
echo "  For systemd service, add to the service file:"
echo "    Environment=\"DOCKER_HOST=${DOCKER_HOST}\""
echo ""
echo "  For IDE launch, add to run configuration environment variables."
echo ""
echo "  The application config property is:"
echo "    magenta.docker.agent-image=${AGENT_IMAGE}"
echo "    magenta.docker.selinux-relabel=true  (default, enables :Z on bind mounts)"
echo ""

# ── Summary ──
echo "=== Result ==="
if [ "$FAILURES" -eq 0 ]; then
    echo -e "${GREEN}All checks passed. Container runtime is ready for Magenta2 agent execution.${NC}"
    exit 0
else
    echo -e "${RED}$FAILURES check(s) failed. Fix the issues above before running Magenta2.${NC}"
    exit 1
fi
