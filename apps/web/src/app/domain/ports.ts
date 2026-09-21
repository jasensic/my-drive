import { InjectionToken } from '@angular/core';
import {
  CloudImportConfig,
  ImportedLocalFile,
  SpotifyPlaylist,
  SpotifyTrack,
} from './cloud-import.models';
import { MusicSearchResult } from './music.models';
import { Album, AppRelease, ApkIdentity, AuthSession, Device, LibrarySilo, MediaFile, SyncProfile, SyncRule } from './models';

export interface TokenStore {
  get(): string | null;
  username(): string | null;
  set(token: string, username: string): void;
  clear(): void;
}

export interface AuthRepository {
  status(): Promise<{ setup_required: boolean }>;
  setup(username: string, password: string): Promise<AuthSession>;
  login(username: string, password: string): Promise<AuthSession>;
  me(): Promise<{ id: string; username: string }>;
}

export interface AlbumRepository {
  list(): Promise<Album[]>;
  create(name: string): Promise<Album>;
}

export interface FileRepository {
  list(silo?: LibrarySilo, trash?: boolean): Promise<MediaFile[]>;
  get(id: string): Promise<MediaFile>;
  upload(file: File, albumId?: string): Promise<MediaFile>;
  assignAlbum(id: string, albumId: string | null): Promise<MediaFile>;
  trash(id: string): Promise<MediaFile>;
  restore(id: string): Promise<MediaFile>;
  purge(id: string): Promise<void>;
  emptyTrash(silo?: LibrarySilo): Promise<{ deleted: number }>;
  contentUrl(id: string): string;
  blob(id: string, thumbnail: boolean): Promise<Blob>;
}

export interface DeviceRepository {
  list(): Promise<Device[]>;
  register(name: string): Promise<Device>;
  getProfile(deviceId: string): Promise<SyncProfile>;
  saveProfile(deviceId: string, name: string, rules: SyncRule[]): Promise<SyncProfile>;
}

export interface AppReleaseRepository {
  list(): Promise<AppRelease[]>;
  latest(): Promise<AppRelease | null>;
  inspect(apk: File): Promise<ApkIdentity>;
  publish(input: { changelog: string; apk: File }): Promise<AppRelease>;
  update(id: string, input: { changelog?: string; apk?: File }): Promise<AppRelease>;
  remove(id: string): Promise<void>;
}

export interface CloudImportConfigPort {
  get(): CloudImportConfig;
  resolveSpotifyRedirectUri(): string;
}

/** Official Google Photos Picker API — user picks their own media; no Library API scrape. */
export interface GooglePhotosImportPort {
  pickFiles(): Promise<ImportedLocalFile[]>;
}

/** Official Google Picker + Drive download for the signed-in user's own files. */
export interface GoogleDriveImportPort {
  pickFiles(): Promise<ImportedLocalFile[]>;
}

/** Spotify Authorization Code + PKCE against the official accounts API. */
export interface SpotifyAuthPort {
  isConnected(): boolean;
  beginLogin(): Promise<void>;
  completeLogin(code: string, state: string): Promise<void>;
  logout(): void;
}

/**
 * Spotify Web API metadata only.
 * Playback is embed / previewUrl — never persist or download full audio.
 */
export interface SpotifyLibraryPort {
  listPlaylists(): Promise<SpotifyPlaylist[]>;
  listPlaylistTracks(playlistId: string): Promise<SpotifyTrack[]>;
}

/** musicdl-export search. Download URLs stay on the sidecar; import only sends ids. */
export interface MusicCatalog {
  search(keyword: string): Promise<MusicSearchResult>;
  importTrack(searchId: string, trackId: string): Promise<MediaFile>;
}

export const TOKEN_STORE = new InjectionToken<TokenStore>('TOKEN_STORE');
export const AUTH_REPOSITORY = new InjectionToken<AuthRepository>('AUTH_REPOSITORY');
export const ALBUM_REPOSITORY = new InjectionToken<AlbumRepository>('ALBUM_REPOSITORY');
export const FILE_REPOSITORY = new InjectionToken<FileRepository>('FILE_REPOSITORY');
export const DEVICE_REPOSITORY = new InjectionToken<DeviceRepository>('DEVICE_REPOSITORY');
export const APP_RELEASE_REPOSITORY = new InjectionToken<AppReleaseRepository>('APP_RELEASE_REPOSITORY');
export const CLOUD_IMPORT_CONFIG = new InjectionToken<CloudImportConfigPort>('CLOUD_IMPORT_CONFIG');
export const GOOGLE_PHOTOS_IMPORT = new InjectionToken<GooglePhotosImportPort>('GOOGLE_PHOTOS_IMPORT');
export const GOOGLE_DRIVE_IMPORT = new InjectionToken<GoogleDriveImportPort>('GOOGLE_DRIVE_IMPORT');
export const SPOTIFY_AUTH = new InjectionToken<SpotifyAuthPort>('SPOTIFY_AUTH');
export const SPOTIFY_LIBRARY = new InjectionToken<SpotifyLibraryPort>('SPOTIFY_LIBRARY');
export const MUSIC_CATALOG = new InjectionToken<MusicCatalog>('MUSIC_CATALOG');
