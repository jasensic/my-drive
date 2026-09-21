export interface MusicTrack {
  id: string;
  source: string;
  song_name: string;
  singers: string;
  album: string;
  duration: string;
  file_size: string;
  ext: string;
  cover_url?: string;
}

export interface MusicSearchResult {
  search_id: string;
  tracks: MusicTrack[];
}
