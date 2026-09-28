# musicdl HTTP sidecar. Build context is services/:
#   docker compose -f deploy/docker-compose.yml build musicdl
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
    && mkdir -p /downloads /opt/musicdl /opt/musicdl-export \
    && chown musicdl:musicdl /downloads

WORKDIR /opt/musicdl
COPY musicdl/requirements.txt musicdl/setup.py musicdl/MANIFEST.in musicdl/README.md musicdl/LICENSE ./
COPY musicdl/musicdl ./musicdl
RUN pip install --no-cache-dir .

COPY musicdl-server/server.py /opt/musicdl-export/server.py

USER musicdl
WORKDIR /downloads
VOLUME ["/downloads"]
EXPOSE 8090
ENTRYPOINT ["python", "/opt/musicdl-export/server.py"]
