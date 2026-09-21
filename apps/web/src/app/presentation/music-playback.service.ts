import { Inject, Injectable, signal } from '@angular/core';
import { LOAD_MEDIA_BLOB } from '../application/use-cases.tokens';
import type { LoadMediaBlob } from '../application/use-cases.tokens';
import { MediaFile } from '../domain/models';

export function audioQueue(files: MediaFile[], startId: string): { queue: MediaFile[]; index: number } {
  const queue = files.filter((file) => file.media_kind === 'audio');
  const found = queue.findIndex((file) => file.id === startId);
  return { queue, index: found >= 0 ? found : 0 };
}

export function formatPlaybackTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '0:00';
  }
  const whole = Math.floor(seconds);
  const minutes = Math.floor(whole / 60);
  const rest = whole % 60;
  return `${minutes}:${rest.toString().padStart(2, '0')}`;
}

@Injectable()
export class MusicPlaybackService {
  readonly current = signal<MediaFile | null>(null);
  readonly playing = signal(false);
  readonly currentTime = signal(0);
  readonly duration = signal(0);

  private readonly audio = new Audio();
  private queue: MediaFile[] = [];
  private index = -1;
  private objectUrl: string | null = null;
  private loadToken = 0;

  constructor(@Inject(LOAD_MEDIA_BLOB) private readonly loadMedia: LoadMediaBlob) {
    this.audio.preload = 'auto';
    this.audio.addEventListener('timeupdate', () => this.currentTime.set(this.audio.currentTime));
    this.audio.addEventListener('durationchange', () => {
      this.duration.set(Number.isFinite(this.audio.duration) ? this.audio.duration : 0);
    });
    this.audio.addEventListener('play', () => {
      this.playing.set(true);
      this.setSessionState('playing');
    });
    this.audio.addEventListener('pause', () => {
      this.playing.set(false);
      this.setSessionState('paused');
    });
    this.audio.addEventListener('ended', () => void this.next());
    this.bindMediaSession();
  }

  async playQueue(files: MediaFile[], startId: string): Promise<void> {
    const next = audioQueue(files, startId);
    if (!next.queue.length) {
      return;
    }
    this.queue = next.queue;
    await this.loadIndex(next.index);
  }

  async playFile(file: MediaFile): Promise<void> {
    if (file.media_kind !== 'audio') {
      return;
    }
    if (this.current()?.id === file.id) {
      return;
    }
    const existing = this.queue.findIndex((item) => item.id === file.id);
    if (existing >= 0) {
      await this.loadIndex(existing);
      return;
    }
    await this.playQueue([file], file.id);
  }

  toggle(): void {
    if (this.playing()) {
      this.pause();
      return;
    }
    void this.resume();
  }

  pause(): void {
    this.audio.pause();
  }

  async resume(): Promise<void> {
    if (!this.audio.src) {
      return;
    }
    await this.audio.play();
  }

  seek(seconds: number): void {
    if (!Number.isFinite(seconds)) {
      return;
    }
    this.audio.currentTime = seconds;
    this.currentTime.set(seconds);
  }

  async next(): Promise<void> {
    if (this.index < 0 || this.index + 1 >= this.queue.length) {
      this.pause();
      return;
    }
    await this.loadIndex(this.index + 1);
  }

  async previous(): Promise<void> {
    if (this.audio.currentTime > 3) {
      this.seek(0);
      return;
    }
    if (this.index <= 0) {
      this.seek(0);
      return;
    }
    await this.loadIndex(this.index - 1);
  }

  stop(): void {
    this.loadToken += 1;
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.audio.load();
    this.revokeUrl();
    this.queue = [];
    this.index = -1;
    this.current.set(null);
    this.playing.set(false);
    this.currentTime.set(0);
    this.duration.set(0);
  }

  private async loadIndex(index: number): Promise<void> {
    const file = this.queue[index];
    if (!file) {
      this.stop();
      return;
    }
    const token = ++this.loadToken;
    this.index = index;
    this.current.set(file);
    this.currentTime.set(0);
    let url: string;
    try {
      url = await this.loadMedia.execute(file.id, false);
    } catch {
      if (token === this.loadToken) {
        this.stop();
      }
      return;
    }
    if (token !== this.loadToken) {
      URL.revokeObjectURL(url);
      return;
    }
    this.revokeUrl();
    this.objectUrl = url;
    this.audio.src = url;
    this.publishSession(file);
    try {
      await this.audio.play();
    } catch {
      this.playing.set(false);
    }
  }

  private revokeUrl(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }

  private publishSession(file: MediaFile): void {
    if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') {
      return;
    }
    navigator.mediaSession.metadata = new MediaMetadata({ title: file.name, artist: 'my-drive' });
    this.setSessionState('playing');
  }

  private setSessionState(state: MediaSessionPlaybackState): void {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = state;
    }
  }

  private bindMediaSession(): void {
    if (!('mediaSession' in navigator)) {
      return;
    }
    const actions: Array<[MediaSessionAction, () => void]> = [
      ['play', () => void this.resume()],
      ['pause', () => this.pause()],
      ['nexttrack', () => void this.next()],
      ['previoustrack', () => void this.previous()],
    ];
    for (const [action, handler] of actions) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // Some browsers reject actions they do not support.
      }
    }
  }
}
