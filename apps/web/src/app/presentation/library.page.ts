import { Component, Inject, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import { filesInAlbum } from '../application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CREATE_ALBUM,
  LIST_LIBRARY,
  UPLOAD_MEDIA,
} from '../application/use-cases.tokens';
import type {
  AssignFileAlbum,
  CreateAlbum,
  ListLibrary,
  UploadMedia,
} from '../application/use-cases.tokens';
import { Album, MediaFile } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-library-page',
  imports: [FormsModule, RouterLink, Button, Card, Select, Tag, Toolbar, InputText],
  template: `
    <p-toolbar>
      <ng-template #start>
        <strong>Library</strong>
      </ng-template>
      <ng-template #end>
        <input #picker type="file" multiple hidden (change)="onFiles(picker.files); picker.value = ''" />
        <p-button label="Upload" icon="pi pi-upload" (onClick)="picker.click()" />
      </ng-template>
    </p-toolbar>

    <div class="filters">
      <p-select
        [options]="albumOptions()"
        [ngModel]="selectedAlbum()"
        (ngModelChange)="selectedAlbum.set($event)"
        optionLabel="label"
        optionValue="value"
        placeholder="All albums"
        [showClear]="true"
      />
      <input pInputText placeholder="New album" [(ngModel)]="newAlbum" />
      <p-button label="Create album" (onClick)="createAlbum()" [disabled]="!newAlbum.trim()" />
    </div>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <div class="grid">
      @for (file of visible(); track file.id) {
        <p-card>
          <div class="thumb">
            @if (file.media_kind === 'photo' && file.thumbnail_url) {
              <img [src]="file.thumbnail_url" [alt]="file.name" />
            } @else if (file.media_kind === 'video') {
              <video [src]="file.content_url" muted></video>
            } @else if (file.media_kind === 'audio') {
              <i class="pi pi-volume-up"></i>
            } @else {
              <i class="pi pi-file"></i>
            }
          </div>
          <div class="meta">
            <a [routerLink]="['/player', file.id]">{{ file.name }}</a>
            <p-tag [value]="file.media_kind" />
          </div>
          <p-select
            [options]="albumOptions()"
            [ngModel]="file.album_id"
            optionLabel="label"
            optionValue="value"
            placeholder="No album"
            [showClear]="true"
            (ngModelChange)="assign(file, $event)"
          />
        </p-card>
      }
    </div>
  `,
  styles: `
    .filters { display: flex; gap: 0.75rem; padding: 1rem; flex-wrap: wrap; align-items: center; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; padding: 1rem; }
    .thumb { height: 140px; display: grid; place-items: center; overflow: hidden; background: var(--p-surface-100); }
    .thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; }
    .meta { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin: 0.5rem 0; }
    .error { color: var(--p-red-500); padding: 0 1rem; }
  `,
})
export class LibraryPage {
  files = signal<MediaFile[]>([]);
  albums = signal<Album[]>([]);
  selectedAlbum = signal<string | null>(null);
  newAlbum = '';
  error = signal<string | null>(null);

  albumOptions = computed(() =>
    this.albums().map((a) => ({ label: a.name, value: a.id })),
  );

  visible = computed(() => filesInAlbum(this.files(), this.selectedAlbum()));

  constructor(
    @Inject(LIST_LIBRARY) private readonly listLibrary: ListLibrary,
    @Inject(UPLOAD_MEDIA) private readonly uploadMedia: UploadMedia,
    @Inject(CREATE_ALBUM) private readonly createAlbumUseCase: CreateAlbum,
    @Inject(ASSIGN_FILE_ALBUM) private readonly assignAlbum: AssignFileAlbum,
  ) {
    void this.reload();
  }

  async reload() {
    try {
      const data = await this.listLibrary.execute();
      this.files.set(data.files);
      this.albums.set(data.albums);
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async onFiles(list: FileList | null) {
    if (!list) {
      return;
    }
    this.error.set(null);
    try {
      for (const file of Array.from(list)) {
        await this.uploadMedia.execute(file, this.selectedAlbum() ?? undefined);
      }
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async createAlbum() {
    try {
      await this.createAlbumUseCase.execute(this.newAlbum);
      this.newAlbum = '';
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async assign(file: MediaFile, albumId: string | null) {
    try {
      await this.assignAlbum.execute(file.id, albumId);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }
}
