pub mod apk;
pub mod clock;
pub mod config;
pub mod ffmpeg;
pub mod hasher;
pub mod jwt;
pub mod mdns;
pub mod memory;
pub mod musicdl;
pub mod postgres;
pub mod s3;
pub mod thumbnail;

use std::sync::Arc;

use application::{Deps, FileTranscodeLocks};
use crate::clock::SystemClock;
use crate::config::Settings;
use crate::ffmpeg::{FakeAudioTranscoder, FfmpegAudioTranscoder};
use crate::hasher::Argon2Hasher;
use crate::jwt::JwtTokenService;
use crate::memory::MemoryStore;
use crate::postgres::PgRepos;
use crate::s3::S3Store;
use crate::thumbnail::ImageThumbnailer;
use crate::apk::ZipApkInspector;
use crate::musicdl::HttpMusicDownloader;

pub async fn build_deps(settings: &Settings) -> Result<Arc<Deps>, domain::DomainError> {
    let hasher = Arc::new(Argon2Hasher::new());
    let tokens = Arc::new(JwtTokenService::new(&settings.jwt_secret));
    let clock = Arc::new(SystemClock);
    let thumbnailer = Arc::new(ImageThumbnailer);
    let apk_inspector = Arc::new(ZipApkInspector);
    let music = Arc::new(HttpMusicDownloader::new(&settings.musicdl_url)?);
    let transcode_locks = Arc::new(FileTranscodeLocks::new());

    if settings.memory_backend {
        let store = MemoryStore::new();
        return Ok(Arc::new(Deps {
            users: store.clone(),
            albums: store.clone(),
            files: store.clone(),
            devices: store.clone(),
            exclusions: store.clone(),
            shares: store.clone(),
            profiles: store.clone(),
            app_releases: store.clone(),
            objects: store,
            hasher,
            tokens,
            clock,
            thumbnailer,
            apk_inspector,
            music,
            audio_transcoder: Arc::new(FakeAudioTranscoder),
            transcode_locks,
        }));
    }

    let repos = PgRepos::connect(&settings.database_url).await?;
    repos.migrate().await?;
    let objects = S3Store::connect(settings).await?;
    Ok(Arc::new(Deps {
        users: Arc::new(repos.clone()),
        albums: Arc::new(repos.clone()),
        files: Arc::new(repos.clone()),
        devices: Arc::new(repos.clone()),
        exclusions: Arc::new(repos.clone()),
        shares: Arc::new(repos.clone()),
        profiles: Arc::new(repos.clone()),
        app_releases: Arc::new(repos),
        objects: Arc::new(objects),
        hasher,
        tokens,
        clock,
        thumbnailer,
        apk_inspector,
        music,
        audio_transcoder: Arc::new(FfmpegAudioTranscoder::new()),
        transcode_locks,
    }))
}
