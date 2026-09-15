import { InjectionToken } from '@angular/core';
import { Album, AuthSession, Device, MediaFile, SyncProfile, SyncRule } from '../domain/models';

export interface CheckSetup {
  execute(): Promise<boolean>;
}

export interface SetupAdmin {
  execute(username: string, password: string): Promise<AuthSession>;
}

export interface Login {
  execute(username: string, password: string): Promise<AuthSession>;
}

export interface SessionQuery {
  hasSession(): boolean;
  username(): string | null;
  logout(): void;
}

export interface ListLibrary {
  execute(): Promise<{ files: MediaFile[]; albums: Album[] }>;
}

export interface UploadMedia {
  execute(file: File, albumId?: string): Promise<MediaFile>;
}

export interface CreateAlbum {
  execute(name: string): Promise<Album>;
}

export interface AssignFileAlbum {
  execute(fileId: string, albumId: string | null): Promise<MediaFile>;
}

export interface ListDevices {
  execute(): Promise<Device[]>;
}

export interface RegisterDevice {
  execute(name: string): Promise<Device>;
}

export interface LoadSyncProfile {
  execute(deviceId: string): Promise<SyncProfile>;
}

export interface SaveSyncProfile {
  execute(deviceId: string, name: string, rules: SyncRule[]): Promise<SyncProfile>;
}

export const CHECK_SETUP = new InjectionToken<CheckSetup>('CHECK_SETUP');
export const SETUP_ADMIN = new InjectionToken<SetupAdmin>('SETUP_ADMIN');
export const LOGIN = new InjectionToken<Login>('LOGIN');
export const SESSION_QUERY = new InjectionToken<SessionQuery>('SESSION_QUERY');
export const LIST_LIBRARY = new InjectionToken<ListLibrary>('LIST_LIBRARY');
export const UPLOAD_MEDIA = new InjectionToken<UploadMedia>('UPLOAD_MEDIA');
export const CREATE_ALBUM = new InjectionToken<CreateAlbum>('CREATE_ALBUM');
export const ASSIGN_FILE_ALBUM = new InjectionToken<AssignFileAlbum>('ASSIGN_FILE_ALBUM');
export const LIST_DEVICES = new InjectionToken<ListDevices>('LIST_DEVICES');
export const REGISTER_DEVICE = new InjectionToken<RegisterDevice>('REGISTER_DEVICE');
export const LOAD_SYNC_PROFILE = new InjectionToken<LoadSyncProfile>('LOAD_SYNC_PROFILE');
export const SAVE_SYNC_PROFILE = new InjectionToken<SaveSyncProfile>('SAVE_SYNC_PROFILE');
