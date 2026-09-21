import { Inject, Injectable } from '@angular/core';
import { MusicSearchResult } from '../domain/music.models';
import { MediaFile } from '../domain/models';
import { MUSIC_CATALOG, MusicCatalog } from '../domain/ports';
import { ImportMusicTrack, SearchMusic } from './use-cases.tokens';

@Injectable()
export class SearchMusicService implements SearchMusic {
  constructor(@Inject(MUSIC_CATALOG) private readonly catalog: MusicCatalog) {}

  execute(keyword: string): Promise<MusicSearchResult> {
    return this.catalog.search(keyword.trim());
  }
}

@Injectable()
export class ImportMusicTrackService implements ImportMusicTrack {
  constructor(@Inject(MUSIC_CATALOG) private readonly catalog: MusicCatalog) {}

  execute(searchId: string, trackId: string): Promise<MediaFile> {
    return this.catalog.importTrack(searchId, trackId);
  }
}
