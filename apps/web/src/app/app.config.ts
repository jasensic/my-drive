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
  ListLibraryService,
  LoadMediaBlobService,
  UploadMediaService,
} from './application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CHECK_SETUP,
  CREATE_ALBUM,
  LIST_APP_RELEASES,
  LIST_DEVICES,
  LIST_LIBRARY,
  LOAD_MEDIA_BLOB,
  LOAD_SYNC_PROFILE,
  LOGIN,
  INSPECT_APK,
  PUBLISH_APP_RELEASE,
  REGISTER_DEVICE,
  SAVE_SYNC_PROFILE,
  SESSION_QUERY,
  SETUP_ADMIN,
  UPDATE_APP_RELEASE,
  DELETE_APP_RELEASE,
  UPLOAD_MEDIA,
} from './application/use-cases.tokens';
import { DATA_PROVIDERS, authInterceptor } from './data/http.adapters';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    providePrimeNG({ theme: { preset: Aura } }),
    MessageService,
    ...DATA_PROVIDERS,
    { provide: CHECK_SETUP, useClass: CheckSetupService },
    { provide: SETUP_ADMIN, useClass: SetupAdminService },
    { provide: LOGIN, useClass: LoginService },
    { provide: SESSION_QUERY, useClass: SessionQueryService },
    { provide: LIST_LIBRARY, useClass: ListLibraryService },
    { provide: UPLOAD_MEDIA, useClass: UploadMediaService },
    { provide: CREATE_ALBUM, useClass: CreateAlbumService },
    { provide: ASSIGN_FILE_ALBUM, useClass: AssignFileAlbumService },
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
