"""Small HTTP API in front of musicdl.

Search keeps SongInfo objects in memory. Download uses that cache so the
portal never sees download URLs or cookies.

POST /search    {"keyword": "..."}
POST /download  {"search_id": "...", "track_id": "..."}  -> audio bytes
GET  /health
"""

from __future__ import annotations

import json
import os
import threading
import time
import traceback
import uuid
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote

from musicdl import musicdl

DEFAULT_SOURCES = [
    "MiguMusicClient",
    "NeteaseMusicClient",
    "QQMusicClient",
    "KuwoMusicClient",
    "QianqianMusicClient",
]
MAX_SEARCHES = 8
MAX_KEYWORD = 200
WORK_DIR = os.environ.get("MUSICDL_WORK_DIR", "/downloads")

MIME_BY_EXT = {
    "mp3": "audio/mpeg",
    "flac": "audio/flac",
    "wav": "audio/wav",
    "m4a": "audio/mp4",
    "aac": "audio/aac",
    "ogg": "audio/ogg",
    "opus": "audio/opus",
    "wma": "audio/x-ms-wma",
    "ape": "audio/ape",
}


def sources_from_env() -> list[str]:
    raw = os.environ.get("MUSICDL_SOURCES", "")
    parsed = [part.strip() for part in raw.split(",") if part.strip()]
    return parsed or list(DEFAULT_SOURCES)


def search_size() -> int:
    try:
        size = int(os.environ.get("MUSICDL_SEARCH_SIZE", "3"))
    except ValueError:
        size = 3
    return max(1, min(size, 20))


def search_deadline() -> float:
    try:
        seconds = float(os.environ.get("MUSICDL_SEARCH_DEADLINE", "20"))
    except ValueError:
        seconds = 20
    return max(5.0, min(seconds, 120.0))


def text(value) -> str:
    if value is None:
        return ""
    return str(value)


def safe_filename(song, ext: str) -> str:
    artist = text(song.singers).strip() or "Unknown"
    title = text(song.song_name).strip() or "track"
    raw = f"{artist} - {title}.{ext}"
    cleaned = "".join("_" if c in '\\/:*?"<>|\n\r\t' else c for c in raw)
    cleaned = cleaned.strip().strip(".")
    return (cleaned or f"track.{ext}")[:180]


def mime_for(ext: str) -> str:
    return MIME_BY_EXT.get(ext, f"audio/{ext}" if ext else "audio/mpeg")


def flatten(song):
    episodes = song.episodes or []
    if not episodes:
        yield song
        return
    for episode in episodes:
        if not getattr(episode, "source", None):
            episode.source = song.source
        yield episode


class Catalog:
    def __init__(self) -> None:
        sources = sources_from_env()
        size = search_size()
        init_cfg = {
            source: {
                "work_dir": WORK_DIR,
                "search_size_per_source": size,
                "search_size_per_page": size,
                "max_retries": 1,
            }
            for source in sources
        }
        self.client = musicdl.MusicClient(
            music_sources=sources,
            init_music_clients_cfg=init_cfg,
            clients_threadings={source: 2 for source in sources},
            requests_overrides={source: {"timeout": (5, 8)} for source in sources},
        )
        self.cache_lock = threading.Lock()
        self.source_locks = {source: threading.Lock() for source in self.client.music_clients}
        self.deadline = search_deadline()
        self.searches: OrderedDict[str, dict[str, object]] = OrderedDict()

    def search(self, keyword: str) -> dict:
        keyword = keyword.strip()
        if not keyword:
            raise ValueError("keyword is required")
        if len(keyword) > MAX_KEYWORD:
            raise ValueError(f"keyword must be at most {MAX_KEYWORD} characters")
        started = time.monotonic()
        grouped = self._search_sources(keyword)
        songs: dict[str, object] = {}
        tracks = []
        for per_source in grouped.values():
            for song in per_source or []:
                for item in flatten(song):
                    track_id = str(len(songs))
                    songs[track_id] = item
                    ext = text(item.ext).lstrip(".").lower()
                    tracks.append(
                        {
                            "id": track_id,
                            "source": text(item.source),
                            "song_name": text(item.song_name),
                            "singers": text(item.singers),
                            "album": text(item.album),
                            "duration": text(item.duration),
                            "file_size": text(item.file_size),
                            "ext": ext,
                        }
                    )
        elapsed = time.monotonic() - started
        print(
            f"[musicdl-export] search {keyword!r}: {len(tracks)} tracks "
            f"from {', '.join(grouped) or 'no source'} in {elapsed:.1f}s"
        )
        if not tracks:
            raise RuntimeError(
                f"no songs returned within {int(self.deadline)}s; try again or narrow the query"
            )
        search_id = uuid.uuid4().hex
        with self.cache_lock:
            self.searches[search_id] = songs
            self.searches.move_to_end(search_id)
            while len(self.searches) > MAX_SEARCHES:
                self.searches.popitem(last=False)
        return {"search_id": search_id, "tracks": tracks}

    def _search_sources(self, keyword: str) -> dict:
        sources = list(self.client.music_clients)
        grouped: dict[str, list] = {}
        pool = ThreadPoolExecutor(max_workers=len(sources))
        futures = [pool.submit(self._search_source, source, keyword) for source in sources]
        try:
            for future in as_completed(futures, timeout=self.deadline):
                source, found = future.result()
                grouped[source] = found
        except TimeoutError:
            slow = [source for source in sources if source not in grouped]
            print(f"[musicdl-export] search deadline {int(self.deadline)}s, still waiting on {', '.join(slow)}")
        finally:
            pool.shutdown(wait=False, cancel_futures=True)
        return grouped

    def _search_source(self, source: str, keyword: str) -> tuple[str, list]:
        client = self.client.music_clients[source]
        try:
            with self.source_locks[source]:
                found = client.search(
                    keyword=keyword,
                    num_threadings=self.client.clients_threadings.get(source, 2),
                    request_overrides=self.client.requests_overrides.get(source, {}),
                    rule=self.client.search_rules.get(source, {}),
                )
            return source, found or []
        except Exception:
            traceback.print_exc()
            return source, []

    def download(self, search_id: str, track_id: str) -> tuple[bytes, str, str]:
        with self.cache_lock:
            songs = self.searches.get(search_id)
            if songs is None:
                raise KeyError("search expired; search again")
            song = songs.get(str(track_id))
            if song is None:
                raise KeyError("track not found in that search")
        source_lock = self.source_locks.get(text(getattr(song, "source", "")))
        if source_lock is None:
            downloaded = self.client.download([song]) or []
        else:
            with source_lock:
                downloaded = self.client.download([song]) or []
        if not downloaded:
            raise RuntimeError("musicdl did not save a file")
        path = Path(downloaded[0].save_path or "")
        if not path.is_file():
            raise RuntimeError("musicdl finished without an audio file")
        payload = path.read_bytes()
        path.unlink(missing_ok=True)
        ext = text(downloaded[0].ext).lstrip(".").lower() or path.suffix.lstrip(".").lower()
        name = safe_filename(downloaded[0], ext or "mp3")
        return payload, name, mime_for(ext or "mp3")


CATALOG: Catalog | None = None


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        print(f"[musicdl-export] {self.address_string()} {fmt % args}")

    def do_GET(self) -> None:
        if self.path.split("?", 1)[0] == "/health":
            self._json(200, {"status": "ok"})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0]
        try:
            body = self._read_json()
        except ValueError as err:
            self._json(400, {"error": str(err)})
            return
        try:
            if path == "/search":
                assert CATALOG is not None
                self._json(200, CATALOG.search(str(body.get("keyword", ""))))
            elif path == "/download":
                assert CATALOG is not None
                payload, name, mime = CATALOG.download(
                    str(body.get("search_id", "")),
                    str(body.get("track_id", "")),
                )
                self._file(payload, name, mime)
            else:
                self._json(404, {"error": "not found"})
        except ValueError as err:
            self._json(400, {"error": str(err)})
        except KeyError as err:
            message = err.args[0] if err.args and isinstance(err.args[0], str) else "not found"
            self._json(404, {"error": message})
        except Exception as err:
            traceback.print_exc()
            self._json(502, {"error": str(err) or "download failed"})

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length < 0 or length > 65_536:
            raise ValueError("body too large")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as err:
            raise ValueError("invalid json") from err
        if not isinstance(parsed, dict):
            raise ValueError("json object required")
        return parsed

    def _json(self, status: int, payload: dict) -> None:
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _file(self, payload: bytes, name: str, mime: str) -> None:
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-Song-Name", quote(name, safe=""))
        self.end_headers()
        self.wfile.write(payload)


def main() -> None:
    global CATALOG
    CATALOG = Catalog()
    port = int(os.environ.get("MUSICDL_PORT", "8090"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"[musicdl-export] listening on 0.0.0.0:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
