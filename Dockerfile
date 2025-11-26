FROM eclipse-temurin:24-jre

ARG SNAPSHOT=false
ARG TOOLCHAIN_VERSION=v1.0.0
ARG QPM_VERSION=v1.0.0

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /root/.qilletni/bin

RUN set -x && \
    EFFECTIVE_VERSION=$(if [ "$SNAPSHOT" = "true" ]; then echo "snapshot"; else echo "$TOOLCHAIN_VERSION"; fi) && \
    curl -L "$(curl -s https://api.github.com/repos/Qilletni/QilletniToolchain/releases/tags/${EFFECTIVE_VERSION} | grep 'browser_download_url.*tar.gz' | cut -d'"' -f4)" \
    | tar -xzf - -C /root/.qilletni

RUN set -x && \
    EFFECTIVE_VERSION=$(if [ "$SNAPSHOT" = "true" ]; then echo "snapshot"; else echo "$QPM_VERSION"; fi) && \
    curl -L "$(curl -s https://api.github.com/repos/Qilletni/QPMCLI/releases/tags/${EFFECTIVE_VERSION} | grep 'browser_download_url.*tar.gz' | cut -d'"' -f4)" \
    | tar -xzf - -C /root/.qilletni

ENV PATH="/root/.qilletni/bin:${PATH}"

RUN chmod -R 755 /root/.qilletni/bin

WORKDIR /workspace
