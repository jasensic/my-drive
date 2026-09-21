import {
  CloudImportAvailability,
  CloudImportBatchResult,
  CloudImportConfig,
  ImportedLocalFile,
  SpotifyPlaylist,
  SpotifyTrack,
} from '../domain/cloud-import.models';

export function cloudImportAvailability(config: CloudImportConfig): CloudImportAvailability {
  const googleClient = Boolean(config.googleClientId.trim());
  return {
    googlePhotos: googleClient,
    googleDrive: googleClient && Boolean(config.googleApiKey.trim()),
    spotify: Boolean(config.spotifyClientId.trim()),
  };
}

export function defaultSpotifyRedirectUri(origin: string, configured: string): string {
  const trimmed = configured.trim();
  if (trimmed) {
    return trimmed;
  }
  return `${origin.replace(/\/$/, '')}/imports/spotify/callback`;
}

export function summarizeImportBatch(imported: number, failed: number): string {
  if (imported === 0 && failed === 0) {
    return 'No files selected.';
  }
  if (failed === 0) {
    return `Imported ${imported} file${imported === 1 ? '' : 's'} into my-drive.`;
  }
  if (imported === 0) {
    return `Import failed for ${failed} file${failed === 1 ? '' : 's'}.`;
  }
  return `Imported ${imported} file${imported === 1 ? '' : 's'}; ${failed} failed.`;
}

export function toBatchResult(ok: number, errors: string[]): CloudImportBatchResult {
  return { imported: ok, failed: errors.length, errors };
}

export interface SpotifyPlaylistDto {
  id?: string;
  name?: string;
  tracks?: { total?: number };
  images?: Array<{ url?: string }>;
}

export interface SpotifyTrackDto {
  id?: string | null;
  name?: string;
  duration_ms?: number;
  preview_url?: string | null;
  uri?: string;
  external_urls?: { spotify?: string };
  artists?: Array<{ name?: string }>;
  album?: { name?: string };
}

export function mapSpotifyPlaylist(dto: SpotifyPlaylistDto): SpotifyPlaylist | null {
  if (!dto.id || !dto.name) {
    return null;
  }
  return {
    id: dto.id,
    name: dto.name,
    trackCount: dto.tracks?.total ?? 0,
    imageUrl: dto.images?.[0]?.url ?? null,
  };
}

export function mapSpotifyTrack(dto: SpotifyTrackDto): SpotifyTrack | null {
  if (!dto.id || !dto.name || !dto.uri) {
    return null;
  }
  return {
    id: dto.id,
    name: dto.name,
    artists: (dto.artists ?? []).map((a) => a.name ?? '').filter(Boolean),
    albumName: dto.album?.name ?? '',
    durationMs: dto.duration_ms ?? 0,
    previewUrl: dto.preview_url ?? null,
    uri: dto.uri,
    externalUrl: dto.external_urls?.spotify ?? `https://open.spotify.com/track/${dto.id}`,
  };
}

export function mapSpotifyPlaylists(items: SpotifyPlaylistDto[]): SpotifyPlaylist[] {
  return items.map(mapSpotifyPlaylist).filter((p): p is SpotifyPlaylist => p !== null);
}

export function mapSpotifyTracks(items: Array<{ track?: SpotifyTrackDto | null }>): SpotifyTrack[] {
  return items
    .map((item) => (item.track ? mapSpotifyTrack(item.track) : null))
    .filter((t): t is SpotifyTrack => t !== null);
}

export function spotifyEmbedUrl(trackId: string): string {
  return `https://open.spotify.com/embed/track/${encodeURIComponent(trackId)}`;
}

export function formatTrackDuration(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

export function bytesToImportedFile(name: string, mimeType: string, bytes: ArrayBuffer): ImportedLocalFile {
  const type = mimeType || 'application/octet-stream';
  return {
    name,
    mimeType: type,
    file: new File([bytes], name, { type }),
  };
}
