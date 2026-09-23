export type MediaKind = 'photo' | 'video' | 'audio' | 'other';
export type LibrarySilo = 'music' | 'photos' | 'files';
export type ThemeMode = 'light' | 'dark';

export const TRASH_RETENTION_DAYS = 30;

export function siloContains(silo: LibrarySilo, kind: MediaKind): boolean {
  switch (silo) {
    case 'music':
      return kind === 'audio';
    case 'photos':
      return kind === 'photo' || kind === 'video';
    case 'files':
      return kind === 'other';
  }
}

export function siloForKind(kind: MediaKind): LibrarySilo {
  if (kind === 'audio') {
    return 'music';
  }
  if (kind === 'photo' || kind === 'video') {
    return 'photos';
  }
  return 'files';
}

export interface AuthSession {
  token: string;
  user: { id: string; username: string };
}

export interface UserProfile {
  id: string;
  username: string;
}

export type ShareResourceType = 'file' | 'album';
export type SharePermission = 'read' | 'write';
export type ResourceAccess = 'owner' | 'read' | 'write';

export function isLibraryOwner(item: { access?: ResourceAccess }): boolean {
  return item.access !== 'read' && item.access !== 'write';
}

export interface ShareGrant {
  id: string;
  resource_type: ShareResourceType;
  resource_id: string;
  owner_id: string;
  grantee_id: string;
  grantee_username: string;
  permission: SharePermission;
  created_at: string;
}

export interface Album {
  id: string;
  owner_id?: string;
  name: string;
  silo: LibrarySilo;
  created_at: string;
  access?: ResourceAccess;
  shared?: boolean;
}

export interface MediaFile {
  id: string;
  owner_id?: string;
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
  preview_url?: string;
  media_url?: string;
  deleted_at?: string | null;
  purge_at?: string | null;
  access?: ResourceAccess;
  shared?: boolean;
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

export interface ApkIdentity {
  version_code: number;
  version_name: string;
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
