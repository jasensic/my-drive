import { Inject, Injectable } from '@angular/core';
import {
  CloudImportAvailability,
  CloudImportBatchResult,
  SpotifyConnectionStatus,
  SpotifyPlaylist,
  SpotifyTrack,
} from '../domain/cloud-import.models';
import {
  CLOUD_IMPORT_CONFIG,
  CloudImportConfigPort,
  FILE_REPOSITORY,
  FileRepository,
  GOOGLE_DRIVE_IMPORT,
  GOOGLE_PHOTOS_IMPORT,
  GoogleDriveImportPort,
  GooglePhotosImportPort,
  SPOTIFY_AUTH,
  SPOTIFY_LIBRARY,
  SpotifyAuthPort,
  SpotifyLibraryPort,
} from '../domain/ports';
import {
  cloudImportAvailability,
  summarizeImportBatch,
  toBatchResult,
} from './cloud-import.mapping';
import {
  ConnectSpotify,
  DisconnectSpotify,
  FinishSpotifyLogin,
  GetCloudImportAvailability,
  GetSpotifyStatus,
  ImportGoogleDrive,
  ImportGooglePhotos,
  ListSpotifyPlaylistTracks,
  ListSpotifyPlaylists,
} from './use-cases.tokens';

@Injectable()
export class GetCloudImportAvailabilityService implements GetCloudImportAvailability {
  constructor(@Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort) {}

  execute(): CloudImportAvailability {
    return cloudImportAvailability(this.config.get());
  }
}

@Injectable()
export class ImportGooglePhotosService implements ImportGooglePhotos {
  constructor(
    @Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort,
    @Inject(GOOGLE_PHOTOS_IMPORT) private readonly photos: GooglePhotosImportPort,
    @Inject(FILE_REPOSITORY) private readonly files: FileRepository,
  ) {}

  async execute(albumId?: string): Promise<CloudImportBatchResult> {
    if (!cloudImportAvailability(this.config.get()).googlePhotos) {
      throw new Error('Google Photos is not configured (set GOOGLE_OAUTH_CLIENT_ID).');
    }
    const picked = await this.photos.pickFiles();
    if (!picked.length) {
      return toBatchResult(0, []);
    }
    const errors: string[] = [];
    let imported = 0;
    for (const item of picked) {
      try {
        await this.files.upload(item.file, albumId);
        imported += 1;
      } catch (err) {
        errors.push(`${item.name}: ${err instanceof Error ? err.message : 'upload failed'}`);
      }
    }
    return toBatchResult(imported, errors);
  }
}

@Injectable()
export class ImportGoogleDriveService implements ImportGoogleDrive {
  constructor(
    @Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort,
    @Inject(GOOGLE_DRIVE_IMPORT) private readonly drive: GoogleDriveImportPort,
    @Inject(FILE_REPOSITORY) private readonly files: FileRepository,
  ) {}

  async execute(albumId?: string): Promise<CloudImportBatchResult> {
    if (!cloudImportAvailability(this.config.get()).googleDrive) {
      throw new Error('Google Drive is not configured (set GOOGLE_OAUTH_CLIENT_ID and GOOGLE_API_KEY).');
    }
    const picked = await this.drive.pickFiles();
    if (!picked.length) {
      return toBatchResult(0, []);
    }
    const errors: string[] = [];
    let imported = 0;
    for (const item of picked) {
      try {
        await this.files.upload(item.file, albumId);
        imported += 1;
      } catch (err) {
        errors.push(`${item.name}: ${err instanceof Error ? err.message : 'upload failed'}`);
      }
    }
    return toBatchResult(imported, errors);
  }
}

@Injectable()
export class GetSpotifyStatusService implements GetSpotifyStatus {
  constructor(
    @Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort,
    @Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort,
  ) {}

  execute(): SpotifyConnectionStatus {
    return {
      configured: cloudImportAvailability(this.config.get()).spotify,
      connected: this.auth.isConnected(),
    };
  }
}

@Injectable()
export class ConnectSpotifyService implements ConnectSpotify {
  constructor(
    @Inject(CLOUD_IMPORT_CONFIG) private readonly config: CloudImportConfigPort,
    @Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort,
  ) {}

  async execute(): Promise<void> {
    if (!cloudImportAvailability(this.config.get()).spotify) {
      throw new Error('Spotify is not configured (set SPOTIFY_CLIENT_ID).');
    }
    await this.auth.beginLogin();
  }
}

@Injectable()
export class FinishSpotifyLoginService implements FinishSpotifyLogin {
  constructor(@Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort) {}

  execute(code: string, state: string): Promise<void> {
    return this.auth.completeLogin(code, state);
  }
}

@Injectable()
export class DisconnectSpotifyService implements DisconnectSpotify {
  constructor(@Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort) {}

  execute(): void {
    this.auth.logout();
  }
}

@Injectable()
export class ListSpotifyPlaylistsService implements ListSpotifyPlaylists {
  constructor(
    @Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort,
    @Inject(SPOTIFY_LIBRARY) private readonly library: SpotifyLibraryPort,
  ) {}

  execute(): Promise<SpotifyPlaylist[]> {
    if (!this.auth.isConnected()) {
      return Promise.reject(new Error('Connect Spotify first.'));
    }
    return this.library.listPlaylists();
  }
}

@Injectable()
export class ListSpotifyPlaylistTracksService implements ListSpotifyPlaylistTracks {
  constructor(
    @Inject(SPOTIFY_AUTH) private readonly auth: SpotifyAuthPort,
    @Inject(SPOTIFY_LIBRARY) private readonly library: SpotifyLibraryPort,
  ) {}

  execute(playlistId: string): Promise<SpotifyTrack[]> {
    if (!this.auth.isConnected()) {
      return Promise.reject(new Error('Connect Spotify first.'));
    }
    const id = playlistId.trim();
    if (!id) {
      return Promise.reject(new Error('Playlist id is required'));
    }
    return this.library.listPlaylistTracks(id);
  }
}

export { summarizeImportBatch };
