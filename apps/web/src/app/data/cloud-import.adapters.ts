import { Inject, Injectable } from '@angular/core';
import { bytesToImportedFile, defaultSpotifyRedirectUri, mapSpotifyPlaylists, mapSpotifyTracks } from '../application/cloud-import.mapping';
import { CloudImportConfig, ImportedLocalFile, SpotifyPlaylist, SpotifyTrack } from '../domain/cloud-import.models';
import {
  CLOUD_IMPORT_CONFIG,
  CloudImportConfigPort,
  GOOGLE_DRIVE_IMPORT,
  GOOGLE_PHOTOS_IMPORT,
  GoogleDriveImportPort,
  GooglePhotosImportPort,
  SPOTIFY_AUTH,
  SPOTIFY_LIBRARY,
  SpotifyAuthPort,
  SpotifyLibraryPort,
} from '../domain/ports';
import { CLOUD_IMPORT_ENV } from '../../cloud-import-config';

declare global {
  interface Window {
    google?: {
      accounts?: {
        oauth2?: {
          initTokenClient(config: {
            client_id: string;
            scope: string;
            callback: (response: { access_token?: string; error?: string }) => void;
          }): { requestAccessToken: (opts?: { prompt?: string }) => void };
        };
      };
      picker?: {
        PickerBuilder: new () => GooglePickerBuilder;
        ViewId: { DOCS: unknown };
        Feature: { MULTISELECT_ENABLED: unknown; NAV_HIDDEN: unknown };
        Action: { PICKED: string; CANCEL: string };
      };
    };
    gapi?: {
      load: (name: string, cb: () => void) => void;
      client: {
        load: (name: string, version: string) => Promise<void>;
        setToken: (token: { access_token: string } | null) => void;
        getToken: () => { access_token: string } | null;
      };
    };
  }
}

interface GooglePickerBuilder {
  addView(view: unknown): GooglePickerBuilder;
  enableFeature(feature: unknown): GooglePickerBuilder;
  setOAuthToken(token: string): GooglePickerBuilder;
  setDeveloperKey(key: string): GooglePickerBuilder;
  setAppId(appId: string): GooglePickerBuilder;
  setCallback(cb: (data: GooglePickerResponse) => void): GooglePickerBuilder;
  build(): { setVisible: (visible: boolean) => void };
}

interface GooglePickerResponse {
  action: string;
  docs?: Array<{ id: string; name: string; mimeType: string }>;
}

interface PhotosSession {
  id: string;
  pickerUri: string;
  mediaItemsSet?: boolean;
}

interface PickedMediaItem {
  id: string;
  mediaFile?: {
    baseUrl?: string;
    mimeType?: string;
    filename?: string;
  };
}

const GIS_SRC = 'https://accounts.google.com/gsi/client';
const GAPI_SRC = 'https://apis.google.com/js/api.js';
const PHOTOS_PICKER_SCOPE = 'https://www.googleapis.com/auth/photospicker.mediaitems.readonly';
const DRIVE_SCOPE = 'https://www.googleapis.com/auth/drive.readonly';
const SPOTIFY_SCOPES = 'playlist-read-private playlist-read-collaborative user-library-read';
const SPOTIFY_TOKEN_KEY = 'mydrive.spotify.token';
const SPOTIFY_PKCE_KEY = 'mydrive.spotify.pkce';

@Injectable()
export class EnvCloudImportConfig implements CloudImportConfigPort {
  get(): CloudImportConfig {
    return {
      googleClientId: CLOUD_IMPORT_ENV.googleClientId ?? '',
      googleApiKey: CLOUD_IMPORT_ENV.googleApiKey ?? '',
      googleAppId: CLOUD_IMPORT_ENV.googleAppId ?? '',
      spotifyClientId: CLOUD_IMPORT_ENV.spotifyClientId ?? '',
      spotifyRedirectUri: CLOUD_IMPORT_ENV.spotifyRedirectUri ?? '',
    };
  }

  resolveSpotifyRedirectUri(): string {
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    return defaultSpotifyRedirectUri(origin, this.get().spotifyRedirectUri);
  }
}

@Injectable()
export class BrowserGooglePhotosImport implements GooglePhotosImportPort {
  constructor(@Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort) {}

  async pickFiles(): Promise<ImportedLocalFile[]> {
    const clientId = this.config.get().googleClientId.trim();
    if (!clientId) {
      throw new Error('GOOGLE_OAUTH_CLIENT_ID is not set.');
    }
    await loadScript(GIS_SRC);
    const token = await requestGoogleAccessToken(clientId, PHOTOS_PICKER_SCOPE);
    const session = await createPhotosSession(token);
    const pickerUrl = session.pickerUri.endsWith('/autoclose')
      ? session.pickerUri
      : `${session.pickerUri}/autoclose`;
    const popup = window.open(pickerUrl, 'google-photos-picker', 'width=960,height=720');
    if (!popup) {
      throw new Error('Popup blocked. Allow popups for Google Photos picking.');
    }
    await pollPhotosSession(token, session.id);
    try {
      popup.close();
    } catch {
      /* ignore */
    }
    const items = await listPickedMediaItems(token, session.id);
    const files: ImportedLocalFile[] = [];
    for (const item of items) {
      const baseUrl = item.mediaFile?.baseUrl;
      if (!baseUrl) {
        continue;
      }
      const mime = item.mediaFile?.mimeType ?? 'application/octet-stream';
      const name = item.mediaFile?.filename || guessFilename(item.id, mime);
      const downloadUrl = mime.startsWith('video/') ? `${baseUrl}=dv` : `${baseUrl}=d`;
      const bytes = await fetchAuthorizedBytes(downloadUrl, token);
      files.push(bytesToImportedFile(name, mime, bytes));
    }
    await deletePhotosSession(token, session.id).catch(() => undefined);
    return files;
  }
}

@Injectable()
export class BrowserGoogleDriveImport implements GoogleDriveImportPort {
  constructor(@Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort) {}

  async pickFiles(): Promise<ImportedLocalFile[]> {
    const { googleClientId, googleApiKey, googleAppId } = this.config.get();
    if (!googleClientId.trim() || !googleApiKey.trim()) {
      throw new Error('GOOGLE_OAUTH_CLIENT_ID and GOOGLE_API_KEY are required for Drive import.');
    }
    await Promise.all([loadScript(GIS_SRC), loadScript(GAPI_SRC)]);
    await loadGapiPicker();
    const token = await requestGoogleAccessToken(googleClientId.trim(), DRIVE_SCOPE);
    const docs = await openDrivePicker(token, googleApiKey.trim(), googleAppId.trim());
    const files: ImportedLocalFile[] = [];
    for (const doc of docs) {
      const downloaded = await downloadDriveFile(token, doc);
      files.push(downloaded);
    }
    return files;
  }
}

@Injectable()
export class BrowserSpotifyAuth implements SpotifyAuthPort {
  constructor(@Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort) {}

  isConnected(): boolean {
    return Boolean(readSpotifyToken()?.access_token);
  }

  async beginLogin(): Promise<void> {
    const clientId = this.config.get().spotifyClientId.trim();
    if (!clientId) {
      throw new Error('SPOTIFY_CLIENT_ID is not set.');
    }
    const verifier = randomString(64);
    const challenge = await pkceChallenge(verifier);
    const state = randomString(24);
    sessionStorage.setItem(SPOTIFY_PKCE_KEY, JSON.stringify({ verifier, state }));
    const redirectUri = this.config.resolveSpotifyRedirectUri();
    const params = new URLSearchParams({
      client_id: clientId,
      response_type: 'code',
      redirect_uri: redirectUri,
      scope: SPOTIFY_SCOPES,
      code_challenge_method: 'S256',
      code_challenge: challenge,
      state,
    });
    window.location.assign(`https://accounts.spotify.com/authorize?${params.toString()}`);
  }

  async completeLogin(code: string, state: string): Promise<void> {
    const raw = sessionStorage.getItem(SPOTIFY_PKCE_KEY);
    if (!raw) {
      throw new Error('Spotify login session expired. Try connecting again.');
    }
    const pkce = JSON.parse(raw) as { verifier: string; state: string };
    sessionStorage.removeItem(SPOTIFY_PKCE_KEY);
    if (pkce.state !== state) {
      throw new Error('Spotify login state mismatch.');
    }
    const clientId = this.config.get().spotifyClientId.trim();
    const redirectUri = this.config.resolveSpotifyRedirectUri();
    const body = new URLSearchParams({
      client_id: clientId,
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri,
      code_verifier: pkce.verifier,
    });
    const res = await fetch('https://accounts.spotify.com/api/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    if (!res.ok) {
      throw new Error(`Spotify token exchange failed (${res.status}).`);
    }
    const json = (await res.json()) as {
      access_token: string;
      refresh_token?: string;
      expires_in: number;
    };
    writeSpotifyToken({
      access_token: json.access_token,
      refresh_token: json.refresh_token,
      expires_at: Date.now() + json.expires_in * 1000,
    });
  }

  logout(): void {
    sessionStorage.removeItem(SPOTIFY_TOKEN_KEY);
    sessionStorage.removeItem(SPOTIFY_PKCE_KEY);
  }
}

@Injectable()
export class BrowserSpotifyLibrary implements SpotifyLibraryPort {
  async listPlaylists(): Promise<SpotifyPlaylist[]> {
    const token = await requireSpotifyAccessToken();
    const items: unknown[] = [];
    let url: string | null = 'https://api.spotify.com/v1/me/playlists?limit=50';
    while (url) {
      const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) {
        throw new Error(`Spotify playlists failed (${res.status}).`);
      }
      const json = (await res.json()) as { items?: unknown[]; next?: string | null };
      items.push(...(json.items ?? []));
      url = json.next ?? null;
    }
    return mapSpotifyPlaylists(items as never);
  }

  async listPlaylistTracks(playlistId: string): Promise<SpotifyTrack[]> {
    const token = await requireSpotifyAccessToken();
    const items: unknown[] = [];
    let url: string | null =
      `https://api.spotify.com/v1/playlists/${encodeURIComponent(playlistId)}/tracks?limit=50`;
    while (url) {
      const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) {
        throw new Error(`Spotify tracks failed (${res.status}).`);
      }
      const json = (await res.json()) as { items?: unknown[]; next?: string | null };
      items.push(...(json.items ?? []));
      url = json.next ?? null;
    }
    return mapSpotifyTracks(items as never);
  }
}

export const CLOUD_IMPORT_DATA_PROVIDERS = [
  { provide: CLOUD_IMPORT_CONFIG, useClass: EnvCloudImportConfig },
  { provide: GOOGLE_PHOTOS_IMPORT, useClass: BrowserGooglePhotosImport },
  { provide: GOOGLE_DRIVE_IMPORT, useClass: BrowserGoogleDriveImport },
  { provide: SPOTIFY_AUTH, useClass: BrowserSpotifyAuth },
  { provide: SPOTIFY_LIBRARY, useClass: BrowserSpotifyLibrary },
];

function loadScript(src: string): Promise<void> {
  const existing = document.querySelector(`script[src="${src}"]`);
  if (existing) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`Failed to load ${src}`));
    document.head.appendChild(script);
  });
}

function loadGapiPicker(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!window.gapi) {
      reject(new Error('Google API script missing'));
      return;
    }
    window.gapi.load('picker', () => resolve());
  });
}

function requestGoogleAccessToken(clientId: string, scope: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const init = window.google?.accounts?.oauth2?.initTokenClient;
    if (!init) {
      reject(new Error('Google Identity Services unavailable'));
      return;
    }
    const client = init({
      client_id: clientId,
      scope,
      callback: (response) => {
        if (response.error || !response.access_token) {
          reject(new Error(response.error || 'Google access token denied'));
          return;
        }
        resolve(response.access_token);
      },
    });
    client.requestAccessToken({ prompt: '' });
  });
}

async function createPhotosSession(token: string): Promise<PhotosSession> {
  const res = await fetch('https://photospicker.googleapis.com/v1/sessions', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: '{}',
  });
  if (!res.ok) {
    throw new Error(`Google Photos session failed (${res.status}).`);
  }
  return (await res.json()) as PhotosSession;
}

async function pollPhotosSession(token: string, sessionId: string): Promise<void> {
  const deadline = Date.now() + 5 * 60 * 1000;
  while (Date.now() < deadline) {
    const res = await fetch(
      `https://photospicker.googleapis.com/v1/sessions/${encodeURIComponent(sessionId)}`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    if (!res.ok) {
      throw new Error(`Google Photos session poll failed (${res.status}).`);
    }
    const session = (await res.json()) as PhotosSession;
    if (session.mediaItemsSet) {
      return;
    }
    await sleep(1500);
  }
  throw new Error('Google Photos picking timed out.');
}

async function listPickedMediaItems(token: string, sessionId: string): Promise<PickedMediaItem[]> {
  const items: PickedMediaItem[] = [];
  let pageToken: string | undefined;
  do {
    const params = new URLSearchParams({ sessionId, pageSize: '100' });
    if (pageToken) {
      params.set('pageToken', pageToken);
    }
    const res = await fetch(`https://photospicker.googleapis.com/v1/mediaItems?${params}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
      throw new Error(`Google Photos media list failed (${res.status}).`);
    }
    const json = (await res.json()) as { mediaItems?: PickedMediaItem[]; nextPageToken?: string };
    items.push(...(json.mediaItems ?? []));
    pageToken = json.nextPageToken;
  } while (pageToken);
  return items;
}

async function deletePhotosSession(token: string, sessionId: string): Promise<void> {
  await fetch(`https://photospicker.googleapis.com/v1/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
}

function openDrivePicker(
  token: string,
  apiKey: string,
  appId: string,
): Promise<Array<{ id: string; name: string; mimeType: string }>> {
  return new Promise((resolve, reject) => {
    const pickerApi = window.google?.picker;
    if (!pickerApi) {
      reject(new Error('Google Picker unavailable'));
      return;
    }
    let builder = new pickerApi.PickerBuilder()
      .addView(pickerApi.ViewId.DOCS)
      .enableFeature(pickerApi.Feature.MULTISELECT_ENABLED)
      .setOAuthToken(token)
      .setDeveloperKey(apiKey)
      .setCallback((data) => {
        if (data.action === pickerApi.Action.CANCEL) {
          resolve([]);
          return;
        }
        if (data.action === pickerApi.Action.PICKED) {
          resolve(
            (data.docs ?? []).map((doc) => ({
              id: doc.id,
              name: doc.name,
              mimeType: doc.mimeType,
            })),
          );
        }
      });
    if (appId) {
      builder = builder.setAppId(appId);
    }
    builder.build().setVisible(true);
  });
}

async function fetchAuthorizedBytes(url: string, token: string): Promise<ArrayBuffer> {
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) {
    throw new Error(`Download failed (${res.status}).`);
  }
  return res.arrayBuffer();
}

async function downloadDriveFile(
  token: string,
  doc: { id: string; name: string; mimeType: string },
): Promise<ImportedLocalFile> {
  const mime = doc.mimeType || 'application/octet-stream';
  if (mime.startsWith('application/vnd.google-apps.')) {
    const exportMime =
      mime === 'application/vnd.google-apps.spreadsheet'
        ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        : 'application/pdf';
    const ext = exportMime.includes('sheet') ? '.xlsx' : '.pdf';
    const name = doc.name.endsWith(ext) ? doc.name : `${doc.name}${ext}`;
    const bytes = await fetchAuthorizedBytes(
      `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(doc.id)}/export?mimeType=${encodeURIComponent(exportMime)}`,
      token,
    );
    return bytesToImportedFile(name, exportMime, bytes);
  }
  const bytes = await fetchAuthorizedBytes(
    `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(doc.id)}?alt=media`,
    token,
  );
  return bytesToImportedFile(doc.name, mime, bytes);
}

function guessFilename(id: string, mime: string): string {
  if (mime.startsWith('image/')) {
    return `${id}.jpg`;
  }
  if (mime.startsWith('video/')) {
    return `${id}.mp4`;
  }
  return id;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function randomString(length: number): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (b) => chars[b % chars.length]).join('');
}

async function pkceChallenge(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64Url(digest);
}

function base64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

interface SpotifyStoredToken {
  access_token: string;
  refresh_token?: string;
  expires_at: number;
}

function readSpotifyToken(): SpotifyStoredToken | null {
  const raw = sessionStorage.getItem(SPOTIFY_TOKEN_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as SpotifyStoredToken;
  } catch {
    return null;
  }
}

function writeSpotifyToken(token: SpotifyStoredToken): void {
  sessionStorage.setItem(SPOTIFY_TOKEN_KEY, JSON.stringify(token));
}

async function requireSpotifyAccessToken(): Promise<string> {
  const token = readSpotifyToken();
  if (!token?.access_token) {
    throw new Error('Connect Spotify first.');
  }
  if (token.expires_at > Date.now() + 30_000) {
    return token.access_token;
  }
  if (!token.refresh_token) {
    sessionStorage.removeItem(SPOTIFY_TOKEN_KEY);
    throw new Error('Spotify session expired. Connect again.');
  }
  const clientId = CLOUD_IMPORT_ENV.spotifyClientId.trim();
  const body = new URLSearchParams({
    client_id: clientId,
    grant_type: 'refresh_token',
    refresh_token: token.refresh_token,
  });
  const res = await fetch('https://accounts.spotify.com/api/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!res.ok) {
    sessionStorage.removeItem(SPOTIFY_TOKEN_KEY);
    throw new Error('Spotify session expired. Connect again.');
  }
  const json = (await res.json()) as {
    access_token: string;
    refresh_token?: string;
    expires_in: number;
  };
  writeSpotifyToken({
    access_token: json.access_token,
    refresh_token: json.refresh_token ?? token.refresh_token,
    expires_at: Date.now() + json.expires_in * 1000,
  });
  return json.access_token;
}
