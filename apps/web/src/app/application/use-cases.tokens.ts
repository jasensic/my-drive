import { InjectionToken } from '@angular/core';
import { Album, AppRelease, AuthSession, Device, LibrarySilo, MediaFile, SyncProfile, SyncRule } from '../domain/models';

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
  execute(silo?: LibrarySilo, trash?: boolean): Promise<{ files: MediaFile[]; albums: Album[] }>;
}

export interface GetMedia {
  execute(fileId: string): Promise<MediaFile>;
}

export interface UploadMedia {
  execute(file: File, albumId?: string): Promise<MediaFile>;
}

export interface CreateAlbum {
  execute(name: string, silo: LibrarySilo): Promise<Album>;
}

export interface RenameAlbum {
  execute(id: string, name: string): Promise<Album>;
}

export interface DeleteAlbum {
  execute(id: string): Promise<void>;
}

export interface AssignFileAlbum {
  execute(fileId: string, albumId: string | null): Promise<MediaFile>;
}

export interface RenameMedia {
  execute(fileId: string, name: string): Promise<MediaFile>;
}

export interface ShareMedia {
  execute(files: MediaFile[]): Promise<{ name: string; mime: string; blob: Blob }[]>;
}

export interface TrashMedia {
  execute(fileId: string): Promise<MediaFile>;
}

export interface RestoreMedia {
  execute(fileId: string): Promise<MediaFile>;
}

export interface PurgeMedia {
  execute(fileId: string): Promise<void>;
}

export interface EmptyTrash {
  execute(silo?: LibrarySilo): Promise<{ deleted: number }>;
}

export interface LoadMediaBlob {
  execute(fileId: string, thumbnail: boolean): Promise<string>;
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

export interface ListAppReleases {
  execute(): Promise<AppRelease[]>;
}

export interface PublishAppRelease {
  execute(input: { changelog: string; apk: File }): Promise<AppRelease>;
}

export interface InspectApk {
  execute(apk: File): Promise<{ version_code: number; version_name: string }>;
}

export interface UpdateAppRelease {
  execute(id: string, input: { changelog?: string; apk?: File }): Promise<AppRelease>;
}

export interface DeleteAppRelease {
  execute(id: string): Promise<void>;
}

export const CHECK_SETUP = new InjectionToken<CheckSetup>('CHECK_SETUP');
export const SETUP_ADMIN = new InjectionToken<SetupAdmin>('SETUP_ADMIN');
export const LOGIN = new InjectionToken<Login>('LOGIN');
export const SESSION_QUERY = new InjectionToken<SessionQuery>('SESSION_QUERY');
export const LIST_LIBRARY = new InjectionToken<ListLibrary>('LIST_LIBRARY');
export const GET_MEDIA = new InjectionToken<GetMedia>('GET_MEDIA');
export const UPLOAD_MEDIA = new InjectionToken<UploadMedia>('UPLOAD_MEDIA');
export const CREATE_ALBUM = new InjectionToken<CreateAlbum>('CREATE_ALBUM');
export const RENAME_ALBUM = new InjectionToken<RenameAlbum>('RENAME_ALBUM');
export const DELETE_ALBUM = new InjectionToken<DeleteAlbum>('DELETE_ALBUM');
export const ASSIGN_FILE_ALBUM = new InjectionToken<AssignFileAlbum>('ASSIGN_FILE_ALBUM');
export const RENAME_MEDIA = new InjectionToken<RenameMedia>('RENAME_MEDIA');
export const SHARE_MEDIA = new InjectionToken<ShareMedia>('SHARE_MEDIA');
export const TRASH_MEDIA = new InjectionToken<TrashMedia>('TRASH_MEDIA');
export const RESTORE_MEDIA = new InjectionToken<RestoreMedia>('RESTORE_MEDIA');
export const PURGE_MEDIA = new InjectionToken<PurgeMedia>('PURGE_MEDIA');
export const EMPTY_TRASH = new InjectionToken<EmptyTrash>('EMPTY_TRASH');
export const LOAD_MEDIA_BLOB = new InjectionToken<LoadMediaBlob>('LOAD_MEDIA_BLOB');
export const LIST_DEVICES = new InjectionToken<ListDevices>('LIST_DEVICES');
export const REGISTER_DEVICE = new InjectionToken<RegisterDevice>('REGISTER_DEVICE');
export const LOAD_SYNC_PROFILE = new InjectionToken<LoadSyncProfile>('LOAD_SYNC_PROFILE');
export const SAVE_SYNC_PROFILE = new InjectionToken<SaveSyncProfile>('SAVE_SYNC_PROFILE');
export const LIST_APP_RELEASES = new InjectionToken<ListAppReleases>('LIST_APP_RELEASES');
export const PUBLISH_APP_RELEASE = new InjectionToken<PublishAppRelease>('PUBLISH_APP_RELEASE');
export const INSPECT_APK = new InjectionToken<InspectApk>('INSPECT_APK');
export const UPDATE_APP_RELEASE = new InjectionToken<UpdateAppRelease>('UPDATE_APP_RELEASE');
export const DELETE_APP_RELEASE = new InjectionToken<DeleteAppRelease>('DELETE_APP_RELEASE');
