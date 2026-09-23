import { InjectionToken } from '@angular/core';
import {
  CloudImportAvailability,
  CloudImportBatchResult,
  SpotifyConnectionStatus,
  SpotifyPlaylist,
  SpotifyTrack,
} from '../domain/cloud-import.models';
import { MusicSearchResult } from '../domain/music.models';
import { Album, AppRelease, AuthSession, Device, LibrarySilo, MediaFile, ShareGrant, SharePermission, ShareResourceType, SyncProfile, SyncRule, UserProfile } from '../domain/models';

export interface CheckSetup {
  execute(): Promise<boolean>;
}

export interface SetupAdmin {
  execute(username: string, password: string): Promise<AuthSession>;
}

export interface Login {
  execute(username: string, password: string): Promise<AuthSession>;
}

export interface RegisterAccount {
  execute(username: string, password: string): Promise<AuthSession>;
}

export interface ListUsers {
  execute(): Promise<UserProfile[]>;
}

export interface SessionQuery {
  hasSession(): boolean;
  username(): string | null;
  userId(): string | null;
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

export interface ListShares {
  execute(resourceType?: ShareResourceType, resourceId?: string): Promise<ShareGrant[]>;
}

export interface CreateShare {
  execute(input: {
    resourceType: ShareResourceType;
    resourceId: string;
    granteeId: string;
    permission: SharePermission;
  }): Promise<ShareGrant>;
}

export interface RevokeShare {
  execute(id: string): Promise<void>;
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

export interface GetCloudImportAvailability {
  execute(): CloudImportAvailability;
}

export interface ImportGooglePhotos {
  execute(albumId?: string): Promise<CloudImportBatchResult>;
}

export interface ImportGoogleDrive {
  execute(albumId?: string): Promise<CloudImportBatchResult>;
}

export interface GetSpotifyStatus {
  execute(): SpotifyConnectionStatus;
}

export interface ConnectSpotify {
  execute(): Promise<void>;
}

export interface FinishSpotifyLogin {
  execute(code: string, state: string): Promise<void>;
}

export interface DisconnectSpotify {
  execute(): void;
}

export interface ListSpotifyPlaylists {
  execute(): Promise<SpotifyPlaylist[]>;
}

export interface ListSpotifyPlaylistTracks {
  execute(playlistId: string): Promise<SpotifyTrack[]>;
}

export interface SearchMusic {
  execute(keyword: string): Promise<MusicSearchResult>;
}

export interface ImportMusicTrack {
  execute(searchId: string, trackId: string): Promise<MediaFile>;
}

export const CHECK_SETUP = new InjectionToken<CheckSetup>('CHECK_SETUP');
export const SETUP_ADMIN = new InjectionToken<SetupAdmin>('SETUP_ADMIN');
export const LOGIN = new InjectionToken<Login>('LOGIN');
export const REGISTER_ACCOUNT = new InjectionToken<RegisterAccount>('REGISTER_ACCOUNT');
export const LIST_USERS = new InjectionToken<ListUsers>('LIST_USERS');
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
export const LIST_SHARES = new InjectionToken<ListShares>('LIST_SHARES');
export const CREATE_SHARE = new InjectionToken<CreateShare>('CREATE_SHARE');
export const REVOKE_SHARE = new InjectionToken<RevokeShare>('REVOKE_SHARE');
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
export const GET_CLOUD_IMPORT_AVAILABILITY = new InjectionToken<GetCloudImportAvailability>(
  'GET_CLOUD_IMPORT_AVAILABILITY',
);
export const IMPORT_GOOGLE_PHOTOS = new InjectionToken<ImportGooglePhotos>('IMPORT_GOOGLE_PHOTOS');
export const IMPORT_GOOGLE_DRIVE = new InjectionToken<ImportGoogleDrive>('IMPORT_GOOGLE_DRIVE');
export const GET_SPOTIFY_STATUS = new InjectionToken<GetSpotifyStatus>('GET_SPOTIFY_STATUS');
export const CONNECT_SPOTIFY = new InjectionToken<ConnectSpotify>('CONNECT_SPOTIFY');
export const FINISH_SPOTIFY_LOGIN = new InjectionToken<FinishSpotifyLogin>('FINISH_SPOTIFY_LOGIN');
export const DISCONNECT_SPOTIFY = new InjectionToken<DisconnectSpotify>('DISCONNECT_SPOTIFY');
export const LIST_SPOTIFY_PLAYLISTS = new InjectionToken<ListSpotifyPlaylists>('LIST_SPOTIFY_PLAYLISTS');
export const LIST_SPOTIFY_PLAYLIST_TRACKS = new InjectionToken<ListSpotifyPlaylistTracks>(
  'LIST_SPOTIFY_PLAYLIST_TRACKS',
);
export const SEARCH_MUSIC = new InjectionToken<SearchMusic>('SEARCH_MUSIC');
export const IMPORT_MUSIC_TRACK = new InjectionToken<ImportMusicTrack>('IMPORT_MUSIC_TRACK');
