import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MusicSearchResult } from '../domain/music.models';
import { MediaFile } from '../domain/models';
import { MUSIC_CATALOG, MusicCatalog } from '../domain/ports';

@Injectable()
export class HttpMusicCatalog implements MusicCatalog {
  constructor(private readonly http: HttpClient) {}

  search(keyword: string): Promise<MusicSearchResult> {
    return firstValueFrom(this.http.post<MusicSearchResult>('/v1/music/search', { keyword }));
  }

  searchSnapshot(searchId: string): Promise<MusicSearchResult> {
    return firstValueFrom(this.http.get<MusicSearchResult>(`/v1/music/search/${encodeURIComponent(searchId)}`));
  }

  cancel(searchId: string): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`/v1/music/search/${encodeURIComponent(searchId)}/cancel`, {}),
    );
  }

  parsePlaylist(url: string): Promise<MusicSearchResult> {
    return firstValueFrom(this.http.post<MusicSearchResult>('/v1/music/playlist', { url }));
  }

  importTrack(searchId: string, trackId: string): Promise<MediaFile> {
    return firstValueFrom(
      this.http.post<MediaFile>('/v1/music/import', { search_id: searchId, track_id: trackId }),
    );
  }
}

export const MUSIC_DATA_PROVIDERS = [{ provide: MUSIC_CATALOG, useClass: HttpMusicCatalog }];
