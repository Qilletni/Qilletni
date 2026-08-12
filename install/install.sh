#!/bin/sh
#
# Qilletni installer.  curl -L https://install.qilletni.dev/ | sh
#
# Installs a platform version: the reviewed QilletniToolchain + QPM composition
# recorded in release/platform/X.Y.Z.yml, which pins each component's release
# asset and SHA-256 and is what the Docker image is built from.
#
# Installs itself to ~/.qilletni/bin/qilletni-up for update/uninstall.

set -eu

REPO=Qilletni/Qilletni
BRANCH=master
RAW_BASE="https://raw.githubusercontent.com/$REPO/$BRANCH"
INSTALLER_URL="$RAW_BASE/install/install.sh"

QILLETNI_DIR="$HOME/.qilletni"
BIN_DIR="$QILLETNI_DIR/bin"
PLATFORMS_DIR="$QILLETNI_DIR/platforms"
ENV_FILE="$QILLETNI_DIR/env"

BLOCK_START="# >>> qilletni >>>"
BLOCK_END="# <<< qilletni <<<"
# Single-quoted so $HOME expands when the profile is sourced, not now.
# shellcheck disable=SC2016
ENV_LINE='. "$HOME/.qilletni/env"'
JAVA_MIN=22
USER_DATA="config.json packages packages-local persistence doc-cache logs cache"

opt_snapshot=0
opt_yes=0
opt_no_modify_path=0
opt_force=0
opt_quiet=0
opt_purge=0
staging=""

say() { [ "$opt_quiet" -eq 1 ] || printf '%s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }
matches() { printf '%s' "$1" | grep -Eq "$2"; }

cleanup() { [ -n "$staging" ] && rm -rf "$staging"; return 0; }
trap cleanup EXIT INT TERM

usage() {
    cat <<EOF
Usage: qilletni-up <command> [options]

  install [<version>|latest]   install a platform version (default)
  update                       update to the latest version
  uninstall [--purge]          remove Qilletni
  status                       show what is installed

  --snapshot          install the snapshot build (not hash-pinned)
  --force             reinstall even if already current
  --no-modify-path    do not touch shell profiles
  -y, --yes           assume yes for prompts
  -q, --quiet         only print warnings and errors
  -h, --help          show this help
EOF
}

# ----------------------------------------------------------------- fetch ----

http_get() {
    if have curl; then curl -fsSL --retry 3 --retry-delay 2 "$1"
    elif have wget; then wget -qO- "$1"
    else die "curl or wget is required."
    fi
}

http_download() {
    if have curl; then curl -fsL --retry 3 --retry-delay 2 -o "$2" "$1"
    elif have wget; then wget -qO "$2" "$1"
    else die "curl or wget is required."
    fi
}

sha256_of() {
    if have sha256sum; then sha256sum "$1" | cut -d' ' -f1
    elif have shasum; then shasum -a 256 "$1" | cut -d' ' -f1
    else die "sha256sum or shasum is required."
    fi
}

# ------------------------------------------------------------------ java ----

# Must match the launcher scripts: JAVA_HOME first, then PATH.
resolve_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        printf '%s' "$JAVA_HOME/bin/java"
    elif have java; then
        command -v java
    else
        return 1
    fi
}

java_major() {
    java_version=$("$1" -version 2>&1 | head -n1 | sed -n 's/.*version "\([^"]*\)".*/\1/p')
    [ -n "$java_version" ] || return 1
    case "$java_version" in
        1.*) printf '%s' "$java_version" | cut -d. -f2 ;;
        *) printf '%s' "$java_version" | cut -d. -f1 | cut -d- -f1 ;;
    esac
}

check_java() {
    java_exe=$(resolve_java) ||
        die "Java $JAVA_MIN+ is required but no java was found. Install a JDK or set JAVA_HOME."
    java_major_version=$(java_major "$java_exe") || die "Could not determine the version of $java_exe"
    if ! matches "$java_major_version" '^[0-9]+$' || [ "$java_major_version" -lt "$JAVA_MIN" ]; then
        die "Java $JAVA_MIN+ is required, but $java_exe is Java $java_major_version."
    fi
}

# -------------------------------------------------------------- manifest ----

# The manifest schema is fixed and validated by platform_manifest.ts before it
# can be committed (no anchors, flow style or quoting), so this is enough.
parse_manifest() {
    awk '
        /^components:[[:space:]]*$/ { inc = 1; next }
        /^[^[:space:]]/ { inc = 0 }
        inc == 0 { next }
        /^  [A-Za-z][A-Za-z0-9_]*:[[:space:]]*$/ { c = $1; sub(/:$/, "", c); next }
        /^    (repository|version|tag|asset|sha256):[[:space:]]+/ {
            if (c == "") next
            k = $1; sub(/:$/, "", k)
            print c "_" k "=" $2
        }
    '
}

RE_REPOSITORY='^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$'
RE_VERSION='^[A-Za-z0-9._+-]+$'
RE_ASSET='^[A-Za-z0-9._-]+$'
RE_SHA256='^[0-9a-f]{64}$'

# Reads one <component>.<field> out of $parsed, dying if it is absent or
# malformed. Runs in a command substitution, so die() ends the subshell and
# the failed assignment ends the script under set -e.
field() {
    value=$(printf '%s\n' "$parsed" | sed -n "s/^$1_$2=//p" | head -n1)
    [ -n "$value" ] || die "Manifest is missing $1.$2"
    matches "$value" "$3" || die "Manifest value $1.$2 is malformed: $value"
    printf '%s' "$value"
}

load_manifest() {
    parsed=$(printf '%s\n' "$1" | parse_manifest)

    toolchain_repository=$(field toolchain repository "$RE_REPOSITORY")
    toolchain_version=$(field toolchain version "$RE_VERSION")
    toolchain_tag=$(field toolchain tag "$RE_VERSION")
    toolchain_asset=$(field toolchain asset "$RE_ASSET")
    toolchain_sha256=$(field toolchain sha256 "$RE_SHA256")

    qpm_repository=$(field qpm repository "$RE_REPOSITORY")
    qpm_version=$(field qpm version "$RE_VERSION")
    qpm_tag=$(field qpm tag "$RE_VERSION")
    qpm_asset=$(field qpm asset "$RE_ASSET")
    qpm_sha256=$(field qpm sha256 "$RE_SHA256")
}

# ------------------------------------------------------------- resolvers ----
#
# Both set the same globals: resolved, and per component <c>_repository,
# <c>_version, <c>_tag, <c>_asset, <c>_sha256 and <c>_url. <c>_sha256 is empty
# for snapshots, which are not hash-pinned.

resolve_release() {
    want="$1"

    if [ "$want" = latest ]; then
        want=$(http_get "$RAW_BASE/release/platform/stable" | head -n1 | tr -d '[:space:]') ||
            die "Could not fetch the platform version pointer."
    fi
    matches "$want" '^[0-9]+\.[0-9]+\.[0-9]+$' ||
        die "'$want' is not a platform version (expected X.Y.Z or latest)."

    manifest=$(http_get "$RAW_BASE/release/platform/$want.yml") ||
        die "No platform manifest for $want. See https://github.com/$REPO/tree/$BRANCH/release/platform"
    load_manifest "$manifest"

    resolved=$want
    toolchain_url="https://github.com/$toolchain_repository/releases/download/$toolchain_tag/$toolchain_asset"
    qpm_url="https://github.com/$qpm_repository/releases/download/$qpm_tag/$qpm_asset"
}

# Same "exactly one asset" guard as build-docker.yml.
snapshot_asset() {
    urls=$(http_get "https://api.github.com/repos/$1/releases/tags/snapshot" |
        tr ',' '\n' |
        sed -n 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' |
        grep -E "/$2[^/]*\.tar\.gz$" || true)
    n=$(printf '%s' "$urls" | grep -c . || true)
    [ "$n" -eq 1 ] || die "Expected one $2*.tar.gz asset on the $1 snapshot release, found $n."
    printf '%s' "$urls"
}

resolve_snapshot() {
    toolchain_repository=Qilletni/QilletniToolchain
    qpm_repository=Qilletni/QPMCLI
    toolchain_tag=snapshot
    qpm_tag=snapshot

    toolchain_url=$(snapshot_asset "$toolchain_repository" qilletni-)
    qpm_url=$(snapshot_asset "$qpm_repository" qpm-)

    toolchain_asset=${toolchain_url##*/}
    qpm_asset=${qpm_url##*/}
    toolchain_version=${toolchain_asset#qilletni-}; toolchain_version=${toolchain_version%.tar.gz}
    qpm_version=${qpm_asset#qpm-}; qpm_version=${qpm_version%.tar.gz}
    toolchain_sha256=''
    qpm_sha256=''
    resolved=snapshot

    warn "snapshot assets are not hash-pinned"
}

# --------------------------------------------------------------- install ----

# Each component gets its own directory: the archives are flat and both contain
# a file named component-manifest.json.
fetch_component() {
    archive="$staging/$1.tar.gz"
    http_download "$2" "$archive" || die "Failed to download $2"

    if [ -n "$3" ]; then
        actual=$(sha256_of "$archive")
        [ "$actual" = "$3" ] || die "Checksum mismatch for ${2##*/}: expected $3, got $actual"
    fi

    mkdir -p "$staging/tree/$1"
    tar -xzf "$archive" -C "$staging/tree/$1" || die "Failed to unpack ${2##*/}"
    rm -f "$archive"
}

write_install_json() {
    cat > "$1" <<EOF
{
  "schema_version": 1,
  "platform_version": "$resolved",
  "installed_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "components": {
    "toolchain": { "version": "$toolchain_version", "asset": "$toolchain_asset", "sha256": "$toolchain_sha256", "url": "$toolchain_url" },
    "qpm": { "version": "$qpm_version", "asset": "$qpm_asset", "sha256": "$qpm_sha256", "url": "$qpm_url" }
  }
}
EOF
}

# rename(2), so bin/ is never briefly empty.
link_atomic() {
    tmp="$2.tmp.$$"
    rm -f "$tmp"
    ln -s "$1" "$tmp"
    mv -f "$tmp" "$2"
}

current_version() {
    [ -L "$BIN_DIR/qilletni" ] || return 1
    v=$(readlink "$BIN_DIR/qilletni" | sed -n 's|.*platforms/\([^/]*\)/.*|\1|p')
    [ -n "$v" ] || return 1
    printf '%s' "$v"
}

install_platform() {
    check_java

    if [ "$opt_snapshot" -eq 1 ]; then resolve_snapshot; else resolve_release "$1"; fi

    cur=$(current_version || true)
    if [ "$cur" = "$resolved" ] && [ "$opt_force" -eq 0 ] && [ "$resolved" != snapshot ]; then
        say "Qilletni $resolved is already installed (--force to reinstall)."
        return 0
    fi

    say "Installing Qilletni $resolved (toolchain $toolchain_version, qpm $qpm_version)"

    mkdir -p "$PLATFORMS_DIR" "$BIN_DIR"
    staging="$PLATFORMS_DIR/.staging-$$"
    rm -rf "$staging"
    mkdir -p "$staging/tree"

    fetch_component toolchain "$toolchain_url" "$toolchain_sha256"
    fetch_component qpm "$qpm_url" "$qpm_sha256"
    chmod +x "$staging/tree/toolchain/qilletni" "$staging/tree/qpm/qpm"
    write_install_json "$staging/tree/install.json"

    # Nothing existing is touched until both components are verified above.
    target="$PLATFORMS_DIR/$resolved"
    rm -rf "$target"
    mv "$staging/tree" "$target"
    rm -rf "$staging"
    staging=""

    link_atomic "../platforms/$resolved/toolchain/qilletni" "$BIN_DIR/qilletni"
    link_atomic "../platforms/$resolved/qpm/qpm" "$BIN_DIR/qpm"

    # One version is retained; anything else is superseded.
    for d in "$PLATFORMS_DIR"/*; do
        [ -d "$d" ] && [ "$d" != "$target" ] && rm -rf "$d"
    done

    install_self
    write_env_file
    [ "$opt_no_modify_path" -eq 1 ] || update_profiles

    say "Installed. Open a new shell, or run: . \"$ENV_FILE\""
}

install_self() {
    src="$0"

    # Piped from curl, $0 is the shell rather than a script, so fetch a copy.
    # The grep guards against $0 naming an unrelated file that happens to exist
    # in the working directory.
    if ! grep -q "^REPO=$REPO\$" "$src" 2>/dev/null; then
        src="$BIN_DIR/.qilletni-up.src.$$"
        http_download "$INSTALLER_URL" "$src" || {
            rm -f "$src"
            warn "could not install the qilletni-up manager"
            return 0
        }
    fi

    # Never write in place: sh reads scripts incrementally and this may be it.
    tmp="$BIN_DIR/.qilletni-up.$$"
    cat "$src" > "$tmp"
    chmod +x "$tmp"
    mv -f "$tmp" "$BIN_DIR/qilletni-up"
    [ "$src" = "$0" ] || rm -f "$src"
}

# Last step of an update, so a fix to this script lands on the next run rather
# than needing a re-exec mid-update.
refresh_self() {
    tmp="$BIN_DIR/.qilletni-up.$$"
    http_download "$INSTALLER_URL" "$tmp" 2>/dev/null || { rm -f "$tmp"; return 0; }

    if [ -f "$BIN_DIR/qilletni-up" ] &&
       [ "$(sha256_of "$tmp")" = "$(sha256_of "$BIN_DIR/qilletni-up")" ]; then
        rm -f "$tmp"
        return 0
    fi
    chmod +x "$tmp"
    mv -f "$tmp" "$BIN_DIR/qilletni-up"
}

# ------------------------------------------------------------------ PATH ----

write_env_file() {
    cat > "$ENV_FILE" <<'EOF'
# Generated by the Qilletni installer. Safe to re-source.
case ":${PATH}:" in
    *":$HOME/.qilletni/bin:"*) ;;
    *) PATH="$HOME/.qilletni/bin:$PATH" ;;
esac
export PATH
EOF
}

add_block() {
    grep -qF "$BLOCK_START" "$1" 2>/dev/null && return 0
    # Complete a missing final newline, so the block never joins the last line.
    [ -s "$1" ] && [ -n "$(tail -c1 "$1")" ] && printf '\n' >> "$1"
    {
        printf '%s\n' "$BLOCK_START"
        printf '%s\n' "$2"
        printf '%s\n' "$BLOCK_END"
    } >> "$1"
}

remove_block() {
    [ -f "$1" ] || return 0
    grep -qF "$BLOCK_START" "$1" || return 0
    tmp="$1.qilletni.$$"
    sed "/^${BLOCK_START}\$/,/^${BLOCK_END}\$/d" "$1" > "$tmp"
    cat "$tmp" > "$1"   # keeps the profile's inode and permissions
    rm -f "$tmp"
}

update_profiles() {
    found=0
    for p in "$HOME/.profile" "$HOME/.bashrc" "$HOME/.bash_profile" "$HOME/.zshrc" "$HOME/.zprofile"; do
        [ -f "$p" ] || continue
        found=1
        add_block "$p" "$ENV_LINE"
    done

    if [ "$found" -eq 0 ]; then
        touch "$HOME/.profile"
        add_block "$HOME/.profile" "$ENV_LINE"
    fi
}

clean_profiles() {
    for p in "$HOME/.profile" "$HOME/.bashrc" "$HOME/.bash_profile" "$HOME/.zshrc" \
             "$HOME/.zprofile"; do
        remove_block "$p"
    done
}

# -------------------------------------------------------------- commands ----

cmd_update() {
    cur=$(current_version || true)
    [ -n "$cur" ] || die "Qilletni is not installed. Run 'qilletni-up install'."

    if [ "$cur" = snapshot ]; then
        opt_snapshot=1
        opt_force=1
    fi

    install_platform latest
    refresh_self
}

# stdin may be the script itself (curl | sh), so read the terminal directly.
# No terminal means no.
confirm() {
    [ "$opt_yes" -eq 1 ] && return 0
    { : >/dev/tty; } 2>/dev/null || return 1
    printf '%s [y/N] ' "$1" >/dev/tty
    read -r ans </dev/tty || return 1
    case "$ans" in [Yy]|[Yy][Ee][Ss]) return 0 ;; *) return 1 ;; esac
}

cmd_uninstall() {
    [ -d "$QILLETNI_DIR" ] || die "Qilletni is not installed at $QILLETNI_DIR"

    rm -rf "$BIN_DIR" "$PLATFORMS_DIR" "$ENV_FILE"
    clean_profiles

    kept=''
    for e in $USER_DATA; do
        [ -e "$QILLETNI_DIR/$e" ] && kept="$kept $e"
    done

    if [ -z "$kept" ]; then
        rmdir "$QILLETNI_DIR" 2>/dev/null || true
        say "Removed."
        return 0
    fi

    purge=$opt_purge
    if [ "$purge" -eq 0 ]; then
        say "User data in $QILLETNI_DIR:$kept"
        say "This includes your qpm credentials and installed packages."
        confirm "Delete it too?" && purge=1
    fi

    if [ "$purge" -eq 1 ]; then
        rm -rf "$QILLETNI_DIR"
        say "Removed, including user data."
    else
        say "Removed. Kept your data in $QILLETNI_DIR"
    fi
}

# install.json is only ever written by this script, so its layout is known.
json_field() {
    sed -n "/\"$2\": {/s/.*\"$3\": \"\([^\"]*\)\".*/\1/p" "$1" | head -n1
}

cmd_status() {
    cur=$(current_version || true)
    [ -n "$cur" ] || die "Qilletni is not installed."
    info="$PLATFORMS_DIR/$cur/install.json"

    printf 'platform   %s\n' "$cur"
    if [ -f "$info" ]; then
        printf 'toolchain  %s\n' "$(json_field "$info" toolchain version)"
        printf 'qpm        %s\n' "$(json_field "$info" qpm version)"
    fi
    printf 'location   %s\n' "$PLATFORMS_DIR/$cur"

    if java_exe=$(resolve_java); then
        printf 'java       %s (%s)\n' "$(java_major "$java_exe")" "$java_exe"
    else
        printf 'java       not found\n'
    fi

    case ":${PATH}:" in
        *":$BIN_DIR:"*) printf 'path       ok\n' ;;
        *) printf 'path       %s not on PATH\n' "$BIN_DIR" ;;
    esac
}

# ------------------------------------------------------------------ main ----

for c in tar sed awk grep; do
    have "$c" || die "'$c' is required but was not found."
done

command=''
version_arg=''

while [ $# -gt 0 ]; do
    case "$1" in
        install|update|upgrade|uninstall|status)
            [ -n "$command" ] && die "Only one command may be given."
            command="$1" ;;
        --snapshot) opt_snapshot=1 ;;
        --force) opt_force=1 ;;
        --purge) opt_purge=1 ;;
        --no-modify-path) opt_no_modify_path=1 ;;
        -y|--yes) opt_yes=1 ;;
        -q|--quiet) opt_quiet=1 ;;
        -h|--help) usage; exit 0 ;;
        -*) die "Unknown option '$1'. See --help." ;;
        *)
            [ -n "$version_arg" ] && die "Unexpected argument '$1'."
            version_arg="$1" ;;
    esac
    shift
done

case "${command:-install}" in
    install) install_platform "${version_arg:-latest}" ;;
    update|upgrade) cmd_update ;;
    uninstall) cmd_uninstall ;;
    status) cmd_status ;;
esac
