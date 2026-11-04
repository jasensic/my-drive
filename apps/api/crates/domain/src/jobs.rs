use std::sync::Mutex;

use async_trait::async_trait;
use chrono::{DateTime, Duration, Utc};
use uuid::Uuid;

use crate::ports::JobQueue;
use crate::DomainError;

/// A job claimed by a worker that stops answering is handed to another one after this long.
pub const JOB_LEASE: Duration = Duration::minutes(15);

/// What a background job does. Stored by name, so never rename a variant's `as_str`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum JobKind {
    /// Make the AAC copy of a lossless audio file for phones. Subject: the file id.
    MobileAudio,
    /// Delete files that have been in the trash longer than the retention period.
    PurgeTrash,
}

impl JobKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::MobileAudio => "mobile_audio",
            Self::PurgeTrash => "purge_trash",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "mobile_audio" => Some(Self::MobileAudio),
            "purge_trash" => Some(Self::PurgeTrash),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JobState {
    Queued,
    Running,
    /// Out of attempts. Kept so a caller can tell "never tried" from "gave up".
    Failed,
}

impl JobState {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Queued => "queued",
            Self::Running => "running",
            Self::Failed => "failed",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "queued" => Some(Self::Queued),
            "running" => Some(Self::Running),
            "failed" => Some(Self::Failed),
            _ => None,
        }
    }

    pub fn is_active(self) -> bool {
        matches!(self, Self::Queued | Self::Running)
    }
}

/// A claimed job. `attempts` counts this run.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Job {
    pub id: Uuid,
    pub kind: JobKind,
    pub subject: Option<Uuid>,
    pub attempts: i32,
}

struct Row {
    job: Job,
    state: JobState,
    run_at: DateTime<Utc>,
    locked_at: Option<DateTime<Utc>>,
    last_error: Option<String>,
}

/// A queue that lives in memory, for `MEMORY_BACKEND` and tests. It follows the same rules as the
/// Postgres table: one active job per (kind, subject), claimed one at a time, leases that expire.
#[derive(Default)]
pub struct InMemoryJobQueue {
    rows: Mutex<Vec<Row>>,
}

impl InMemoryJobQueue {
    pub fn new() -> Self {
        Self::default()
    }

    /// The last error a job recorded, for tests.
    pub fn last_error(&self, kind: JobKind, subject: Option<Uuid>) -> Option<String> {
        let rows = self.rows.lock().unwrap();
        rows.iter()
            .filter(|row| row.job.kind == kind && row.job.subject == subject)
            .find_map(|row| row.last_error.clone())
    }
}

#[async_trait]
impl JobQueue for InMemoryJobQueue {
    async fn enqueue(
        &self,
        kind: JobKind,
        subject: Option<Uuid>,
        run_at: DateTime<Utc>,
    ) -> Result<bool, DomainError> {
        let mut rows = self.rows.lock().unwrap();
        // A new attempt wipes the memory of an earlier give-up.
        rows.retain(|row| !(row.job.kind == kind && row.job.subject == subject && row.state == JobState::Failed));
        if rows
            .iter()
            .any(|row| row.job.kind == kind && row.job.subject == subject && row.state.is_active())
        {
            return Ok(false);
        }
        rows.push(Row {
            job: Job {
                id: Uuid::new_v4(),
                kind,
                subject,
                attempts: 0,
            },
            state: JobState::Queued,
            run_at,
            locked_at: None,
            last_error: None,
        });
        Ok(true)
    }

    async fn claim(&self, now: DateTime<Utc>) -> Result<Option<Job>, DomainError> {
        let mut rows = self.rows.lock().unwrap();
        let next = rows
            .iter_mut()
            .filter(|row| match row.state {
                JobState::Queued => row.run_at <= now,
                JobState::Running => row.locked_at.is_some_and(|at| at + JOB_LEASE < now),
                JobState::Failed => false,
            })
            .min_by_key(|row| row.run_at);
        let Some(row) = next else { return Ok(None) };
        row.state = JobState::Running;
        row.locked_at = Some(now);
        row.job.attempts += 1;
        Ok(Some(row.job.clone()))
    }

    async fn complete(&self, id: Uuid) -> Result<(), DomainError> {
        self.rows.lock().unwrap().retain(|row| row.job.id != id);
        Ok(())
    }

    async fn fail(
        &self,
        id: Uuid,
        error: &str,
        retry_at: Option<DateTime<Utc>>,
    ) -> Result<(), DomainError> {
        let mut rows = self.rows.lock().unwrap();
        if let Some(row) = rows.iter_mut().find(|row| row.job.id == id) {
            row.last_error = Some(error.to_string());
            row.locked_at = None;
            match retry_at {
                Some(at) => {
                    row.state = JobState::Queued;
                    row.run_at = at;
                }
                None => row.state = JobState::Failed,
            }
        }
        Ok(())
    }

    async fn state(
        &self,
        kind: JobKind,
        subject: Option<Uuid>,
    ) -> Result<Option<JobState>, DomainError> {
        let rows = self.rows.lock().unwrap();
        let mut found = None;
        for row in rows.iter().filter(|row| row.job.kind == kind && row.job.subject == subject) {
            if row.state.is_active() {
                return Ok(Some(row.state));
            }
            found = Some(row.state);
        }
        Ok(found)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(minutes: i64) -> DateTime<Utc> {
        DateTime::<Utc>::from_timestamp(1_800_000_000, 0).unwrap() + Duration::minutes(minutes)
    }

    #[tokio::test]
    async fn one_active_job_per_kind_and_subject() {
        let queue = InMemoryJobQueue::new();
        let file = Some(Uuid::new_v4());
        assert!(queue.enqueue(JobKind::MobileAudio, file, at(0)).await.unwrap());
        assert!(!queue.enqueue(JobKind::MobileAudio, file, at(0)).await.unwrap());
        assert!(queue.enqueue(JobKind::MobileAudio, Some(Uuid::new_v4()), at(0)).await.unwrap());
        assert!(queue.enqueue(JobKind::PurgeTrash, None, at(0)).await.unwrap());
        assert!(!queue.enqueue(JobKind::PurgeTrash, None, at(5)).await.unwrap());
    }

    #[tokio::test]
    async fn claims_due_jobs_once_oldest_first() {
        let queue = InMemoryJobQueue::new();
        queue.enqueue(JobKind::PurgeTrash, None, at(5)).await.unwrap();
        let file = Uuid::new_v4();
        queue.enqueue(JobKind::MobileAudio, Some(file), at(1)).await.unwrap();

        assert!(queue.claim(at(0)).await.unwrap().is_none(), "nothing is due yet");
        let first = queue.claim(at(10)).await.unwrap().unwrap();
        assert_eq!((first.kind, first.subject, first.attempts), (JobKind::MobileAudio, Some(file), 1));
        let second = queue.claim(at(10)).await.unwrap().unwrap();
        assert_eq!(second.kind, JobKind::PurgeTrash);
        assert!(queue.claim(at(10)).await.unwrap().is_none(), "both are running");
    }

    #[tokio::test]
    async fn a_lost_worker_loses_its_job_after_the_lease() {
        let queue = InMemoryJobQueue::new();
        queue.enqueue(JobKind::PurgeTrash, None, at(0)).await.unwrap();
        let first = queue.claim(at(0)).await.unwrap().unwrap();
        assert!(queue.claim(at(10)).await.unwrap().is_none());
        let again = queue.claim(at(16)).await.unwrap().unwrap();
        assert_eq!(again.id, first.id);
        assert_eq!(again.attempts, 2);
    }

    #[tokio::test]
    async fn failures_retry_later_then_stay_failed() {
        let queue = InMemoryJobQueue::new();
        let file = Some(Uuid::new_v4());
        queue.enqueue(JobKind::MobileAudio, file, at(0)).await.unwrap();

        let job = queue.claim(at(0)).await.unwrap().unwrap();
        queue.fail(job.id, "ffmpeg exited 1", Some(at(2))).await.unwrap();
        assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Queued));
        assert!(queue.claim(at(1)).await.unwrap().is_none(), "backing off");

        let job = queue.claim(at(2)).await.unwrap().unwrap();
        queue.fail(job.id, "ffmpeg exited 1", None).await.unwrap();
        assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Failed));
        assert!(queue.claim(at(60)).await.unwrap().is_none());
        assert_eq!(queue.last_error(JobKind::MobileAudio, file).as_deref(), Some("ffmpeg exited 1"));
        // A failed job does not block a new one, and queuing again clears the failure.
        assert!(queue.enqueue(JobKind::MobileAudio, file, at(61)).await.unwrap());
        assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Queued));
    }

    #[tokio::test]
    async fn completed_jobs_disappear() {
        let queue = InMemoryJobQueue::new();
        let file = Some(Uuid::new_v4());
        queue.enqueue(JobKind::MobileAudio, file, at(0)).await.unwrap();
        let job = queue.claim(at(0)).await.unwrap().unwrap();
        assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Running));
        queue.complete(job.id).await.unwrap();
        assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), None);
    }
}
