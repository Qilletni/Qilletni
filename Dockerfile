FROM eclipse-temurin:24-jdk

ARG SNAPSHOT=false
ARG PLATFORM_VERSION=snapshot

# For a release build these are populated from release/platform/X.Y.Z.yml
# (exact asset URL + verified sha256) by build-docker.yml. For a snapshot
# build the asset is the mutable `snapshot` release and is not hash-pinned.
ARG TOOLCHAIN_ASSET_URL
ARG TOOLCHAIN_SHA256=""
ARG QPM_ASSET_URL
ARG QPM_SHA256=""

# Provenance/labelling args, also populated from release/platform/X.Y.Z.yml
# by build-docker.yml for a release build. Left empty for a plain/snapshot
# `docker build` invocation.
ARG SOURCE_COMMIT=""
ARG CREATED=""
ARG PLATFORM_MANIFEST_SHA256=""
ARG TOOLCHAIN_VERSION=""
ARG TOOLCHAIN_COMMIT=""
ARG QPM_VERSION=""
ARG QPM_COMMIT=""
ARG CORE_VERSION=""

LABEL org.opencontainers.image.title="qilletni"
LABEL org.opencontainers.image.description="Qilletni language toolchain and package manager"
LABEL org.opencontainers.image.source="https://github.com/Qilletni/Qilletni"
LABEL org.opencontainers.image.version="${PLATFORM_VERSION}"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.revision="${SOURCE_COMMIT}"
LABEL org.opencontainers.image.created="${CREATED}"
LABEL dev.qilletni.snapshot="${SNAPSHOT}"
LABEL dev.qilletni.platform-manifest-sha256="${PLATFORM_MANIFEST_SHA256}"
LABEL dev.qilletni.toolchain-version="${TOOLCHAIN_VERSION}"
LABEL dev.qilletni.toolchain-commit="${TOOLCHAIN_COMMIT}"
LABEL dev.qilletni.qpm-version="${QPM_VERSION}"
LABEL dev.qilletni.qpm-commit="${QPM_COMMIT}"
LABEL dev.qilletni.core-version="${CORE_VERSION}"

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Same layout as a CLI install (install/install.sh): one directory per
# component, symlinked into bin/. The archives are flat and both contain a
# component-manifest.json, so unpacking them into a shared directory would
# silently drop one of them.
ENV QILLETNI_PLATFORM_DIR="/root/.qilletni/platforms/${PLATFORM_VERSION}"
RUN mkdir -p "$QILLETNI_PLATFORM_DIR/toolchain" "$QILLETNI_PLATFORM_DIR/qpm" /root/.qilletni/bin

# Downloads + verifies (when a sha256 is supplied) a pinned release asset.
COPY <<'EOF' /usr/local/bin/fetch-and-verify.sh
#!/usr/bin/env bash
set -euo pipefail
url="$1"
sha256="$2"
dest="$3"

curl -fL --retry 5 --retry-delay 5 "$url" -o "$dest"

if [[ -n "$sha256" ]]; then
  echo "${sha256}  ${dest}" | sha256sum -c -
fi
EOF
RUN chmod +x /usr/local/bin/fetch-and-verify.sh

RUN set -x && \
    /usr/local/bin/fetch-and-verify.sh "$TOOLCHAIN_ASSET_URL" "$TOOLCHAIN_SHA256" /tmp/toolchain.tar.gz && \
    tar -xzf /tmp/toolchain.tar.gz -C "$QILLETNI_PLATFORM_DIR/toolchain" && \
    rm /tmp/toolchain.tar.gz

RUN set -x && \
    /usr/local/bin/fetch-and-verify.sh "$QPM_ASSET_URL" "$QPM_SHA256" /tmp/qpm.tar.gz && \
    tar -xzf /tmp/qpm.tar.gz -C "$QILLETNI_PLATFORM_DIR/qpm" && \
    rm /tmp/qpm.tar.gz

# The launchers resolve their jar through `readlink -f "$0"`, so symlinks work.
RUN ln -s "$QILLETNI_PLATFORM_DIR/toolchain/qilletni" /root/.qilletni/bin/qilletni && \
    ln -s "$QILLETNI_PLATFORM_DIR/qpm/qpm" /root/.qilletni/bin/qpm && \
    chmod -R 755 "$QILLETNI_PLATFORM_DIR" /root/.qilletni/bin

ENV PATH="/root/.qilletni/bin:${PATH}"

WORKDIR /workspace
