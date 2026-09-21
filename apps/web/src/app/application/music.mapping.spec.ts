import { musicSourceLabel } from './music.mapping';

describe('musicSourceLabel', () => {
  it('drops the musicdl client suffix', () => {
    expect(musicSourceLabel('NeteaseMusicClient')).toBe('Netease');
    expect(musicSourceLabel('QQMusicClient')).toBe('QQ');
  });
});
