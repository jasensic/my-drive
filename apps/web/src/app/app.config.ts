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
import {
  AssignFileAlbumService,
  CreateAlbumService,
  EmptyTrashService,
  GetMediaService,
  ListLibraryService,
  LoadMediaBlobService,
  PurgeMediaService,
  RestoreMediaService,
  TrashMediaService,
  UploadMediaService,
} from './application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CHECK_SETUP,
  CREATE_ALBUM,
  EMPTY_TRASH,
  GET_MEDIA,
  LIST_APP_RELEASES,
  LIST_DEVICES,
  LIST_LIBRARY,
  LOAD_MEDIA_BLOB,
  LOAD_SYNC_PROFILE,
  LOGIN,
  INSPECT_APK,
  PUBLISH_APP_RELEASE,
  PURGE_MEDIA,
  REGISTER_DEVICE,
  RESTORE_MEDIA,
  SAVE_SYNC_PROFILE,
  SESSION_QUERY,
  SETUP_ADMIN,
  TRASH_MEDIA,
  UPDATE_APP_RELEASE,
  DELETE_APP_RELEASE,
  UPLOAD_MEDIA,
} from './application/use-cases.tokens';
import { DATA_PROVIDERS, authInterceptor } from './data/http.adapters';
import { PRIMENG_LICENSE } from '../primeng-license';
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
    ...DATA_PROVIDERS,
    { provide: CHECK_SETUP, useClass: CheckSetupService },
    { provide: SETUP_ADMIN, useClass: SetupAdminService },
    { provide: LOGIN, useClass: LoginService },
    { provide: SESSION_QUERY, useClass: SessionQueryService },
    { provide: LIST_LIBRARY, useClass: ListLibraryService },
    { provide: GET_MEDIA, useClass: GetMediaService },
    { provide: UPLOAD_MEDIA, useClass: UploadMediaService },
    { provide: CREATE_ALBUM, useClass: CreateAlbumService },
    { provide: ASSIGN_FILE_ALBUM, useClass: AssignFileAlbumService },
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
  ],
};
