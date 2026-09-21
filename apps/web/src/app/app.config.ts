import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { routes } from './app.routes';
import {
  CheckSetupService,
  LoginService,
  SessionQueryService,
  SetupAdminService,
} from './application/auth.use-cases';
import {
  ListDevicesService,
  LoadSyncProfileService,
  RegisterDeviceService,
  SaveSyncProfileService,
} from './application/devices.use-cases';
import {
  DeleteAppReleaseService,
  InspectApkService,
  ListAppReleasesService,
  PublishAppReleaseService,
  UpdateAppReleaseService,
} from './application/app-releases.use-cases';
import { DATA_PROVIDERS, authInterceptor } from './data/http.adapters';
import { MUSIC_DATA_PROVIDERS } from './data/music.adapters';
import { CLOUD_IMPORT_DATA_PROVIDERS } from './data/cloud-import.adapters';
import {
  ConnectSpotifyService,
  DisconnectSpotifyService,
  FinishSpotifyLoginService,
  GetCloudImportAvailabilityService,
  GetSpotifyStatusService,
  ImportGoogleDriveService,
  ImportGooglePhotosService,
  ListSpotifyPlaylistTracksService,
  ListSpotifyPlaylistsService,
} from './application/cloud-import.use-cases';
import { ImportMusicTrackService, SearchMusicService } from './application/music.use-cases';
import {
  AssignFileAlbumService,
  CreateAlbumService,
  DeleteAlbumService,
  EmptyTrashService,
  GetMediaService,
  ListLibraryService,
  LoadMediaBlobService,
  PurgeMediaService,
  RenameAlbumService,
  RenameMediaService,
  RestoreMediaService,
  ShareMediaService,
  TrashMediaService,
  UploadMediaService,
} from './application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CHECK_SETUP,
  CONNECT_SPOTIFY,
  CREATE_ALBUM,
  DISCONNECT_SPOTIFY,
  DELETE_ALBUM,
  EMPTY_TRASH,
  FINISH_SPOTIFY_LOGIN,
  GET_CLOUD_IMPORT_AVAILABILITY,
  GET_MEDIA,
  GET_SPOTIFY_STATUS,
  IMPORT_GOOGLE_DRIVE,
  IMPORT_GOOGLE_PHOTOS,
  LIST_APP_RELEASES,
  LIST_DEVICES,
  LIST_LIBRARY,
  LIST_SPOTIFY_PLAYLISTS,
  LIST_SPOTIFY_PLAYLIST_TRACKS,
  SEARCH_MUSIC,
  IMPORT_MUSIC_TRACK,
  LOAD_MEDIA_BLOB,
  LOAD_SYNC_PROFILE,
  LOGIN,
  INSPECT_APK,
  PUBLISH_APP_RELEASE,
  PURGE_MEDIA,
  REGISTER_DEVICE,
  RENAME_ALBUM,
  RENAME_MEDIA,
  RESTORE_MEDIA,
  SAVE_SYNC_PROFILE,
  SESSION_QUERY,
  SETUP_ADMIN,
  SHARE_MEDIA,
  TRASH_MEDIA,
  UPDATE_APP_RELEASE,
  DELETE_APP_RELEASE,
  UPLOAD_MEDIA,
} from './application/use-cases.tokens';
import { PRIMENG_LICENSE } from '../primeng-license';
import { MusicPlaybackService } from './presentation/music-playback.service';
import { ThemeModeService } from './presentation/theme.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    providePrimeNG({
      theme: { preset: Aura, options: { darkModeSelector: '.app-dark' } },
      license: PRIMENG_LICENSE,
    }),
    MessageService,
    ThemeModeService,
    MusicPlaybackService,
    ...DATA_PROVIDERS,
    ...CLOUD_IMPORT_DATA_PROVIDERS,
    ...MUSIC_DATA_PROVIDERS,
    { provide: CHECK_SETUP, useClass: CheckSetupService },
    { provide: SETUP_ADMIN, useClass: SetupAdminService },
    { provide: LOGIN, useClass: LoginService },
    { provide: SESSION_QUERY, useClass: SessionQueryService },
    { provide: LIST_LIBRARY, useClass: ListLibraryService },
    { provide: GET_MEDIA, useClass: GetMediaService },
    { provide: UPLOAD_MEDIA, useClass: UploadMediaService },
    { provide: CREATE_ALBUM, useClass: CreateAlbumService },
    { provide: RENAME_ALBUM, useClass: RenameAlbumService },
    { provide: DELETE_ALBUM, useClass: DeleteAlbumService },
    { provide: ASSIGN_FILE_ALBUM, useClass: AssignFileAlbumService },
    { provide: RENAME_MEDIA, useClass: RenameMediaService },
    { provide: SHARE_MEDIA, useClass: ShareMediaService },
    { provide: TRASH_MEDIA, useClass: TrashMediaService },
    { provide: RESTORE_MEDIA, useClass: RestoreMediaService },
    { provide: PURGE_MEDIA, useClass: PurgeMediaService },
    { provide: EMPTY_TRASH, useClass: EmptyTrashService },
    { provide: LOAD_MEDIA_BLOB, useClass: LoadMediaBlobService },
    { provide: LIST_DEVICES, useClass: ListDevicesService },
    { provide: REGISTER_DEVICE, useClass: RegisterDeviceService },
    { provide: LOAD_SYNC_PROFILE, useClass: LoadSyncProfileService },
    { provide: SAVE_SYNC_PROFILE, useClass: SaveSyncProfileService },
    { provide: LIST_APP_RELEASES, useClass: ListAppReleasesService },
    { provide: INSPECT_APK, useClass: InspectApkService },
    { provide: PUBLISH_APP_RELEASE, useClass: PublishAppReleaseService },
    { provide: UPDATE_APP_RELEASE, useClass: UpdateAppReleaseService },
    { provide: DELETE_APP_RELEASE, useClass: DeleteAppReleaseService },
    { provide: GET_CLOUD_IMPORT_AVAILABILITY, useClass: GetCloudImportAvailabilityService },
    { provide: IMPORT_GOOGLE_PHOTOS, useClass: ImportGooglePhotosService },
    { provide: IMPORT_GOOGLE_DRIVE, useClass: ImportGoogleDriveService },
    { provide: GET_SPOTIFY_STATUS, useClass: GetSpotifyStatusService },
    { provide: CONNECT_SPOTIFY, useClass: ConnectSpotifyService },
    { provide: FINISH_SPOTIFY_LOGIN, useClass: FinishSpotifyLoginService },
    { provide: DISCONNECT_SPOTIFY, useClass: DisconnectSpotifyService },
    { provide: LIST_SPOTIFY_PLAYLISTS, useClass: ListSpotifyPlaylistsService },
    { provide: LIST_SPOTIFY_PLAYLIST_TRACKS, useClass: ListSpotifyPlaylistTracksService },
    { provide: SEARCH_MUSIC, useClass: SearchMusicService },
    { provide: IMPORT_MUSIC_TRACK, useClass: ImportMusicTrackService },
  ],
};
