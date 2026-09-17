pub mod error;
pub mod usecases;

use std::sync::Arc;

use domain::ports::{
    AlbumRepository, ApkInspector, AppReleaseRepository, Clock, DeviceRepository, FileRepository, ObjectStore,
    PasswordHasher, SyncProfileRepository, Thumbnailer, TokenService, UserRepository,
};

pub use error::AppError;
pub use usecases::*;

#[derive(Clone)]
pub struct Deps {
    pub users: Arc<dyn UserRepository>,
    pub albums: Arc<dyn AlbumRepository>,
    pub files: Arc<dyn FileRepository>,
    pub devices: Arc<dyn DeviceRepository>,
    pub profiles: Arc<dyn SyncProfileRepository>,
    pub app_releases: Arc<dyn AppReleaseRepository>,
    pub objects: Arc<dyn ObjectStore>,
    pub hasher: Arc<dyn PasswordHasher>,
    pub tokens: Arc<dyn TokenService>,
    pub clock: Arc<dyn Clock>,
    pub thumbnailer: Arc<dyn Thumbnailer>,
    pub apk_inspector: Arc<dyn ApkInspector>,
}

/// Composition helper used by the presentation binary. Handlers still depend on the
/// individual use-case traits, never on this struct's concrete service types.
pub struct Services {
    pub check_setup: Arc<dyn CheckSetup>,
    pub setup_admin: Arc<dyn SetupAdmin>,
    pub login: Arc<dyn Login>,
    pub authenticate: Arc<dyn Authenticate>,
    pub current_user: Arc<dyn GetCurrentUser>,
    pub create_album: Arc<dyn CreateAlbum>,
    pub list_albums: Arc<dyn ListAlbums>,
    pub upload_file: Arc<dyn UploadFile>,
    pub list_files: Arc<dyn ListFiles>,
    pub get_file: Arc<dyn GetFile>,
    pub assign_file_album: Arc<dyn AssignFileAlbum>,
    pub get_file_content: Arc<dyn GetFileContent>,
    pub register_device: Arc<dyn RegisterDevice>,
    pub list_devices: Arc<dyn ListDevices>,
    pub upsert_sync_profile: Arc<dyn UpsertSyncProfile>,
    pub get_sync_profile: Arc<dyn GetSyncProfile>,
    pub build_sync_manifest: Arc<dyn BuildSyncManifest>,
    pub publish_app_release: Arc<dyn PublishAppRelease>,
    pub list_app_releases: Arc<dyn ListAppReleases>,
    pub get_latest_app_release: Arc<dyn GetLatestAppRelease>,
    pub get_app_release: Arc<dyn GetAppRelease>,
    pub get_app_release_apk: Arc<dyn GetAppReleaseApk>,
    pub inspect_apk: Arc<dyn InspectApk>,
    pub update_app_release: Arc<dyn UpdateAppRelease>,
    pub delete_app_release: Arc<dyn DeleteAppRelease>,
}

impl Services {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self {
            check_setup: Arc::new(CheckSetupService::new(deps.clone())),
            setup_admin: Arc::new(SetupAdminService::new(deps.clone())),
            login: Arc::new(LoginService::new(deps.clone())),
            authenticate: Arc::new(AuthenticateService::new(deps.clone())),
            current_user: Arc::new(GetCurrentUserService::new(deps.clone())),
            create_album: Arc::new(CreateAlbumService::new(deps.clone())),
            list_albums: Arc::new(ListAlbumsService::new(deps.clone())),
            upload_file: Arc::new(UploadFileService::new(deps.clone())),
            list_files: Arc::new(ListFilesService::new(deps.clone())),
            get_file: Arc::new(GetFileService::new(deps.clone())),
            assign_file_album: Arc::new(AssignFileAlbumService::new(deps.clone())),
            get_file_content: Arc::new(GetFileContentService::new(deps.clone())),
            register_device: Arc::new(RegisterDeviceService::new(deps.clone())),
            list_devices: Arc::new(ListDevicesService::new(deps.clone())),
            upsert_sync_profile: Arc::new(UpsertSyncProfileService::new(deps.clone())),
            get_sync_profile: Arc::new(GetSyncProfileService::new(deps.clone())),
            build_sync_manifest: Arc::new(BuildSyncManifestService::new(deps.clone())),
            publish_app_release: Arc::new(PublishAppReleaseService::new(deps.clone())),
            list_app_releases: Arc::new(ListAppReleasesService::new(deps.clone())),
            get_latest_app_release: Arc::new(GetLatestAppReleaseService::new(deps.clone())),
            get_app_release: Arc::new(GetAppReleaseService::new(deps.clone())),
            get_app_release_apk: Arc::new(GetAppReleaseApkService::new(deps.clone())),
            inspect_apk: Arc::new(InspectApkService::new(deps.clone())),
            update_app_release: Arc::new(UpdateAppReleaseService::new(deps.clone())),
            delete_app_release: Arc::new(DeleteAppReleaseService::new(deps)),
        }
    }
}

