import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Album, AppRelease, ApkIdentity, AuthSession, Device, LibrarySilo, MediaFile, SyncProfile, SyncRule } from '../domain/models';
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
  list(silo?: LibrarySilo, trash = false): Promise<MediaFile[]> {
    const params = silo ? `?silo=${silo}` : '';
    const path = trash ? `/v1/files/trash${params}` : `/v1/files${params}`;
    return firstValueFrom(this.http.get<MediaFile[]>(path));
  }
  get(id: string): Promise<MediaFile> {
    return firstValueFrom(this.http.get<MediaFile>(`/v1/files/${id}`));
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
  trash(id: string): Promise<MediaFile> {
    return firstValueFrom(this.http.post<MediaFile>(`/v1/files/${id}/trash`, {}));
  }
  restore(id: string): Promise<MediaFile> {
    return firstValueFrom(this.http.post<MediaFile>(`/v1/files/${id}/restore`, {}));
  }
  async purge(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/v1/files/${id}`));
  }
  emptyTrash(silo?: LibrarySilo): Promise<{ deleted: number }> {
    const params = silo ? `?silo=${silo}` : '';
    return firstValueFrom(this.http.delete<{ deleted: number }>(`/v1/files/trash${params}`));
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

  inspect(apk: File): Promise<ApkIdentity> {
    const body = new FormData();
    body.append('apk', apk, apk.name);
    return firstValueFrom(this.http.post<ApkIdentity>('/v1/app/releases/inspect', body));
  }

  publish(input: { changelog: string; apk: File }): Promise<AppRelease> {
    const body = new FormData();
    body.append('changelog', input.changelog);
    body.append('apk', input.apk, input.apk.name);
    return firstValueFrom(this.http.post<AppRelease>('/v1/app/releases', body));
  }

  update(id: string, input: { changelog?: string; apk?: File }): Promise<AppRelease> {
    const body = new FormData();
    if (input.changelog !== undefined) {
      body.append('changelog', input.changelog);
    }
    if (input.apk) {
      body.append('apk', input.apk, input.apk.name);
    }
    return firstValueFrom(this.http.patch<AppRelease>(`/v1/app/releases/${id}`, body));
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/v1/app/releases/${id}`));
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
