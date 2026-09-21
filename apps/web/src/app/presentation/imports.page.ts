import { Component, Inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Tabs, TabList, Tab, TabPanels, TabPanel } from 'primeng/tabs';
import { Toolbar } from 'primeng/toolbar';
import {
  formatTrackDuration,
  spotifyEmbedUrl,
  summarizeImportBatch,
} from '../application/cloud-import.mapping';
import {
  CONNECT_SPOTIFY,
  DISCONNECT_SPOTIFY,
  GET_CLOUD_IMPORT_AVAILABILITY,
  GET_SPOTIFY_STATUS,
  IMPORT_GOOGLE_DRIVE,
  IMPORT_GOOGLE_PHOTOS,
  LIST_SPOTIFY_PLAYLISTS,
  LIST_SPOTIFY_PLAYLIST_TRACKS,
} from '../application/use-cases.tokens';
import type {
  ConnectSpotify,
  DisconnectSpotify,
  GetCloudImportAvailability,
  GetSpotifyStatus,
  ImportGoogleDrive,
  ImportGooglePhotos,
  ListSpotifyPlaylistTracks,
  ListSpotifyPlaylists,
} from '../application/use-cases.tokens';
import { SpotifyPlaylist, SpotifyTrack } from '../domain/cloud-import.models';
import { extractError } from './login.page';

@Component({
  selector: 'app-imports-page',
  imports: [FormsModule, Button, Card, Toolbar, Tabs, TabList, Tab, TabPanels, TabPanel],
  template: `
    <p-toolbar>
      <ng-template #start>
        <div class="heading">
          <i class="pi pi-cloud-download"></i>
          <div>
            <strong>Cloud imports</strong>
            <p>
              Bring your own Google Photos and Drive files into my-drive, or browse Spotify playlists
              with official playback. Full Spotify tracks are never downloaded.
            </p>
          </div>
        </div>
      </ng-template>
    </p-toolbar>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (ok()) {
      <p class="ok">{{ ok() }}</p>
    }

    <p-tabs value="0">
      <p-tablist>
        <p-tab value="0">Google Photos</p-tab>
        <p-tab value="1">Google Drive</p-tab>
        <p-tab value="2">Spotify</p-tab>
      </p-tablist>
      <p-tabpanels>
        <p-tabpanel value="0">
          <p-card header="Import from Google Photos">
            @if (!availability().googlePhotos) {
              <p class="hint">
                Not configured. Set <code>GOOGLE_OAUTH_CLIENT_ID</code> in <code>.env</code>, enable the
                Photos Picker API, and restart the portal.
              </p>
            } @else {
              <p class="hint">
                Opens the official Google Photos Picker. Selected photos and videos are uploaded into
                your my-drive library.
              </p>
              <p-button
                label="Pick photos"
                icon="pi pi-images"
                [loading]="busy()"
                (onClick)="importPhotos()"
              />
            }
          </p-card>
        </p-tabpanel>

        <p-tabpanel value="1">
          <p-card header="Import from Google Drive">
            @if (!availability().googleDrive) {
              <p class="hint">
                Not configured. Set <code>GOOGLE_OAUTH_CLIENT_ID</code> and <code>GOOGLE_API_KEY</code>
                (optional <code>GOOGLE_APP_ID</code>) in <code>.env</code>, enable the Google Picker and
                Drive APIs, then restart the portal.
              </p>
            } @else {
              <p class="hint">
                Opens the official Google Picker for files you own. Chosen files are uploaded into
                my-drive.
              </p>
              <p-button
                label="Pick Drive files"
                icon="pi pi-folder-open"
                [loading]="busy()"
                (onClick)="importDrive()"
              />
            }
          </p-card>
        </p-tabpanel>

        <p-tabpanel value="2">
          <p-card header="Spotify library">
            @if (!availability().spotify) {
              <p class="hint">
                Not configured. Set <code>SPOTIFY_CLIENT_ID</code> (and optionally
                <code>SPOTIFY_REDIRECT_URI</code>) in <code>.env</code>, register the redirect URI in the
                Spotify Developer Dashboard, then restart the portal.
              </p>
            } @else if (!spotifyConnected()) {
              <p class="hint">
                Connect your Spotify account to list playlists and play tracks via Spotify’s official
                embed or 30-second preview URLs. my-drive never saves full audio from Spotify.
              </p>
              <p-button label="Connect Spotify" icon="pi pi-link" (onClick)="connectSpotify()" />
            } @else {
              <div class="spotify-actions">
                <p-button
                  label="Refresh playlists"
                  icon="pi pi-refresh"
                  [text]="true"
                  (onClick)="loadPlaylists()"
                />
                <p-button
                  label="Disconnect"
                  icon="pi pi-sign-out"
                  [text]="true"
                  severity="secondary"
                  (onClick)="disconnectSpotify()"
                />
              </div>

              @if (!playlists().length) {
                <p class="empty">No playlists found.</p>
              }

              <div class="playlist-grid">
                @for (playlist of playlists(); track playlist.id) {
                  <button type="button" class="playlist" (click)="openPlaylist(playlist)">
                    @if (playlist.imageUrl) {
                      <img [src]="playlist.imageUrl" [alt]="playlist.name" />
                    } @else {
                      <i class="pi pi-list"></i>
                    }
                    <span>{{ playlist.name }}</span>
                    <small>{{ playlist.trackCount }} tracks</small>
                  </button>
                }
              </div>

              @if (activePlaylist(); as pl) {
                <h3>{{ pl.name }}</h3>
                @if (!tracks().length) {
                  <p class="empty">This playlist has no playable tracks.</p>
                }
                <div class="tracks">
                  @for (track of tracks(); track track.id) {
                    <div class="track">
                      <div>
                        <strong>{{ track.name }}</strong>
                        <p>{{ track.artists.join(', ') }} · {{ formatDuration(track.durationMs) }}</p>
                      </div>
                      <div class="track-actions">
                        <p-button
                          label="Play"
                          icon="pi pi-play"
                          [text]="true"
                          (onClick)="playTrack(track)"
                        />
                        @if (track.previewUrl) {
                          <audio [src]="track.previewUrl" controls preload="none"></audio>
                        }
                      </div>
                    </div>
                  }
                </div>
              }

              @if (embedUrl(); as url) {
                <div class="embed">
                  <iframe
                    [src]="url"
                    title="Spotify player"
                    allow="encrypted-media; clipboard-write"
                    loading="lazy"
                  ></iframe>
                </div>
              }
            }
          </p-card>
        </p-tabpanel>
      </p-tabpanels>
    </p-tabs>
  `,
  styles: `
    .heading { display: flex; gap: 0.75rem; align-items: flex-start; max-width: 48rem; }
    .heading i { font-size: 1.4rem; margin-top: 0.15rem; }
    .heading p { margin: 0.15rem 0 0; color: var(--p-text-muted-color); font-size: 0.9rem; font-weight: 400; }
    .error, .ok, .hint, .empty, p-card, p-tabs { margin: 1rem; }
    .error { color: var(--p-red-500); }
    .ok { color: var(--p-green-600); }
    .hint { color: var(--p-text-muted-color); }
    .spotify-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .playlist-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 0.75rem;
      margin-bottom: 1rem;
    }
    .playlist {
      border: 1px solid var(--p-content-border-color);
      background: var(--p-content-background);
      border-radius: 10px;
      padding: 0.75rem;
      text-align: left;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      color: inherit;
    }
    .playlist img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 6px; }
    .playlist small { color: var(--p-text-muted-color); }
    .tracks { display: flex; flex-direction: column; gap: 0.75rem; }
    .track {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
      align-items: center;
      border-bottom: 1px solid var(--p-content-border-color);
      padding-bottom: 0.75rem;
    }
    .track p { margin: 0.2rem 0 0; color: var(--p-text-muted-color); font-size: 0.9rem; }
    .track-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
    .embed iframe {
      width: 100%;
      min-height: 152px;
      border: 0;
      border-radius: 12px;
      margin-top: 1rem;
    }
  `,
})
export class ImportsPage {
  availability = signal({ googlePhotos: false, googleDrive: false, spotify: false });
  spotifyConnected = signal(false);
  playlists = signal<SpotifyPlaylist[]>([]);
  tracks = signal<SpotifyTrack[]>([]);
  activePlaylist = signal<SpotifyPlaylist | null>(null);
  embedUrl = signal<SafeResourceUrl | null>(null);
  busy = signal(false);
  error = signal<string | null>(null);
  ok = signal<string | null>(null);

  constructor(
    @Inject(GET_CLOUD_IMPORT_AVAILABILITY) private readonly getAvailability: GetCloudImportAvailability,
    @Inject(IMPORT_GOOGLE_PHOTOS) private readonly importPhotosUseCase: ImportGooglePhotos,
    @Inject(IMPORT_GOOGLE_DRIVE) private readonly importDriveUseCase: ImportGoogleDrive,
    @Inject(GET_SPOTIFY_STATUS) private readonly getSpotifyStatus: GetSpotifyStatus,
    @Inject(CONNECT_SPOTIFY) private readonly connectSpotifyUseCase: ConnectSpotify,
    @Inject(DISCONNECT_SPOTIFY) private readonly disconnectSpotifyUseCase: DisconnectSpotify,
    @Inject(LIST_SPOTIFY_PLAYLISTS) private readonly listPlaylistsUseCase: ListSpotifyPlaylists,
    @Inject(LIST_SPOTIFY_PLAYLIST_TRACKS)
    private readonly listTracksUseCase: ListSpotifyPlaylistTracks,
    private readonly sanitizer: DomSanitizer,
  ) {
    this.refreshStatus();
  }

  refreshStatus() {
    this.availability.set(this.getAvailability.execute());
    const status = this.getSpotifyStatus.execute();
    this.spotifyConnected.set(status.connected);
    if (status.configured && status.connected) {
      void this.loadPlaylists();
    }
  }

  async importPhotos() {
    this.busy.set(true);
    this.error.set(null);
    this.ok.set(null);
    try {
      const result = await this.importPhotosUseCase.execute();
      this.ok.set(summarizeImportBatch(result.imported, result.failed));
      if (result.errors.length) {
        this.error.set(result.errors.slice(0, 3).join(' · '));
      }
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.busy.set(false);
    }
  }

  async importDrive() {
    this.busy.set(true);
    this.error.set(null);
    this.ok.set(null);
    try {
      const result = await this.importDriveUseCase.execute();
      this.ok.set(summarizeImportBatch(result.imported, result.failed));
      if (result.errors.length) {
        this.error.set(result.errors.slice(0, 3).join(' · '));
      }
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.busy.set(false);
    }
  }

  async connectSpotify() {
    this.error.set(null);
    try {
      await this.connectSpotifyUseCase.execute();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  disconnectSpotify() {
    this.disconnectSpotifyUseCase.execute();
    this.spotifyConnected.set(false);
    this.playlists.set([]);
    this.tracks.set([]);
    this.activePlaylist.set(null);
    this.embedUrl.set(null);
    this.ok.set('Disconnected from Spotify.');
  }

  async loadPlaylists() {
    this.error.set(null);
    try {
      this.playlists.set(await this.listPlaylistsUseCase.execute());
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async openPlaylist(playlist: SpotifyPlaylist) {
    this.activePlaylist.set(playlist);
    this.tracks.set([]);
    this.embedUrl.set(null);
    this.error.set(null);
    try {
      this.tracks.set(await this.listTracksUseCase.execute(playlist.id));
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  playTrack(track: SpotifyTrack) {
    this.embedUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(spotifyEmbedUrl(track.id)));
  }

  formatDuration(ms: number) {
    return formatTrackDuration(ms);
  }
}
