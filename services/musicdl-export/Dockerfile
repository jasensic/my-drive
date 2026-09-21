# HTTP wrapper around musicdl (search + download). Image name: musicdl-export.
# Installs the published musicdl package. Extra DRM/CLI tools (Node, N_m3u8DL-RE,
# Bento4, amdecrypt) are not included; use the default HTTP music clients.
FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    HOME=/home/musicdl \
    XDG_CACHE_HOME=/tmp/cache \
    XDG_CONFIG_HOME=/tmp/config \
    XDG_DATA_HOME=/tmp/data \
    XDG_STATE_HOME=/tmp/state \
    MUSICDL_PORT=8090

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 musicdl \
    && useradd --uid 1000 --gid 1000 --home-dir /home/musicdl --create-home --shell /usr/sbin/nologin musicdl \
    && mkdir -p /downloads /opt/musicdl-export \
    && chown musicdl:musicdl /downloads

RUN pip install --no-cache-dir "musicdl==2.13.11"

COPY server.py /opt/musicdl-export/server.py

USER musicdl
WORKDIR /downloads
VOLUME ["/downloads"]
EXPOSE 8090
ENTRYPOINT ["python", "/opt/musicdl-export/server.py"]
