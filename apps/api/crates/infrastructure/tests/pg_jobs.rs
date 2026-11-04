//! Runs the Postgres job queue against a real database. Skipped unless TEST_DATABASE_URL is set,
//! e.g. TEST_DATABASE_URL=postgres://mydrive:mydrive@127.0.0.1:5432/mydrive_test
use chrono::{Duration, Utc};
use domain::ports::JobQueue;
use domain::{JobKind, JobState};
use infrastructure::postgres::PgRepos;
use uuid::Uuid;

async fn repos() -> Option<PgRepos> {
    let url = std::env::var("TEST_DATABASE_URL").ok()?;
    let repos = PgRepos::connect(&url).await.unwrap();
    repos.migrate().await.unwrap();
    Some(repos)
}

// Both checks share one database and drain each other's jobs, so they run one after the other.
#[tokio::test]
async fn postgres_job_queue() {
    let Some(queue) = repos().await else {
        eprintln!("TEST_DATABASE_URL not set; skipping");
        return;
    };
    follows_the_queue_rules(&queue).await;
    never_claims_the_same_job_twice(queue).await;
}

async fn follows_the_queue_rules(queue: &PgRepos) {
    let now = Utc::now();
    let file = Some(Uuid::new_v4());

    // One active job per (kind, subject), including jobs without a subject.
    assert!(queue.enqueue(JobKind::MobileAudio, file, now).await.unwrap());
    assert!(!queue.enqueue(JobKind::MobileAudio, file, now).await.unwrap());
    assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Queued));

    // Drain whatever other runs left, then claim ours.
    let mut claimed = None;
    while let Some(job) = queue.claim(now).await.unwrap() {
        if job.subject == file {
            claimed = Some(job);
            break;
        }
        queue.complete(job.id).await.unwrap();
    }
    let job = claimed.expect("our job is due");
    assert_eq!((job.kind, job.attempts), (JobKind::MobileAudio, 1));
    assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Running));
    assert!(!queue.enqueue(JobKind::MobileAudio, file, now).await.unwrap(), "running counts as active");

    // A failure with a retry time goes back to the queue and is not due before then.
    queue.fail(job.id, "boom", Some(now + Duration::minutes(5))).await.unwrap();
    assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Queued));
    let early = queue.claim(now + Duration::minutes(1)).await.unwrap();
    assert!(early.is_none_or(|other| other.subject != file));
    let again = loop {
        let job = queue.claim(now + Duration::minutes(6)).await.unwrap().expect("due again");
        if job.subject == file {
            break job;
        }
        queue.complete(job.id).await.unwrap();
    };
    assert_eq!(again.id, job.id);
    assert_eq!(again.attempts, 2);

    // Giving up leaves a failed row that does not block a new job.
    queue.fail(again.id, "boom", None).await.unwrap();
    assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), Some(JobState::Failed));
    assert!(queue.enqueue(JobKind::MobileAudio, file, now).await.unwrap());
    let fresh = loop {
        let job = queue.claim(now + Duration::minutes(7)).await.unwrap().expect("queued");
        if job.subject == file {
            break job;
        }
        queue.complete(job.id).await.unwrap();
    };

    // A worker that never reports back loses the job after the lease.
    let stolen = loop {
        let job = queue
            .claim(now + Duration::minutes(7) + domain::JOB_LEASE + Duration::minutes(1))
            .await
            .unwrap()
            .expect("lease expired");
        if job.subject == file {
            break job;
        }
        queue.complete(job.id).await.unwrap();
    };
    assert_eq!(stolen.id, fresh.id);
    queue.complete(stolen.id).await.unwrap();
    assert_eq!(queue.state(JobKind::MobileAudio, file).await.unwrap(), None);
}

async fn never_claims_the_same_job_twice(queue: PgRepos) {
    let now = Utc::now();
    let subjects: Vec<Uuid> = (0..6).map(|_| Uuid::new_v4()).collect();
    for subject in &subjects {
        queue.enqueue(JobKind::MobileAudio, Some(*subject), now).await.unwrap();
    }
    let queue = std::sync::Arc::new(queue);
    let taken = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
    let wanted = subjects.clone();
    let mut tasks = Vec::new();
    for _ in 0..4 {
        let queue = queue.clone();
        let taken = taken.clone();
        let wanted = wanted.clone();
        tasks.push(tokio::spawn(async move {
            let mut mine = Vec::new();
            // A worker that finds nothing looks again, as the real one does: rows another worker
            // is claiming at that instant are skipped, not waited for.
            while taken.load(std::sync::atomic::Ordering::SeqCst) < wanted.len() {
                match queue.claim(now).await.unwrap() {
                    Some(job) => {
                        if job.subject.is_some_and(|s| wanted.contains(&s)) {
                            taken.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                        }
                        mine.push(job);
                    }
                    None => tokio::time::sleep(std::time::Duration::from_millis(5)).await,
                }
            }
            mine
        }));
    }
    let mut claimed = Vec::new();
    for task in tasks {
        claimed.extend(task.await.unwrap());
    }
    let ours: Vec<_> = claimed.iter().filter(|job| job.subject.is_some_and(|s| subjects.contains(&s))).collect();
    let mut ids: Vec<_> = ours.iter().map(|job| job.id).collect();
    ids.sort();
    ids.dedup();
    assert_eq!(ours.len(), subjects.len(), "every job claimed");
    assert_eq!(ids.len(), ours.len(), "none claimed twice");
    for job in claimed {
        queue.complete(job.id).await.unwrap();
    }
}
