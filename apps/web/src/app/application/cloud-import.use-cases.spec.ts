import {
  bytesToImportedFile,
  cloudImportAvailability,
  defaultSpotifyRedirectUri,
  formatTrackDuration,
  mapSpotifyPlaylist,
  mapSpotifyPlaylists,
  mapSpotifyTrack,
  mapSpotifyTracks,
  spotifyEmbedUrl,
  summarizeImportBatch,
  toBatchResult,
} from './cloud-import.mapping';
import {
  ConnectSpotifyService,
  ImportGoogleDriveService,
  ImportGooglePhotosService,
  ListSpotifyPlaylistTracksService,
  ListSpotifyPlaylistsService,
} from './cloud-import.use-cases';
import { CloudImportConfig } from '../domain/cloud-import.models';
import {
  CloudImportConfigPort,
  FileRepository,
  GoogleDriveImportPort,
  GooglePhotosImportPort,
  SpotifyAuthPort,
  SpotifyLibraryPort,
} from '../domain/ports';

const emptyConfig = (): CloudImportConfig => ({
  googleClientId: '',
  googleApiKey: '',
  googleAppId: '',
  spotifyClientId: '',
  spotifyRedirectUri: '',
});

describe('cloudImportAvailability', () => {
  it('requires Google client id for Photos and client+api key for Drive', () => {
    expect(cloudImportAvailability(emptyConfig())).toEqual({
      googlePhotos: false,
      googleDrive: false,
      spotify: false,
    });
    expect(
      cloudImportAvailability({
        ...emptyConfig(),
        googleClientId: 'client',
      }),
    ).toEqual({ googlePhotos: true, googleDrive: false, spotify: false });
    expect(
      cloudImportAvailability({
        ...emptyConfig(),
        googleClientId: 'client',
        googleApiKey: 'key',
        spotifyClientId: 'sp',
      }),
    ).toEqual({ googlePhotos: true, googleDrive: true, spotify: true });
  });
});

describe('defaultSpotifyRedirectUri', () => {
  it('uses configured URI when present, otherwise origin callback path', () => {
    expect(defaultSpotifyRedirectUri('http://localhost:4200/', 'https://app/cb')).toBe('https://app/cb');
    expect(defaultSpotifyRedirectUri('http://localhost:4200/', '')).toBe(
      'http://localhost:4200/imports/spotify/callback',
    );
  });
});

describe('Spotify mapping', () => {
  it('maps playlists and skips incomplete rows', () => {
    expect(
      mapSpotifyPlaylists([
        { id: '1', name: 'Liked vibes', tracks: { total: 3 }, images: [{ url: 'https://img' }] },
        { name: 'missing id' },
      ]),
    ).toEqual([
      { id: '1', name: 'Liked vibes', trackCount: 3, imageUrl: 'https://img' },
    ]);
    expect(mapSpotifyPlaylist({ id: 'x', name: 'Solo' })).toEqual({
      id: 'x',
      name: 'Solo',
      trackCount: 0,
      imageUrl: null,
    });
  });

  it('maps tracks including official preview URLs only', () => {
    const track = mapSpotifyTrack({
      id: 't1',
      name: 'Song',
      duration_ms: 125000,
      preview_url: 'https://p.scdn.co/mp3-preview/abc',
      uri: 'spotify:track:t1',
      external_urls: { spotify: 'https://open.spotify.com/track/t1' },
      artists: [{ name: 'A' }, { name: 'B' }],
      album: { name: 'Album' },
    });
    expect(track).toEqual({
      id: 't1',
      name: 'Song',
      artists: ['A', 'B'],
      albumName: 'Album',
      durationMs: 125000,
      previewUrl: 'https://p.scdn.co/mp3-preview/abc',
      uri: 'spotify:track:t1',
      externalUrl: 'https://open.spotify.com/track/t1',
    });
    expect(mapSpotifyTrack({ id: 'x', name: 'no uri' })).toBeNull();
    expect(
      mapSpotifyTracks([
        { track: { id: 't2', name: 'Ok', uri: 'spotify:track:t2' } },
        { track: null },
      ]),
    ).toHaveLength(1);
  });

  it('builds embed URLs and formats duration', () => {
    expect(spotifyEmbedUrl('abc')).toBe('https://open.spotify.com/embed/track/abc');
    expect(formatTrackDuration(125000)).toBe('2:05');
  });
});

describe('import helpers', () => {
  it('summarizes batch results', () => {
    expect(summarizeImportBatch(0, 0)).toBe('No files selected.');
    expect(summarizeImportBatch(2, 0)).toBe('Imported 2 files into my-drive.');
    expect(summarizeImportBatch(0, 1)).toBe('Import failed for 1 file.');
    expect(summarizeImportBatch(1, 2)).toBe('Imported 1 file; 2 failed.');
    expect(toBatchResult(1, ['a'])).toEqual({ imported: 1, failed: 1, errors: ['a'] });
  });

  it('wraps bytes as an uploadable File', () => {
    const bytes = new Uint8Array([1, 2, 3]).buffer;
    const imported = bytesToImportedFile('shot.jpg', 'image/jpeg', bytes);
    expect(imported.name).toBe('shot.jpg');
    expect(imported.mimeType).toBe('image/jpeg');
    expect(imported.file.size).toBe(3);
  });
});

describe('ImportGooglePhotosService', () => {
  it('rejects when Google Photos is unconfigured', async () => {
    const config: CloudImportConfigPort = {
      get: () => emptyConfig(),
      resolveSpotifyRedirectUri: () => '',
    };
    const photos: GooglePhotosImportPort = { pickFiles: async () => [] };
    const files: FileRepository = {
      list: async () => [],
      get: async () => Promise.reject(new Error('unused')),
      upload: async () => Promise.reject(new Error('should not upload')),
      assignAlbum: async () => Promise.reject(new Error('unused')),
      trash: async () => Promise.reject(new Error('unused')),
      restore: async () => Promise.reject(new Error('unused')),
      purge: async () => Promise.reject(new Error('unused')),
      emptyTrash: async () => ({ deleted: 0 }),
      contentUrl: () => '',
      blob: async () => new Blob(),
    };
    await expect(new ImportGooglePhotosService(config, photos, files).execute()).rejects.toThrow(
      /GOOGLE_OAUTH_CLIENT_ID/,
    );
  });

  it('uploads picked files through the existing file repository', async () => {
    const uploaded: string[] = [];
    const config: CloudImportConfigPort = {
      get: () => ({ ...emptyConfig(), googleClientId: 'cid' }),
      resolveSpotifyRedirectUri: () => '',
    };
    const photos: GooglePhotosImportPort = {
      pickFiles: async () => [
        bytesToImportedFile('a.jpg', 'image/jpeg', new Uint8Array([1]).buffer),
        bytesToImportedFile('b.jpg', 'image/jpeg', new Uint8Array([2]).buffer),
      ],
    };
    const files: FileRepository = {
      list: async () => [],
      get: async () => Promise.reject(new Error('unused')),
      upload: async (file) => {
        uploaded.push(file.name);
        return {
          id: file.name,
          album_id: null,
          name: file.name,
          size: file.size,
          mime: file.type,
          checksum: '',
          media_kind: 'photo',
          created_at: '',
          uploaded_at: '',
          content_url: '',
          thumbnail_url: null,
        };
      },
      assignAlbum: async () => Promise.reject(new Error('unused')),
      trash: async () => Promise.reject(new Error('unused')),
      restore: async () => Promise.reject(new Error('unused')),
      purge: async () => Promise.reject(new Error('unused')),
      emptyTrash: async () => ({ deleted: 0 }),
      contentUrl: () => '',
      blob: async () => new Blob(),
    };
    const result = await new ImportGooglePhotosService(config, photos, files).execute('album-1');
    expect(result).toEqual({ imported: 2, failed: 0, errors: [] });
    expect(uploaded).toEqual(['a.jpg', 'b.jpg']);
  });
});

describe('ImportGoogleDriveService', () => {
  it('rejects when Drive is unconfigured', async () => {
    const config: CloudImportConfigPort = {
      get: () => ({ ...emptyConfig(), googleClientId: 'cid' }),
      resolveSpotifyRedirectUri: () => '',
    };
    const drive: GoogleDriveImportPort = { pickFiles: async () => [] };
    const files = { upload: async () => Promise.reject(new Error('no')) } as unknown as FileRepository;
    await expect(new ImportGoogleDriveService(config, drive, files).execute()).rejects.toThrow(
      /GOOGLE_API_KEY/,
    );
  });
});

describe('Spotify use cases', () => {
  it('ConnectSpotify refuses when client id is missing', async () => {
    const config: CloudImportConfigPort = {
      get: () => emptyConfig(),
      resolveSpotifyRedirectUri: () => '',
    };
    const auth: SpotifyAuthPort = {
      isConnected: () => false,
      beginLogin: async () => undefined,
      completeLogin: async () => undefined,
      logout: () => undefined,
    };
    await expect(new ConnectSpotifyService(config, auth).execute()).rejects.toThrow(/SPOTIFY_CLIENT_ID/);
  });

  it('ListSpotifyPlaylists requires a connected session', async () => {
    const auth: SpotifyAuthPort = {
      isConnected: () => false,
      beginLogin: async () => undefined,
      completeLogin: async () => undefined,
      logout: () => undefined,
    };
    const library: SpotifyLibraryPort = {
      listPlaylists: async () => [],
      listPlaylistTracks: async () => [],
    };
    await expect(new ListSpotifyPlaylistsService(auth, library).execute()).rejects.toThrow(/Connect Spotify/);
  });

  it('ListSpotifyPlaylistTracks validates playlist id', async () => {
    const auth: SpotifyAuthPort = {
      isConnected: () => true,
      beginLogin: async () => undefined,
      completeLogin: async () => undefined,
      logout: () => undefined,
    };
    const library: SpotifyLibraryPort = {
      listPlaylists: async () => [],
      listPlaylistTracks: async () => [],
    };
    await expect(new ListSpotifyPlaylistTracksService(auth, library).execute('  ')).rejects.toThrow(
      /Playlist id/,
    );
  });
});
