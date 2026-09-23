use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use domain::FileId;
use tokio::sync::{Mutex as AsyncMutex, OwnedMutexGuard};

#[derive(Default)]
pub struct FileTranscodeLocks {
    inner: Mutex<HashMap<FileId, Arc<AsyncMutex<()>>>>,
}

impl FileTranscodeLocks {
    pub fn new() -> Self {
        Self::default()
    }

    pub async fn lock(&self, id: FileId) -> OwnedMutexGuard<()> {
        let mutex = {
            let mut map = self.inner.lock().expect("transcode lock map");
            map.entry(id)
                .or_insert_with(|| Arc::new(AsyncMutex::new(())))
                .clone()
        };
        mutex.lock_owned().await
    }
}
