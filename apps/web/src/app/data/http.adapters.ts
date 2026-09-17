import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Album, AppRelease, AuthSession, Device, MediaFile, SyncProfile, SyncRule } from '../domain/models';
import {
  ALBUM_REPOSITORY,
  APP_RELEASE_REPOSITORY,
  AUTH_REPOSITORY,
  AlbumRepository,
  AppReleaseRepository,
  AuthRepository,
  DEVICE_REPOSITORY,
  DeviceRepository,
  FILE_REPOSITORY,
  FileRepository,
  TOKEN_STORE,
  TokenStore,
} from '../domain/ports';

@Injectable()
export class LocalTokenStore implements TokenStore {
  private readonly tokenKey = 'mydrive.token';
  private readonly userKey = 'mydrive.username';

  get(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  username(): string | null {
    return localStorage.getItem(this.userKey);
  }

  set(token: string, username: string): void {
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.userKey, username);
  }

  clear(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(TOKEN_STORE);
  const token = store.get();
  if (!token) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

@Injectable()
export class HttpAuthRepository implements AuthRepository {
  constructor(private readonly http: HttpClient) {}

  status(): Promise<{ setup_required: boolean }> {
    return firstValueFrom(this.http.get<{ setup_required: boolean }>('/v1/status'));
  }

  setup(username: string, password: string): Promise<AuthSession> {
    return firstValueFrom(this.http.post<AuthSession>('/v1/setup', { username, password }));
  }

  login(username: string, password: string): Promise<AuthSession> {
    return firstValueFrom(this.http.post<AuthSession>('/v1/login', { username, password }));
  }

  me(): Promise<{ id: string; username: string }> {
    return firstValueFrom(this.http.get<{ id: string; username: string }>('/v1/me'));
  }
}

@Injectable()
export class HttpAlbumRepository implements AlbumRepository {
  constructor(private readonly http: HttpClient) {}
  list(): Promise<Album[]> {
    return firstValueFrom(this.http.get<Album[]>('/v1/albums'));
  }
  create(name: string): Promise<Album> {
    return firstValueFrom(this.http.post<Album>('/v1/albums', { name }));
  }
}

@Injectable()
export class HttpFileRepository implements FileRepository {
  constructor(private readonly http: HttpClient) {}
  list(): Promise<MediaFile[]> {
    return firstValueFrom(this.http.get<MediaFile[]>('/v1/files'));
  }
  upload(file: File, albumId?: string): Promise<MediaFile> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (albumId) {
      body.append('album_id', albumId);
    }
    return firstValueFrom(this.http.post<MediaFile>('/v1/files', body));
  }
  assignAlbum(id: string, albumId: string | null): Promise<MediaFile> {
    return firstValueFrom(this.http.patch<MediaFile>(`/v1/files/${id}`, { album_id: albumId }));
  }
  contentUrl(id: string): string {
    return `/v1/files/${id}/content`;
  }
  blob(id: string, thumbnail: boolean): Promise<Blob> {
    const path = thumbnail ? `/v1/files/${id}/thumbnail` : `/v1/files/${id}/content`;
    return firstValueFrom(this.http.get(path, { responseType: 'blob' }));
  }
}

@Injectable()
export class HttpDeviceRepository implements DeviceRepository {
  constructor(private readonly http: HttpClient) {}
  list(): Promise<Device[]> {
    return firstValueFrom(this.http.get<Device[]>('/v1/devices'));
  }
  register(name: string): Promise<Device> {
    return firstValueFrom(this.http.post<Device>('/v1/devices', { name }));
  }
  getProfile(deviceId: string): Promise<SyncProfile> {
    return firstValueFrom(this.http.get<SyncProfile>(`/v1/devices/${deviceId}/sync-profile`));
  }
  saveProfile(deviceId: string, name: string, rules: SyncRule[]): Promise<SyncProfile> {
    return firstValueFrom(
      this.http.put<SyncProfile>(`/v1/devices/${deviceId}/sync-profile`, { name, rules }),
    );
  }
}

@Injectable()
export class HttpAppReleaseRepository implements AppReleaseRepository {
  constructor(private readonly http: HttpClient) {}

  list(): Promise<AppRelease[]> {
    return firstValueFrom(this.http.get<AppRelease[]>('/v1/app/releases'));
  }

  async latest(): Promise<AppRelease | null> {
    try {
      return await firstValueFrom(this.http.get<AppRelease>('/v1/app/releases/latest'));
    } catch {
      return null;
    }
  }

  publish(input: {
    versionCode: number;
    versionName: string;
    changelog: string;
    apk: File;
  }): Promise<AppRelease> {
    const body = new FormData();
    body.append('version_code', String(input.versionCode));
    body.append('version_name', input.versionName);
    body.append('changelog', input.changelog);
    body.append('apk', input.apk, input.apk.name);
    return firstValueFrom(this.http.post<AppRelease>('/v1/app/releases', body));
  }
}

export const DATA_PROVIDERS = [
  { provide: TOKEN_STORE, useClass: LocalTokenStore },
  { provide: AUTH_REPOSITORY, useClass: HttpAuthRepository },
  { provide: ALBUM_REPOSITORY, useClass: HttpAlbumRepository },
  { provide: FILE_REPOSITORY, useClass: HttpFileRepository },
  { provide: DEVICE_REPOSITORY, useClass: HttpDeviceRepository },
  { provide: APP_RELEASE_REPOSITORY, useClass: HttpAppReleaseRepository },
];
