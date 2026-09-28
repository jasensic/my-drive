import { Inject, Injectable } from '@angular/core';
import { MusicSearchResult } from '../domain/music.models';
import { MediaFile } from '../domain/models';
import { MUSIC_CATALOG, MusicCatalog } from '../domain/ports';
import { ImportMusicTrack, ParseSpotifyPlaylist, SearchMusic } from './use-cases.tokens';

@Injectable()
export class SearchMusicService implements SearchMusic {
  constructor(@Inject(MUSIC_CATALOG) private readonly catalog: MusicCatalog) {}

  start(keyword: string): Promise<MusicSearchResult> {
    return this.catalog.search(keyword.trim());
  }

  snapshot(searchId: string): Promise<MusicSearchResult> {
    return this.catalog.searchSnapshot(searchId);
  }

  cancel(searchId: string): Promise<void> {
    return this.catalog.cancel(searchId);
  }
}

@Injectable()
export class ParseSpotifyPlaylistService implements ParseSpotifyPlaylist {
  constructor(@Inject(MUSIC_CATALOG) private readonly catalog: MusicCatalog) {}

  start(url: string): Promise<MusicSearchResult> {
    const trimmed = url.trim();
    if (!trimmed) {
      return Promise.reject(new Error('A Spotify playlist link is required'));
    }
    return this.catalog.parsePlaylist(trimmed);
  }
}

@Injectable()
export class ImportMusicTrackService implements ImportMusicTrack {
  constructor(@Inject(MUSIC_CATALOG) private readonly catalog: MusicCatalog) {}

  execute(searchId: string, trackId: string): Promise<MediaFile> {
    return this.catalog.importTrack(searchId, trackId);
  }
}
