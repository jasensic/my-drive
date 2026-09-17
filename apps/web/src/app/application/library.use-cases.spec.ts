import { filesInAlbum, isImagePreviewBlob, ListLibraryService } from './library.use-cases';
import { MediaFile } from '../domain/models';
import { AlbumRepository, FileRepository } from '../domain/ports';

describe('filesInAlbum', () => {
  const files: MediaFile[] = [
    {
      id: '1',
      album_id: 'a',
      name: 'one.jpg',
      size: 1,
      mime: 'image/jpeg',
      checksum: 'x',
      media_kind: 'photo',
      created_at: '',
      uploaded_at: '',
      content_url: '/1',
      thumbnail_url: null,
    },
    {
      id: '2',
      album_id: null,
      name: 'two.jpg',
      size: 1,
      mime: 'image/jpeg',
      checksum: 'y',
      media_kind: 'photo',
      created_at: '',
      uploaded_at: '',
      content_url: '/2',
      thumbnail_url: null,
    },
  ];

  it('returns all files when no album is selected', () => {
    expect(filesInAlbum(files, null).length).toBe(2);
  });

  it('filters by album id', () => {
    expect(filesInAlbum(files, 'a').map((f) => f.id)).toEqual(['1']);
  });
});

describe('isImagePreviewBlob', () => {
  it('accepts image blobs and generic binary types', () => {
    expect(isImagePreviewBlob(new Blob([new Uint8Array([1, 2])], { type: 'image/png' }))).toBeTrue();
    expect(isImagePreviewBlob(new Blob([new Uint8Array([1, 2])], { type: '' }))).toBeTrue();
    expect(isImagePreviewBlob(new Blob([new Uint8Array([1, 2])], { type: 'application/octet-stream' }))).toBeTrue();
  });

  it('rejects empty or non-image blobs', () => {
    expect(isImagePreviewBlob(new Blob([], { type: 'image/png' }))).toBeFalse();
    expect(isImagePreviewBlob(new Blob([new Uint8Array([1])], { type: 'application/json' }))).toBeFalse();
  });
});

describe('ListLibraryService', () => {
  it('attaches authenticated object URLs for photos', async () => {
    const original = URL.createObjectURL;
    URL.createObjectURL = () => 'blob:preview';
    const files: FileRepository = {
      list: async () => [
        {
          id: '1',
          album_id: null,
          name: 'shot.png',
          size: 2,
          mime: 'image/png',
          checksum: 'x',
          media_kind: 'photo',
          created_at: '',
          uploaded_at: '',
          content_url: '/v1/files/1/content',
          thumbnail_url: '/v1/files/1/thumbnail',
        },
        {
          id: '2',
          album_id: null,
          name: 'clip.mp4',
          size: 2,
          mime: 'video/mp4',
          checksum: 'y',
          media_kind: 'video',
          created_at: '',
          uploaded_at: '',
          content_url: '/v1/files/2/content',
          thumbnail_url: null,
        },
      ],
      upload: async () => Promise.reject(new Error('unused')),
      assignAlbum: async () => Promise.reject(new Error('unused')),
      contentUrl: () => '',
      blob: async () => new Blob([new Uint8Array([0xff, 0xd8])], { type: 'application/octet-stream' }),
    };
    const albums: AlbumRepository = {
      list: async () => [],
      create: async (name) => ({ id: 'a', name, created_at: '' }),
    };
    try {
      const result = await new ListLibraryService(files, albums).execute();
      expect(result.files[0].preview_url).toBe('blob:preview');
      expect(result.files[1].preview_url).toBeUndefined();
    } finally {
      URL.createObjectURL = original;
    }
  });
});
