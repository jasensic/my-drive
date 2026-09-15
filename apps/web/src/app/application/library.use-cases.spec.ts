import { filesInAlbum } from './library.use-cases';
import { MediaFile } from '../domain/models';

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
