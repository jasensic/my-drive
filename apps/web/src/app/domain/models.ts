export type MediaKind = 'photo' | 'video' | 'audio' | 'other';

export interface AuthSession {
  token: string;
  user: { id: string; username: string };
}

export interface Album {
  id: string;
  name: string;
  created_at: string;
}

export interface MediaFile {
  id: string;
  album_id: string | null;
  name: string;
  size: number;
  mime: string;
  checksum: string;
  media_kind: MediaKind;
  created_at: string;
  uploaded_at: string;
  content_url: string;
  thumbnail_url: string | null;
}

export interface Device {
  id: string;
  name: string;
  last_sync_at: string | null;
}

export interface SyncRule {
  media_kind: MediaKind;
  max_age_days: number | null;
  max_size_bytes: number | null;
  include_all: boolean;
}

export interface SyncProfile {
  id: string;
  device_id: string;
  name: string;
  rules: SyncRule[];
}

export interface AppRelease {
  id: string;
  version_code: number;
  version_name: string;
  changelog: string;
  checksum: string;
  size: number;
  published_at: string;
  download_url: string;
}
