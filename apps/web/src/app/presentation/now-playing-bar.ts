import { Component, Inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { formatPlaybackTime, MusicPlaybackService } from './music-playback.service';

@Component({
  selector: 'app-now-playing-bar',
  imports: [RouterLink, Button],
  template: `
    @if (playback.current(); as track) {
      <div class="bar">
        <div class="transport">
          <p-button icon="pi pi-step-backward" [text]="true" (onClick)="playback.previous()" />
          <p-button
            [icon]="playback.playing() ? 'pi pi-pause' : 'pi pi-play'"
            [rounded]="true"
            (onClick)="playback.toggle()"
          />
          <p-button icon="pi pi-step-forward" [text]="true" (onClick)="playback.next()" />
        </div>
        <a class="title" [routerLink]="['/player', track.id]">{{ track.name }}</a>
        <span class="time">{{ formatPlaybackTime(playback.currentTime()) }}</span>
        <input
          class="seek"
          type="range"
          min="0"
          [max]="playback.duration() || 0"
          [value]="playback.currentTime()"
          (input)="onSeek($event)"
        />
        <span class="time">{{ formatPlaybackTime(playback.duration()) }}</span>
        <p-button icon="pi pi-times" [text]="true" (onClick)="playback.stop()" />
      </div>
    }
  `,
  styles: `
    .bar {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 20;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 1rem;
      background: var(--p-content-background);
      border-top: 1px solid var(--p-content-border-color);
      box-shadow: 0 -8px 24px rgb(0 0 0 / 12%);
    }
    .transport { display: flex; align-items: center; gap: 0.15rem; }
    .title {
      min-width: 8rem;
      max-width: 18rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: 600;
    }
    .seek { flex: 1; min-width: 6rem; }
    .time { font-variant-numeric: tabular-nums; color: var(--p-text-muted-color); font-size: 0.85rem; }
  `,
})
export class NowPlayingBar {
  readonly formatPlaybackTime = formatPlaybackTime;

  constructor(@Inject(MusicPlaybackService) readonly playback: MusicPlaybackService) {}

  onSeek(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.playback.seek(value);
  }
}
