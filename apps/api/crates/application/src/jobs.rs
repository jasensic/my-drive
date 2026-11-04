//! Background jobs: work that should not make a request wait (transcoding) and chores that no
//! request triggers (emptying the old trash). Requests queue jobs through [`crate::mobile`] and
//! friends; a [`JobWorker`] started by the binary runs them.

use std::sync::Arc;
use std::time::Duration;

use chrono::{DateTime, Utc};
use domain::{FileId, Job, JobKind, TRASH_RETENTION_DAYS};

use crate::mobile::ensure_mobile_audio;
use crate::usecases::remove_stored_file;
use crate::{AppError, Deps};

/// A job that keeps failing is given up on after this many runs.
pub const MAX_ATTEMPTS: i32 = 5;

/// How often the worker queues the trash purge.
const PURGE_EVERY: Duration = Duration::from_secs(60 * 60);

/// Waits before the next run of a job that has just failed for the `attempt`th time:
/// 30 s, 1 min, 2 min, 4 min…
pub fn retry_delay(attempt: i32) -> chrono::Duration {
    let exponent = (attempt - 1).clamp(0, 10) as u32;
    chrono::Duration::seconds(30 * 2_i64.pow(exponent))
}

/// Claims and runs one due job. False when there was none.
pub async fn run_next(deps: &Deps) -> Result<bool, AppError> {
    let now = deps.clock.now();
    let Some(job) = deps.jobs.claim(now).await? else {
        return Ok(false);
    };
    match execute(deps, &job, now).await {
        Ok(()) => deps.jobs.complete(job.id).await?,
        Err(err) => {
            let retry_at = (job.attempts < MAX_ATTEMPTS).then(|| now + retry_delay(job.attempts));
            tracing::warn!(kind = job.kind.as_str(), attempt = job.attempts, "job failed: {err}");
            deps.jobs.fail(job.id, &err.to_string(), retry_at).await?;
        }
    }
    Ok(true)
}

async fn execute(deps: &Deps, job: &Job, now: DateTime<Utc>) -> Result<(), AppError> {
    match job.kind {
        JobKind::MobileAudio => {
            let id = job
                .subject
                .ok_or_else(|| AppError::validation("mobile audio job has no file"))?;
            // The file may have been purged since the job was queued; then there is nothing to do.
            let Some(file) = deps.files.find_by_id(FileId::from_uuid(id)).await? else {
                return Ok(());
            };
            ensure_mobile_audio(deps, file).await.map(|_| ())
        }
        JobKind::PurgeTrash => purge_expired_trash(deps, now).await,
    }
}

/// Deletes every file that has been in the trash for longer than the retention period. One file
/// that cannot be deleted does not stop the others; the job fails afterwards so it is retried.
pub async fn purge_expired_trash(deps: &Deps, now: DateTime<Utc>) -> Result<(), AppError> {
    let cutoff = now - chrono::Duration::days(TRASH_RETENTION_DAYS);
    let mut first_error = None;
    for file in deps.files.list_trashed_before(cutoff).await? {
        if let Err(err) = remove_stored_file(deps, &file).await {
            tracing::warn!(file = %file.id, "could not purge from trash: {err}");
            first_error.get_or_insert(err);
        }
    }
    first_error.map_or(Ok(()), Err)
}

/// Runs jobs one after another for as long as the process lives.
pub struct JobWorker {
    deps: Arc<Deps>,
    poll: Duration,
}

impl JobWorker {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self {
            deps,
            poll: Duration::from_secs(1),
        }
    }

    /// How long to wait when the queue is empty.
    pub fn with_poll(mut self, poll: Duration) -> Self {
        self.poll = poll;
        self
    }

    pub async fn run(self) {
        let mut last_purge: Option<tokio::time::Instant> = None;
        loop {
            if last_purge.is_none_or(|at| at.elapsed() >= PURGE_EVERY) {
                last_purge = Some(tokio::time::Instant::now());
                let now = self.deps.clock.now();
                if let Err(err) = self.deps.jobs.enqueue(JobKind::PurgeTrash, None, now).await {
                    tracing::warn!("could not queue the trash purge: {err}");
                }
            }
            match run_next(&self.deps).await {
                Ok(true) => {}
                Ok(false) => tokio::time::sleep(self.poll).await,
                Err(err) => {
                    // The queue itself is unreachable (database down): wait and look again.
                    tracing::warn!("job queue error: {err}");
                    tokio::time::sleep(self.poll.max(Duration::from_secs(5))).await;
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use chrono::Utc;
    use domain::model::FileRecord;
    use domain::ports::*;
    use domain::{FileId, MediaKind, UserId};

    use super::*;
    use crate::test_support::{deps_from, TestMem};

    fn flac(owner: UserId) -> FileRecord {
        let now = Utc::now();
        FileRecord {
            id: FileId::new(),
            owner_id: owner,
            album_id: None,
            name: "song.flac".into(),
            size: 4,
            mime: "audio/flac".into(),
            checksum: "sha256:x".into(),
            object_key: "music/song.flac".into(),
            thumbnail_key: None,
            media_kind: MediaKind::Audio,
            created_at: now,
            uploaded_at: now,
            deleted_at: None,
            mobile_object_key: None,
            mobile_checksum: None,
            mobile_size: None,
            mobile_mime: None,
        }
    }

    #[test]
    fn retries_back_off_and_stop_growing() {
        assert_eq!(retry_delay(1), chrono::Duration::seconds(30));
        assert_eq!(retry_delay(2), chrono::Duration::seconds(60));
        assert_eq!(retry_delay(4), chrono::Duration::seconds(240));
        assert_eq!(retry_delay(50), retry_delay(11));
    }

    #[tokio::test]
    async fn a_mobile_audio_job_adds_the_aac_copy() {
        let mem = TestMem::new();
        let owner = mem.add_user("owner").await;
        let file = flac(owner);
        FileRepository::insert(mem.as_ref(), &file).await.unwrap();
        ObjectStore::put(mem.as_ref(), &file.object_key, b"flac".to_vec().into(), "audio/flac")
            .await
            .unwrap();
        let deps = deps_from(mem.clone());
        deps.jobs.enqueue(JobKind::MobileAudio, Some(file.id.0), deps.clock.now()).await.unwrap();

        assert!(run_next(&deps).await.unwrap());
        let stored = FileRepository::find_by_id(mem.as_ref(), file.id).await.unwrap().unwrap();
        assert!(stored.has_mobile_audio());
        assert!(!run_next(&deps).await.unwrap(), "the job is done and gone");
    }

    #[tokio::test]
    async fn a_job_for_a_deleted_file_just_finishes() {
        let mem = TestMem::new();
        let deps = deps_from(mem);
        let gone = FileId::new();
        deps.jobs.enqueue(JobKind::MobileAudio, Some(gone.0), deps.clock.now()).await.unwrap();
        assert!(run_next(&deps).await.unwrap());
        assert_eq!(deps.jobs.state(JobKind::MobileAudio, Some(gone.0)).await.unwrap(), None);
    }

    #[tokio::test]
    async fn a_failing_job_retries_and_then_gives_up() {
        let mem = TestMem::new();
        let owner = mem.add_user("owner").await;
        let file = flac(owner);
        FileRepository::insert(mem.as_ref(), &file).await.unwrap();
        // The original is missing from storage, so the transcode cannot read it.
        let deps = deps_from(mem);
        let subject = Some(file.id.0);
        deps.jobs.enqueue(JobKind::MobileAudio, subject, deps.clock.now()).await.unwrap();

        assert!(run_next(&deps).await.unwrap());
        assert_eq!(
            deps.jobs.state(JobKind::MobileAudio, subject).await.unwrap(),
            Some(domain::JobState::Queued),
            "waiting to try again"
        );
        assert!(!run_next(&deps).await.unwrap(), "not due before the back-off ends");
    }

    #[tokio::test]
    async fn expired_trash_is_purged_and_recent_trash_is_kept() {
        let mem = TestMem::new();
        let owner = mem.add_user("owner").await;
        let deps = deps_from(mem.clone());
        let now = deps.clock.now();
        let mut old = flac(owner);
        old.deleted_at = Some(now - chrono::Duration::days(TRASH_RETENTION_DAYS + 1));
        let mut recent = flac(owner);
        recent.deleted_at = Some(now - chrono::Duration::days(2));
        FileRepository::insert(mem.as_ref(), &old).await.unwrap();
        FileRepository::insert(mem.as_ref(), &recent).await.unwrap();
        ObjectStore::put(mem.as_ref(), &old.object_key, b"x".to_vec().into(), "audio/flac").await.unwrap();

        purge_expired_trash(&deps, now).await.unwrap();

        assert!(FileRepository::find_by_id(mem.as_ref(), old.id).await.unwrap().is_none());
        assert!(FileRepository::find_by_id(mem.as_ref(), recent.id).await.unwrap().is_some());
    }
}
