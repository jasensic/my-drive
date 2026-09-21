import { MediaFile } from '../domain/models';
import { audioQueue, formatPlaybackTime } from './music-playback.service';

function track(id: string, kind: MediaFile['media_kind'] = 'audio'): MediaFile {
  return {
    id,
    album_id: null,
    name: id,
    size: 1,
    mime: 'audio/mpeg',
    checksum: 'sha256:x',
    media_kind: kind,
    created_at: '',
    uploaded_at: '',
    content_url: '',
    thumbnail_url: null,
  };
}

describe('audioQueue', () => {
  it('keeps audio files and starts at the chosen track', () => {
    const files = [track('a'), track('photo', 'photo'), track('b')];
    expect(audioQueue(files, 'b')).toEqual({ queue: [track('a'), track('b')], index: 1 });
  });
});

describe('formatPlaybackTime', () => {
  it('formats minutes and seconds', () => {
    expect(formatPlaybackTime(65)).toBe('1:05');
    expect(formatPlaybackTime(Number.NaN)).toBe('0:00');
  });
});
