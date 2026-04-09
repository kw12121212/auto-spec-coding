#!/usr/bin/env bash
# Downloads precompiled binaries for builtin tools (rg, fd) from GitHub Releases.
# Places them in src/main/resources/builtin-tools/{platform}/
set -euo pipefail

RESOURCES_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/builtin-tools"

RG_VERSION="15.1.0"
FD_VERSION="10.4.2"

download_and_extract() {
    local url="$1"
    local platform_dir="$2"
    local binary_name="$3"

    local target="$platform_dir/$binary_name"

    # Skip if binary already exists and is executable
    if [ -x "$target" ]; then
        echo "  SKIP $target (already exists)"
        return 0
    fi

    echo "  Downloading $url"
    local tmpdir
    tmpdir=$(mktemp -d)
    trap "rm -rf '$tmpdir'" RETURN

    local archive="$tmpdir/archive"
    curl -fsSL "$url" -o "$archive"

    # Extract the binary
    case "$url" in
        *.tar.gz) tar xzf "$archive" -C "$tmpdir" ;;
        *.zip)   unzip -qo "$archive" -d "$tmpdir" ;;
    esac

    # Find and copy the binary
    local found
    found=$(find "$tmpdir" -name "$binary_name" -type f | head -1)
    if [ -z "$found" ]; then
        echo "  ERROR: $binary_name not found in archive"
        exit 1
    fi

    mkdir -p "$platform_dir"
    cp "$found" "$target"
    chmod +x "$target"
    echo "  -> $target"
}

echo "=== Downloading builtin tools ==="

# --- Linux x86_64 ---
echo ""
echo "[linux-x86_64]"
download_and_extract \
    "https://github.com/BurntSushi/ripgrep/releases/download/${RG_VERSION}/ripgrep-${RG_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
    "$RESOURCES_DIR/linux-x86_64" "rg"

download_and_extract \
    "https://github.com/sharkdp/fd/releases/download/v${FD_VERSION}/fd-v${FD_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
    "$RESOURCES_DIR/linux-x86_64" "fd"

# --- macOS ARM64 ---
echo ""
echo "[macos-arm64]"
download_and_extract \
    "https://github.com/BurntSushi/ripgrep/releases/download/${RG_VERSION}/ripgrep-${RG_VERSION}-aarch64-apple-darwin.tar.gz" \
    "$RESOURCES_DIR/macos-arm64" "rg"

download_and_extract \
    "https://github.com/sharkdp/fd/releases/download/v${FD_VERSION}/fd-v${FD_VERSION}-aarch64-apple-darwin.tar.gz" \
    "$RESOURCES_DIR/macos-arm64" "fd"

# --- macOS x86_64 (fd dropped Intel Mac support after 10.3.0, rg only) ---
echo ""
echo "[macos-x86_64] (rg only — fd not available for this platform)"
download_and_extract \
    "https://github.com/BurntSushi/ripgrep/releases/download/${RG_VERSION}/ripgrep-${RG_VERSION}-x86_64-apple-darwin.tar.gz" \
    "$RESOURCES_DIR/macos-x86_64" "rg"

echo ""
echo "=== Done ==="
echo "Binaries placed in $RESOURCES_DIR"
find "$RESOURCES_DIR" -type f -exec ls -lh {} \;
