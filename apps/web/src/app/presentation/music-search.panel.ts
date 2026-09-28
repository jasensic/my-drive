import { NgTemplateOutlet } from '@angular/common';
import { Component, Inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { musicSourceLabel } from '../application/music.mapping';
import {
  IMPORT_MUSIC_TRACK,
  PARSE_SPOTIFY_PLAYLIST,
  SEARCH_MUSIC,
} from '../application/use-cases.tokens';
import type { ImportMusicTrack, ParseSpotifyPlaylist, SearchMusic } from '../application/use-cases.tokens';
import { MusicSearchResult, MusicTrack } from '../domain/music.models';
import { extractError } from './login.page';

@Component({
  selector: 'app-music-search',
  imports: [FormsModule, NgTemplateOutlet, Button, Card, InputText, LucideDynamicIcon],
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--md-space-4);
    }
  `,
  template: `
    <p-card header="Search songs">
      <p class="hint">
        Searches Migu, NetEase, QQ, Kuwo, and Qianqian. Results appear as each source answers;
        files under 1 MB are skipped. Download saves the audio into this library.
      </p>
      <form class="search-form" (ngSubmit)="search()">
        <input
          pInputText
          name="keyword"
          placeholder="Artist or song"
          [(ngModel)]="keyword"
          [disabled]="busy()"
        />
        <p-button
          type="submit"
          label="Search"
          [loading]="searching() && mode() === 'search'"
          [disabled]="busy() || !keyword.trim()"
        >
          <ng-template #icon><svg lucideIcon="search" aria-hidden="true" /></ng-template>
        </p-button>
      </form>

      @if (mode() === 'search') {
        <ng-container [ngTemplateOutlet]="results" />
      }
    </p-card>

    <p-card header="Spotify playlist">
      <p class="hint">
        Paste an open.spotify.com playlist link. musicdl reads the tracks, then Download all saves
        them into this library.
      </p>
      <form class="search-form" (ngSubmit)="loadPlaylist()">
        <input
          pInputText
          name="playlistUrl"
          placeholder="Spotify playlist URL"
          [(ngModel)]="playlistUrl"
          [disabled]="busy()"
        />
        <p-button
          type="submit"
          label="Load playlist"
          [loading]="searching() && mode() === 'playlist'"
          [disabled]="busy() || !playlistUrl.trim()"
        >
          <ng-template #icon><svg lucideIcon="list-music" aria-hidden="true" /></ng-template>
        </p-button>
      </form>
      @if (mode() === 'playlist' && tracks().length && !searching()) {
        <p-button
          type="button"
          label="Download all"
          [loading]="importingAll()"
          [disabled]="importingId() !== null"
          (onClick)="downloadAll()"
        >
          <ng-template #icon><svg lucideIcon="download" aria-hidden="true" /></ng-template>
        </p-button>
      }

      @if (mode() === 'playlist' && importingAll()) {
        <p class="caption">Saving {{ saveDone() }} of {{ saveTotal() }}</p>
      }

      @if (mode() === 'playlist') {
        <ng-container [ngTemplateOutlet]="results" />
      }
    </p-card>

    <ng-template #results>
      @if (error()) {
        <p class="banner error">{{ error() }}</p>
      }

      @if (searched() && !tracks().length && !searching() && !error()) {
        <div class="empty-state">
          <svg lucideIcon="search" [size]="28" aria-hidden="true" />
          <p>{{ mode() === 'playlist' ? 'No downloadable tracks in this playlist.' : 'No songs found.' }}</p>
        </div>
      }

      @if (searching() && !tracks().length) {
        <p class="caption">
          {{ mode() === 'playlist' ? 'Reading the Spotify playlist…' : 'Looking up sources…' }}
        </p>
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
    </ng-template>
  `,
})
export class MusicSearchPanel {
  imported = output<string>();
  keyword = '';
  playlistUrl = '';
  tracks = signal<MusicTrack[]>([]);
  searchId = signal<string | null>(null);
  searching = signal(false);
  searched = signal(false);
  mode = signal<'search' | 'playlist' | null>(null);
  importingId = signal<string | null>(null);
  importingAll = signal(false);
  saveDone = signal(0);
  saveTotal = signal(0);
  error = signal<string | null>(null);
  readonly musicSourceLabel = musicSourceLabel;

  private searchSeq = 0;

  constructor(
    @Inject(SEARCH_MUSIC) private readonly searchMusic: SearchMusic,
    @Inject(PARSE_SPOTIFY_PLAYLIST) private readonly parsePlaylist: ParseSpotifyPlaylist,
    @Inject(IMPORT_MUSIC_TRACK) private readonly importTrack: ImportMusicTrack,
  ) {}

  busy(): boolean {
    return this.searching() || this.importingId() !== null;
  }

  async search() {
    const keyword = this.keyword.trim();
    if (!keyword || this.busy()) {
      return;
    }
    const seq = ++this.searchSeq;
    this.begin('search');
    try {
      const started = await this.searchMusic.start(keyword);
      await this.follow(seq, started);
    } catch (err) {
      if (seq === this.searchSeq) {
        this.error.set(extractError(err));
      }
    } finally {
      if (seq === this.searchSeq) {
        this.searching.set(false);
      }
    }
  }

  async loadPlaylist() {
    const url = this.playlistUrl.trim();
    if (!url || this.busy()) {
      return;
    }
    const seq = ++this.searchSeq;
    this.begin('playlist');
    try {
      const started = await this.parsePlaylist.start(url);
      await this.follow(seq, started);
    } catch (err) {
      if (seq === this.searchSeq) {
        this.error.set(extractError(err));
      }
    } finally {
      if (seq === this.searchSeq) {
        this.searching.set(false);
      }
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

  async downloadAll() {
    const searchId = this.searchId();
    const tracks = this.tracks();
    if (!searchId || !tracks.length || this.importingId() || this.searching()) {
      return;
    }
    this.importingAll.set(true);
    this.saveTotal.set(tracks.length);
    this.saveDone.set(0);
    this.error.set(null);
    const failed: string[] = [];
    let saved = 0;
    let lastName = '';
    try {
      for (const track of tracks) {
        this.importingId.set(track.id);
        try {
          const file = await this.importTrack.execute(searchId, track.id);
          saved += 1;
          lastName = file.name;
        } catch {
          failed.push(track.song_name || track.id);
        }
        this.saveDone.update((count) => count + 1);
      }
    } finally {
      this.importingId.set(null);
      this.importingAll.set(false);
    }
    if (failed.length) {
      const names = failed.slice(0, 5).join(', ');
      const extra = failed.length > 5 ? ` and ${failed.length - 5} more` : '';
      this.error.set(`Saved ${saved} of ${tracks.length}. Failed: ${names}${extra}`);
    }
    if (saved === 1) {
      this.imported.emit(lastName);
    } else if (saved > 1) {
      this.imported.emit(`${saved} tracks`);
    }
  }

  private begin(mode: 'search' | 'playlist') {
    this.mode.set(mode);
    this.searching.set(true);
    this.error.set(null);
    this.tracks.set([]);
    this.searchId.set(null);
    this.searched.set(true);
    this.saveDone.set(0);
    this.saveTotal.set(0);
  }

  private async follow(seq: number, started: MusicSearchResult) {
    if (seq !== this.searchSeq) {
      return;
    }
    this.searchId.set(started.search_id);
    this.tracks.set(started.tracks);
    if (started.error) {
      this.error.set(started.error);
    }
    let done = started.done;
    while (!done) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      if (seq !== this.searchSeq) {
        return;
      }
      const snap = await this.searchMusic.snapshot(started.search_id);
      if (seq !== this.searchSeq) {
        return;
      }
      this.tracks.set(snap.tracks);
      if (snap.error) {
        this.error.set(snap.error);
      }
      done = snap.done;
    }
  }
}
