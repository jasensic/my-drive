import { Component, Inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { musicSourceLabel } from '../application/music.mapping';
import { IMPORT_MUSIC_TRACK, SEARCH_MUSIC } from '../application/use-cases.tokens';
import type { ImportMusicTrack, SearchMusic } from '../application/use-cases.tokens';
import { MusicTrack } from '../domain/music.models';
import { extractError } from './login.page';

@Component({
  selector: 'app-music-search',
  imports: [FormsModule, Button, Card, InputText],
  template: `
    <p-card header="Search songs">
      <p class="hint">
        Searches Migu, NetEase, QQ, Kuwo, and Qianqian, keeps the sources that answer within a few
        seconds, then saves the audio file into this library.
      </p>
      <form class="search" (ngSubmit)="search()">
        <input
          pInputText
          name="keyword"
          placeholder="Artist or song"
          [(ngModel)]="keyword"
          [disabled]="searching()"
        />
        <p-button
          type="submit"
          label="Search"
          icon="pi pi-search"
          [loading]="searching()"
          [disabled]="!keyword.trim()"
        />
      </form>

      @if (error()) {
        <p class="error">{{ error() }}</p>
      }

      @if (searched() && !tracks().length && !searching()) {
        <p class="empty">No songs found.</p>
      }

      @if (tracks().length) {
        <ul class="results">
          @for (track of tracks(); track track.id) {
            <li>
              <div>
                <strong>{{ track.song_name || 'Untitled' }}</strong>
                <span>{{ track.singers || 'Unknown artist' }}</span>
                <span class="meta">
                  {{ musicSourceLabel(track.source) }}
                  @if (track.album) {
                    · {{ track.album }}
                  }
                  @if (track.duration) {
                    · {{ track.duration }}
                  }
                  @if (track.file_size) {
                    · {{ track.file_size }}
                  }
                  @if (track.ext) {
                    · {{ track.ext }}
                  }
                </span>
              </div>
              <p-button
                label="Download"
                icon="pi pi-download"
                [outlined]="true"
                [loading]="importingId() === track.id"
                [disabled]="importingId() !== null && importingId() !== track.id"
                (onClick)="download(track)"
              />
            </li>
          }
        </ul>
      }
    </p-card>
  `,
  styles: `
    .hint, .empty, .error { margin: 0 0 0.75rem; color: var(--p-text-muted-color); }
    .error { color: var(--p-red-500); }
    .search { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
    .search input { min-width: 16rem; flex: 1; }
    .results { list-style: none; margin: 1rem 0 0; padding: 0; display: flex; flex-direction: column; gap: 0.75rem; }
    .results li { display: flex; justify-content: space-between; gap: 1rem; align-items: center; }
    .results strong, .results span { display: block; }
    .meta { color: var(--p-text-muted-color); font-size: 0.85rem; }
  `,
})
export class MusicSearchPanel {
  imported = output<string>();
  keyword = '';
  tracks = signal<MusicTrack[]>([]);
  searchId = signal<string | null>(null);
  searching = signal(false);
  searched = signal(false);
  importingId = signal<string | null>(null);
  error = signal<string | null>(null);
  readonly musicSourceLabel = musicSourceLabel;

  constructor(
    @Inject(SEARCH_MUSIC) private readonly searchMusic: SearchMusic,
    @Inject(IMPORT_MUSIC_TRACK) private readonly importTrack: ImportMusicTrack,
  ) {}

  async search() {
    const keyword = this.keyword.trim();
    if (!keyword || this.searching()) {
      return;
    }
    this.searching.set(true);
    this.error.set(null);
    this.tracks.set([]);
    this.searchId.set(null);
    try {
      const result = await this.searchMusic.execute(keyword);
      this.searchId.set(result.search_id);
      this.tracks.set(result.tracks);
      this.searched.set(true);
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.searching.set(false);
    }
  }

  async download(track: MusicTrack) {
    const searchId = this.searchId();
    if (!searchId || this.importingId()) {
      return;
    }
    this.importingId.set(track.id);
    this.error.set(null);
    try {
      const file = await this.importTrack.execute(searchId, track.id);
      this.imported.emit(file.name);
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.importingId.set(null);
    }
  }
}
