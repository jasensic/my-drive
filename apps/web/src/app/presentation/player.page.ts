import { Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { LIST_LIBRARY } from '../application/use-cases.tokens';
import type { ListLibrary } from '../application/use-cases.tokens';
import { MediaFile } from '../domain/models';

@Component({
  selector: 'app-player-page',
  imports: [RouterLink, Button, Card],
  template: `
    <p-button label="Back" icon="pi pi-arrow-left" routerLink="/library" [text]="true" />
    @if (file(); as current) {
      <p-card [header]="current.name">
        @if (current.media_kind === 'photo') {
          <img class="media" [src]="current.content_url" [alt]="current.name" />
        } @else if (current.media_kind === 'video') {
          <video class="media" [src]="current.content_url" controls></video>
        } @else if (current.media_kind === 'audio') {
          <audio [src]="current.content_url" controls></audio>
        } @else {
          <a [href]="current.content_url">Download</a>
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

  constructor(
    @Inject(LIST_LIBRARY) private readonly listLibrary: ListLibrary,
    private readonly route: ActivatedRoute,
  ) {}

  async ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    const data = await this.listLibrary.execute();
    this.file.set(data.files.find((f) => f.id === id) ?? null);
  }
}
