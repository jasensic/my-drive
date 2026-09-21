import { Component, Inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { formatPlaybackTime, MusicPlaybackService } from './music-playback.service';

@Component({
  selector: 'app-now-playing-bar',
  imports: [RouterLink, Button, LucideDynamicIcon],
  template: `
    @if (playback.current(); as track) {
      <div class="now-playing">
        @if (track.preview_url) {
          <img class="cover" [src]="track.preview_url" [alt]="track.name" />
        }
        <div class="transport">
          <p-button ariaLabel="Previous" [text]="true" (onClick)="playback.previous()">
            <ng-template #icon><svg lucideIcon="skip-back" aria-hidden="true" /></ng-template>
          </p-button>
          <p-button
            [ariaLabel]="playback.playing() ? 'Pause' : 'Play'"
            [rounded]="true"
            (onClick)="playback.toggle()"
          >
            <ng-template #icon>
              <svg [lucideIcon]="playback.playing() ? 'pause' : 'play'" aria-hidden="true" />
            </ng-template>
          </p-button>
          <p-button ariaLabel="Next" [text]="true" (onClick)="playback.next()">
            <ng-template #icon><svg lucideIcon="skip-forward" aria-hidden="true" /></ng-template>
          </p-button>
        </div>
        <a class="title" [routerLink]="['/player', track.id]">{{ track.name }}</a>
        <span class="time">{{ formatPlaybackTime(playback.currentTime()) }}</span>
        <input
          class="seek"
          type="range"
          min="0"
          aria-label="Seek"
          [max]="playback.duration() || 0"
          [value]="playback.currentTime()"
          (input)="onSeek($event)"
        />
        <span class="time">{{ formatPlaybackTime(playback.duration()) }}</span>
        <p-button ariaLabel="Stop" [text]="true" (onClick)="playback.stop()">
          <ng-template #icon><svg lucideIcon="x" aria-hidden="true" /></ng-template>
        </p-button>
      </div>
    }
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
