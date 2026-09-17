import { Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { LIST_LIBRARY, LOAD_MEDIA_BLOB } from '../application/use-cases.tokens';
import type { ListLibrary, LoadMediaBlob } from '../application/use-cases.tokens';
import { MediaFile } from '../domain/models';

@Component({
  selector: 'app-player-page',
  imports: [RouterLink, Button, Card],
  template: `
    <p-button label="Back" icon="pi pi-arrow-left" routerLink="/library" [text]="true" />
    @if (file(); as current) {
      <p-card [header]="current.name">
        @if (mediaSrc(); as src) {
          @if (current.media_kind === 'photo') {
            <img class="media" [src]="src" [alt]="current.name" />
          } @else if (current.media_kind === 'video') {
            <video class="media" [src]="src" controls></video>
          } @else if (current.media_kind === 'audio') {
            <audio [src]="src" controls></audio>
          } @else {
            <a [href]="src">Download</a>
          }
        }
      </p-card>
    }
  `,
  styles: `
    .media { max-width: 100%; max-height: 70vh; display: block; margin: 0 auto; }
    :host { display: block; padding: 1rem; }
  `,
})
export class PlayerPage implements OnInit {
  file = signal<MediaFile | null>(null);
  mediaSrc = signal<string | null>(null);

  constructor(
    @Inject(LIST_LIBRARY) private readonly listLibrary: ListLibrary,
    @Inject(LOAD_MEDIA_BLOB) private readonly loadMedia: LoadMediaBlob,
    private readonly route: ActivatedRoute,
  ) {}

  async ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    const data = await this.listLibrary.execute();
    const current = data.files.find((f) => f.id === id) ?? null;
    this.file.set(current);
    if (current) {
      try {
        this.mediaSrc.set(await this.loadMedia.execute(current.id, false));
      } catch {
        this.mediaSrc.set(current.preview_url ?? null);
      }
    }
  }
}
