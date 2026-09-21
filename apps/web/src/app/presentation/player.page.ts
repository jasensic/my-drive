import { Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { GET_MEDIA, LOAD_MEDIA_BLOB } from '../application/use-cases.tokens';
import type { GetMedia, LoadMediaBlob } from '../application/use-cases.tokens';
import { MediaFile, siloForKind } from '../domain/models';
import { MusicPlaybackService } from './music-playback.service';

@Component({
  selector: 'app-player-page',
  imports: [RouterLink, Button, Card, LucideDynamicIcon],
  template: `
    <section class="page">
      <p-button label="Back" [routerLink]="backLink()" [text]="true">
        <ng-template #icon><svg lucideIcon="arrow-left" aria-hidden="true" /></ng-template>
      </p-button>
      @if (file(); as current) {
        <p-card [header]="current.name">
          @if (current.media_kind === 'audio') {
            @if (mediaSrc(); as src) {
              <img class="player-media player-cover" [src]="src" [alt]="current.name" />
            }
            <p>This track keeps playing while you browse the rest of my-drive.</p>
          } @else if (mediaSrc(); as src) {
            @if (current.media_kind === 'photo') {
              <img class="player-media" [src]="src" [alt]="current.name" />
            } @else if (current.media_kind === 'video') {
              <video class="player-media" [src]="src" controls></video>
            } @else {
              <a [href]="src">Download</a>
            }
          }
        </p-card>
      } @else if (missing()) {
        <div class="empty-state">
          <svg lucideIcon="file" [size]="28" aria-hidden="true" />
          <p>This file could not be opened.</p>
        </div>
      }
    </section>
  `,
})
export class PlayerPage implements OnInit {
  file = signal<MediaFile | null>(null);
  mediaSrc = signal<string | null>(null);
  missing = signal(false);

  constructor(
    @Inject(GET_MEDIA) private readonly getMedia: GetMedia,
    @Inject(LOAD_MEDIA_BLOB) private readonly loadMedia: LoadMediaBlob,
    @Inject(MusicPlaybackService) private readonly playback: MusicPlaybackService,
    private readonly route: ActivatedRoute,
  ) {}

  backLink() {
    const current = this.file();
    return current ? '/' + siloForKind(current.media_kind) : '/photos';
  }

  async ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.missing.set(true);
      return;
    }
    try {
      const current = await this.getMedia.execute(id);
      this.file.set(current);
      if (current.media_kind === 'audio') {
        await this.playback.playFile(current);
        try {
          this.mediaSrc.set(await this.loadMedia.execute(current.id, true));
        } catch {
          this.mediaSrc.set(null);
        }
        return;
      }
      try {
        this.mediaSrc.set(await this.loadMedia.execute(current.id, false));
      } catch {
        this.mediaSrc.set(current.preview_url ?? null);
      }
    } catch {
      this.file.set(null);
      this.missing.set(true);
    }
  }
}
