import { InjectionToken } from '@angular/core';
import { Album, AppRelease, AuthSession, Device, MediaFile, SyncProfile, SyncRule } from './models';

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
  list(): Promise<MediaFile[]>;
  upload(file: File, albumId?: string): Promise<MediaFile>;
  assignAlbum(id: string, albumId: string | null): Promise<MediaFile>;
  contentUrl(id: string): string;
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
  publish(input: {
    versionCode: number;
    versionName: string;
    changelog: string;
    apk: File;
  }): Promise<AppRelease>;
}

export const TOKEN_STORE = new InjectionToken<TokenStore>('TOKEN_STORE');
export const AUTH_REPOSITORY = new InjectionToken<AuthRepository>('AUTH_REPOSITORY');
export const ALBUM_REPOSITORY = new InjectionToken<AlbumRepository>('ALBUM_REPOSITORY');
export const FILE_REPOSITORY = new InjectionToken<FileRepository>('FILE_REPOSITORY');
export const DEVICE_REPOSITORY = new InjectionToken<DeviceRepository>('DEVICE_REPOSITORY');
export const APP_RELEASE_REPOSITORY = new InjectionToken<AppReleaseRepository>('APP_RELEASE_REPOSITORY');
