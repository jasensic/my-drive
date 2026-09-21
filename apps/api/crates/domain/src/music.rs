use bytes::Bytes;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MusicTrack {
    pub id: String,
    pub source: String,
    pub song_name: String,
    pub singers: String,
    pub album: String,
    pub duration: String,
    pub file_size: String,
    pub ext: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MusicSearch {
    pub search_id: String,
    pub tracks: Vec<MusicTrack>,
}

#[derive(Debug, Clone)]
pub struct DownloadedAudio {
    pub name: String,
    pub mime: String,
    pub bytes: Bytes,
}
