import { Component, Inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
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
  imports: [FormsModule, Button, Card, InputText, LucideDynamicIcon],
  template: `
    <p-card header="Search songs">
      <p class="hint">
        Searches Migu, NetEase, QQ, Kuwo, and Qianqian, keeps the sources that answer within a few
        seconds, and skips files under 1 MB. Download saves the audio into this library.
      </p>
      <form class="search-form" (ngSubmit)="search()">
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
          [loading]="searching()"
          [disabled]="!keyword.trim()"
        >
          <ng-template #icon><svg lucideIcon="search" aria-hidden="true" /></ng-template>
        </p-button>
      </form>

      @if (error()) {
        <p class="banner error">{{ error() }}</p>
      }

      @if (searched() && !tracks().length && !searching()) {
        <div class="empty-state">
          <svg lucideIcon="search" [size]="28" aria-hidden="true" />
          <p>No songs found.</p>
        </div>
      }

      @if (tracks().length) {
        <ul class="search-results">
          @for (track of tracks(); track track.id) {
            <li>
              @if (track.cover_url) {
                <img class="search-cover" [src]="track.cover_url" [alt]="track.album || track.song_name" />
              }
              <div>
                <strong>{{ track.song_name || 'Untitled' }}</strong>
                <span>{{ track.singers || 'Unknown artist' }}</span>
                <span class="caption">
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
                [outlined]="true"
                [loading]="importingId() === track.id"
                [disabled]="importingId() !== null && importingId() !== track.id"
                (onClick)="download(track)"
              >
                <ng-template #icon><svg lucideIcon="download" aria-hidden="true" /></ng-template>
              </p-button>
            </li>
          }
        </ul>
      }
    </p-card>
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
