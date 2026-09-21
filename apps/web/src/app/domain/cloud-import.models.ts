/** Official cloud import integrations (no stream ripping / MP3 conversion). */

export interface CloudImportConfig {
  /** OAuth 2.0 Web client ID from Google Cloud Console. Env: GOOGLE_OAUTH_CLIENT_ID */
  googleClientId: string;
  /** Browser API key used by the Google Picker. Env: GOOGLE_API_KEY */
  googleApiKey: string;
  /** Google Cloud project number (Picker "appId"). Env: GOOGLE_APP_ID */
  googleAppId: string;
  /** Spotify Developer Dashboard client ID. Env: SPOTIFY_CLIENT_ID */
  spotifyClientId: string;
  /**
   * Must match a redirect URI registered for the Spotify app.
   * Env: SPOTIFY_REDIRECT_URI (empty → runtime origin + /imports/spotify/callback)
   */
  spotifyRedirectUri: string;
}

export interface CloudImportAvailability {
  googlePhotos: boolean;
  googleDrive: boolean;
  spotify: boolean;
}

export interface ImportedLocalFile {
  name: string;
  mimeType: string;
  file: File;
}

export interface CloudImportBatchResult {
  imported: number;
  failed: number;
  errors: string[];
}

export interface SpotifyPlaylist {
  id: string;
  name: string;
  trackCount: number;
  imageUrl: string | null;
}

export interface SpotifyTrack {
  id: string;
  name: string;
  artists: string[];
  albumName: string;
  durationMs: number;
  /** Official ~30s preview URL from Spotify, if available. Never a full-track download. */
  previewUrl: string | null;
  uri: string;
  externalUrl: string;
}

export interface SpotifyConnectionStatus {
  configured: boolean;
  connected: boolean;
}
