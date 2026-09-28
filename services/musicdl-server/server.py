"""Small HTTP API in front of musicdl.

Search keeps SongInfo objects in memory. Download uses that cache so the
portal never sees download URLs or cookies.

POST /search    {"keyword": "..."}
POST /search/{id}/cancel
POST /playlist  {"url": "https://open.spotify.com/playlist/..."}
POST /download  {"search_id": "...", "track_id": "..."}  -> audio bytes
GET  /search/{id}
GET  /health

Spotify is not a search source. Playlist parsing uses its own client so a
slow playlist read does not block keyword search.
"""

from __future__ import annotations

import json
import os
import threading
import time
import traceback
import uuid
from collections import OrderedDict
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit

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
MAX_PLAYLIST_URL = 500
SPOTIFY_PLAYLIST_SOURCE = "SpotifyMusicClient"
SPOTIFY_HOSTS = {"open.spotify.com", "play.spotify.com", "spotify.com", "spotify.link"}
MIN_TRACK_BYTES = 1024 * 1024
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


def normalize_spotify_playlist_url(raw: str) -> str:
    value = raw.strip()
    if not value:
        raise ValueError("A Spotify playlist link is required")
    if len(value) > MAX_PLAYLIST_URL:
        raise ValueError(f"playlist url must be at most {MAX_PLAYLIST_URL} characters")
    if value.startswith("spotify:playlist:"):
        playlist_id = value.split(":", 2)[2].split("?")[0].split("&")[0].strip()
        _require_playlist_id(playlist_id)
        return f"https://open.spotify.com/playlist/{playlist_id}"
    parsed = urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("A Spotify playlist link is required")
    host = parsed.hostname.lower().removeprefix("www.")
    if host not in SPOTIFY_HOSTS:
        raise ValueError("A Spotify playlist link is required")
    if host == "spotify.link":
        if parsed.path in {"", "/"}:
            raise ValueError("A Spotify playlist link is required")
        return urlunsplit(("https", parsed.netloc, parsed.path, parsed.query, ""))
    parts = [part for part in parsed.path.split("/") if part]
    try:
        playlist_id = parts[parts.index("playlist") + 1]
    except (ValueError, IndexError) as err:
        raise ValueError("A Spotify playlist link is required") from err
    _require_playlist_id(playlist_id)
    return f"https://open.spotify.com/playlist/{playlist_id}"


def _require_playlist_id(playlist_id: str) -> None:
    if not playlist_id.isascii() or not playlist_id.isalnum() or not 10 <= len(playlist_id) <= 32:
        raise ValueError("A Spotify playlist link is required")


def text(value) -> str:
    if value is None:
        return ""
    return str(value)


def http_url(value) -> str:
    url = text(value).strip()
    if url.startswith("https://") or url.startswith("http://"):
        return url
    return ""


def is_full_track(item) -> bool:
    raw = getattr(item, "file_size_bytes", None)
    try:
        size = int(raw)
    except (TypeError, ValueError):
        return True
    if size <= 0:
        return True
    return size >= MIN_TRACK_BYTES


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


class SearchJob:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.songs: dict[str, object] = {}
        self.tracks: list[dict] = []
        self.done = False
        self.cancelled = False
        self.cancel_event = threading.Event()
        self.error: str | None = None

    def snapshot(self, search_id: str) -> dict:
        with self.lock:
            return {
                "search_id": search_id,
                "tracks": list(self.tracks),
                "done": self.done,
                "error": self.error,
            }

    def append_source(self, found: list) -> tuple[int, int]:
        added = 0
        skipped = 0
        with self.lock:
            for song in found or []:
                for item in flatten(song):
                    if not is_full_track(item):
                        skipped += 1
                        continue
                    track_id = str(len(self.songs))
                    self.songs[track_id] = item
                    ext = text(item.ext).lstrip(".").lower()
                    self.tracks.append(
                        {
                            "id": track_id,
                            "source": text(item.source),
                            "song_name": text(item.song_name),
                            "singers": text(item.singers),
                            "album": text(item.album),
                            "duration": text(item.duration),
                            "file_size": text(item.file_size),
                            "ext": ext,
                            "cover_url": http_url(getattr(item, "cover_url", "")),
                        }
                    )
                    added += 1
        return added, skipped

    def finish(self, error: str | None = None) -> None:
        with self.lock:
            self.done = True
            if self.cancelled:
                return
            if error:
                self.error = error
            elif not self.tracks and not self.error:
                self.error = "no songs returned; try again or narrow the query"

    def cancel(self) -> None:
        with self.lock:
            self.cancelled = True
            self.done = True
        self.cancel_event.set()


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
        self.playlist_client: musicdl.MusicClient | None = None
        self.playlist_init_lock = threading.Lock()
        self.deadline = search_deadline()
        self.searches: OrderedDict[str, SearchJob] = OrderedDict()

    def search(self, keyword: str) -> dict:
        keyword = keyword.strip()
        if not keyword:
            raise ValueError("keyword is required")
        if len(keyword) > MAX_KEYWORD:
            raise ValueError(f"keyword must be at most {MAX_KEYWORD} characters")
        job = SearchJob()
        search_id = uuid.uuid4().hex
        with self.cache_lock:
            self.searches[search_id] = job
            self.searches.move_to_end(search_id)
            while len(self.searches) > MAX_SEARCHES:
                self.searches.popitem(last=False)
        thread = threading.Thread(
            target=self._run_search,
            args=(search_id, job, keyword),
            daemon=True,
            name=f"musicdl-search-{search_id[:8]}",
        )
        thread.start()
        return job.snapshot(search_id)

    def playlist(self, url: str) -> dict:
        normalized = normalize_spotify_playlist_url(url)
        job = SearchJob()
        search_id = uuid.uuid4().hex
        with self.cache_lock:
            self.searches[search_id] = job
            self.searches.move_to_end(search_id)
            while len(self.searches) > MAX_SEARCHES:
                self.searches.popitem(last=False)
        thread = threading.Thread(
            target=self._run_playlist,
            args=(search_id, job, normalized),
            daemon=True,
            name=f"musicdl-playlist-{search_id[:8]}",
        )
        thread.start()
        return job.snapshot(search_id)

    def snapshot(self, search_id: str) -> dict:
        with self.cache_lock:
            job = self.searches.get(search_id)
        if job is None:
            raise KeyError("search expired; search again")
        return job.snapshot(search_id)

    def cancel(self, search_id: str) -> dict:
        with self.cache_lock:
            job = self.searches.get(search_id)
        if job is None:
            raise KeyError("search expired; search again")
        job.cancel()
        return job.snapshot(search_id)

    def _run_search(self, search_id: str, job: SearchJob, keyword: str) -> None:
        started = time.monotonic()
        sources = list(self.client.music_clients)
        pool = ThreadPoolExecutor(max_workers=len(sources) or 1)
        futures = [pool.submit(self._search_source, source, keyword) for source in sources]
        pending = set(futures)
        skipped = 0
        answered: list[str] = []
        deadline = time.monotonic() + self.deadline
        try:
            while pending and not job.cancel_event.is_set() and time.monotonic() < deadline:
                done, pending = wait(
                    pending,
                    timeout=min(0.4, max(0.0, deadline - time.monotonic())),
                    return_when=FIRST_COMPLETED,
                )
                for future in done:
                    source, found = future.result()
                    added, source_skipped = job.append_source(found)
                    skipped += source_skipped
                    answered.append(source)
                    print(
                        f"[musicdl-export] search {keyword!r}: +{added} from {source} "
                        f"({len(job.snapshot(search_id)['tracks'])} total)"
                    )
            if job.cancel_event.is_set():
                print(f"[musicdl-export] search {keyword!r} cancelled")
                return
            if pending:
                slow = [source for source in sources if source not in answered]
                print(
                    f"[musicdl-export] search deadline {int(self.deadline)}s, "
                    f"still waiting on {', '.join(slow)}"
                )
        except Exception as err:
            traceback.print_exc()
            job.finish(str(err) or "search failed")
            return
        finally:
            pool.shutdown(wait=False, cancel_futures=True)
        elapsed = time.monotonic() - started
        print(
            f"[musicdl-export] search {keyword!r}: "
            f"{len(job.snapshot(search_id)['tracks'])} tracks "
            f"({skipped} under 1MB skipped) "
            f"from {', '.join(answered) or 'no source'} in {elapsed:.1f}s"
        )
        job.finish()

    def _search_source(self, source: str, keyword: str) -> tuple[str, list]:
        client = self.client.music_clients[source]
        lock = self.source_locks[source]
        if not lock.acquire(timeout=2):
            print(f"[musicdl-export] search {keyword!r}: {source} busy, skipped")
            return source, []
        try:
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
        finally:
            lock.release()

    def _spotify_client(self) -> musicdl.MusicClient:
        with self.playlist_init_lock:
            if self.playlist_client is None:
                size = search_size()
                self.playlist_client = musicdl.MusicClient(
                    music_sources=[SPOTIFY_PLAYLIST_SOURCE],
                    init_music_clients_cfg={
                        SPOTIFY_PLAYLIST_SOURCE: {
                            "work_dir": WORK_DIR,
                            "search_size_per_source": size,
                            "search_size_per_page": size,
                            "max_retries": 1,
                        }
                    },
                    clients_threadings={SPOTIFY_PLAYLIST_SOURCE: 2},
                    requests_overrides={SPOTIFY_PLAYLIST_SOURCE: {"timeout": (8, 30)}},
                )
                self.source_locks.setdefault(SPOTIFY_PLAYLIST_SOURCE, threading.Lock())
            return self.playlist_client

    def _run_playlist(self, search_id: str, job: SearchJob, url: str) -> None:
        started = time.monotonic()
        try:
            client = self._spotify_client()
            spotify = client.music_clients[SPOTIFY_PLAYLIST_SOURCE]
            lock = self.source_locks[SPOTIFY_PLAYLIST_SOURCE]
            if job.cancel_event.is_set() or not lock.acquire(timeout=2):
                if not job.cancel_event.is_set():
                    job.finish("playlist is still stopping; try again")
                return
            try:
                if job.cancel_event.is_set():
                    return
                found = spotify.parseplaylist(
                    url,
                    request_overrides=client.requests_overrides.get(SPOTIFY_PLAYLIST_SOURCE, {}),
                )
            finally:
                lock.release()
            added, skipped = job.append_source(found or [])
            elapsed = time.monotonic() - started
            print(
                f"[musicdl-export] playlist {url!r}: +{added} "
                f"({skipped} under 1MB skipped) in {elapsed:.1f}s"
            )
            if added == 0:
                job.finish("no songs returned; check the playlist link")
            else:
                job.finish()
        except Exception as err:
            traceback.print_exc()
            job.finish(str(err) or "playlist failed")

    def download(self, search_id: str, track_id: str) -> tuple[bytes, str, str]:
        with self.cache_lock:
            job = self.searches.get(search_id)
        if job is None:
            raise KeyError("search expired; search again")
        with job.lock:
            song = job.songs.get(str(track_id))
        if song is None:
            raise KeyError("track not found in that search")
        source = text(getattr(song, "source", ""))
        client = self._spotify_client() if source == SPOTIFY_PLAYLIST_SOURCE else self.client
        source_lock = self.source_locks.get(source)
        if source_lock is None:
            downloaded = client.download([song]) or []
        else:
            with source_lock:
                downloaded = client.download([song]) or []
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
        path = self.path.split("?", 1)[0]
        if path == "/health":
            self._json(200, {"status": "ok"})
            return
        if path.startswith("/search/"):
            search_id = path[len("/search/") :].strip("/")
            try:
                assert CATALOG is not None
                self._json(200, CATALOG.snapshot(search_id))
            except KeyError as err:
                message = err.args[0] if err.args and isinstance(err.args[0], str) else "not found"
                self._json(404, {"error": message})
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
            elif path.startswith("/search/") and path.endswith("/cancel"):
                assert CATALOG is not None
                search_id = path[len("/search/") : -len("/cancel")].strip("/")
                self._json(200, CATALOG.cancel(search_id))
            elif path == "/playlist":
                assert CATALOG is not None
                self._json(200, CATALOG.playlist(str(body.get("url", ""))))
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
