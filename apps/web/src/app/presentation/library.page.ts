import { Component, Inject, computed, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import { fileMatchesSilo, filesInAlbum } from '../application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CREATE_ALBUM,
  EMPTY_TRASH,
  LIST_LIBRARY,
  PURGE_MEDIA,
  RESTORE_MEDIA,
  TRASH_MEDIA,
  UPLOAD_MEDIA,
} from '../application/use-cases.tokens';
import type {
  AssignFileAlbum,
  CreateAlbum,
  EmptyTrash,
  ListLibrary,
  PurgeMedia,
  RestoreMedia,
  TrashMedia,
  UploadMedia,
} from '../application/use-cases.tokens';
import { Album, LibrarySilo, MediaFile } from '../domain/models';
import { extractError } from './login.page';
import { MusicPlaybackService } from './music-playback.service';
import { MusicSearchPanel } from './music-search.panel';

const SILO_COPY: Record<
  LibrarySilo,
  { title: string; hint: string; accept: string; empty: string; icon: string }
> = {
  music: {
    title: 'Music',
    hint: 'Search with musicdl, or drop audio files here.',
    accept: 'audio/*',
    empty: 'No songs in this library yet.',
    icon: 'pi pi-volume-up',
  },
  photos: {
    title: 'Photos & videos',
    hint: 'Drop photos and videos here. They stay in this gallery, separate from music and documents.',
    accept: 'image/*,video/*',
    empty: 'No photos or videos yet.',
    icon: 'pi pi-images',
  },
  files: {
    title: 'Files',
    hint: 'Documents and other files live here, apart from music and the photo gallery.',
    accept: '',
    empty: 'No documents in this silo yet.',
    icon: 'pi pi-folder',
  },
};

@Component({
  selector: 'app-library-page',
  imports: [FormsModule, RouterLink, Button, Card, Select, Tag, Toolbar, InputText, MusicSearchPanel],
  template: `
    <p-toolbar>
      <ng-template #start>
        <div class="heading">
          <i [class]="copy().icon"></i>
          <div>
            <strong>{{ copy().title }}</strong>
            <p>{{ trashMode() ? 'Trash for this silo only. Items are removed after 30 days.' : copy().hint }}</p>
          </div>
        </div>
      </ng-template>
      <ng-template #end>
        <input
          #picker
          type="file"
          multiple
          hidden
          [attr.accept]="copy().accept || null"
          (change)="onFiles(picker.files); picker.value = ''"
        />
        @if (!trashMode()) {
          <p-button label="Upload" icon="pi pi-upload" (onClick)="picker.click()" />
        }
        <p-button
          [label]="trashMode() ? 'Back to library' : 'Trash'"
          [icon]="trashMode() ? 'pi pi-arrow-left' : 'pi pi-trash'"
          [outlined]="true"
          (onClick)="toggleTrash()"
        />
        @if (trashMode()) {
          <p-button
            label="Empty trash"
            icon="pi pi-times"
            severity="danger"
            [outlined]="true"
            [disabled]="!files().length"
            (onClick)="emptyTrash()"
          />
        }
      </ng-template>
    </p-toolbar>

    @if (!trashMode() && silo() === 'music') {
      <div class="search-panel">
        <app-music-search (imported)="onMusicImported($event)" />
      </div>
    }

    @if (!trashMode() && silo() === 'photos') {
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
    }

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (ok()) {
      <p class="ok">{{ ok() }}</p>
    }

    @if (!trashMode()) {
      <div
        class="dropzone"
        [class.active]="dragOver()"
        (dragover)="onDragOver($event)"
        (dragleave)="onDragLeave($event)"
        (drop)="onDrop($event)"
      >
        <i class="pi pi-cloud-upload"></i>
        <span>Drag and drop to upload into {{ copy().title.toLowerCase() }}</span>
      </div>
    }

    @if (!visible().length) {
      <p class="empty">{{ trashMode() ? 'This silo trash is empty.' : copy().empty }}</p>
    }

    <div class="grid">
      @for (file of visible(); track file.id) {
        <p-card>
          <div class="thumb">
            @if (file.preview_url) {
              <img [src]="file.preview_url" [alt]="file.name" />
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
            <p-tag [value]="kindLabel(file.media_kind)" />
          </div>
          <p class="size">{{ formatSize(file.size) }}</p>
          @if (trashMode()) {
            <p class="size">Deletes {{ file.purge_at ? formatDate(file.purge_at) : 'in 30 days' }}</p>
            <div class="actions">
              <p-button label="Restore" icon="pi pi-replay" [text]="true" (onClick)="restore(file)" />
              <p-button
                label="Delete forever"
                icon="pi pi-times"
                severity="danger"
                [text]="true"
                (onClick)="purge(file)"
              />
            </div>
          } @else {
            @if (silo() === 'photos') {
              <p-select
                [options]="albumOptions()"
                [ngModel]="file.album_id"
                optionLabel="label"
                optionValue="value"
                placeholder="No album"
                [showClear]="true"
                (ngModelChange)="assign(file, $event)"
              />
            }
            <div class="actions">
              @if (silo() === 'music') {
                <p-button
                  [label]="isCurrent(file) && playback.playing() ? 'Pause' : 'Play'"
                  [icon]="isCurrent(file) && playback.playing() ? 'pi pi-pause' : 'pi pi-play'"
                  [text]="true"
                  (onClick)="play(file)"
                />
              }
              <p-button label="Move to trash" icon="pi pi-trash" [text]="true" (onClick)="trash(file)" />
            </div>
          }
        </p-card>
      }
    </div>
  `,
  styles: `
    .heading { display: flex; gap: 0.75rem; align-items: flex-start; max-width: 42rem; }
    .heading i { font-size: 1.4rem; margin-top: 0.15rem; }
    .heading p { margin: 0.15rem 0 0; color: var(--p-text-muted-color); font-size: 0.9rem; font-weight: 400; }
    .search-panel { padding: 1rem 1rem 0; }
    .filters { display: flex; gap: 0.75rem; padding: 1rem; flex-wrap: wrap; align-items: center; }
    .dropzone {
      margin: 1rem;
      border: 1px dashed var(--p-content-border-color);
      border-radius: 12px;
      padding: 1.25rem;
      display: flex;
      gap: 0.75rem;
      align-items: center;
      justify-content: center;
      color: var(--p-text-muted-color);
      background: var(--p-content-background);
    }
    .dropzone.active { border-color: var(--p-primary-color); color: var(--p-primary-color); }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; padding: 1rem; }
    .thumb { height: 140px; display: grid; place-items: center; overflow: hidden; background: var(--p-surface-100); }
    .thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; }
    .meta { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin: 0.5rem 0; }
    .size, .empty, .error, .ok { padding: 0 1rem; color: var(--p-text-muted-color); }
    .error { color: var(--p-red-500); }
    .ok { color: var(--p-green-600); }
    .actions { display: flex; flex-wrap: wrap; gap: 0.25rem; margin-top: 0.35rem; }
  `,
})
export class LibraryPage {
  files = signal<MediaFile[]>([]);
  albums = signal<Album[]>([]);
  selectedAlbum = signal<string | null>(null);
  trashMode = signal(false);
  dragOver = signal(false);
  newAlbum = '';
  error = signal<string | null>(null);
  ok = signal<string | null>(null);
  silo = signal<LibrarySilo>('photos');

  copy = computed(() => SILO_COPY[this.silo()]);
  albumOptions = computed(() => this.albums().map((a) => ({ label: a.name, value: a.id })));
  visible = computed(() => filesInAlbum(this.files(), this.silo() === 'photos' ? this.selectedAlbum() : null));

  constructor(
    route: ActivatedRoute,
    @Inject(LIST_LIBRARY) private readonly listLibrary: ListLibrary,
    @Inject(UPLOAD_MEDIA) private readonly uploadMedia: UploadMedia,
    @Inject(CREATE_ALBUM) private readonly createAlbumUseCase: CreateAlbum,
    @Inject(ASSIGN_FILE_ALBUM) private readonly assignAlbum: AssignFileAlbum,
    @Inject(TRASH_MEDIA) private readonly trashMedia: TrashMedia,
    @Inject(RESTORE_MEDIA) private readonly restoreMedia: RestoreMedia,
    @Inject(PURGE_MEDIA) private readonly purgeMedia: PurgeMedia,
    @Inject(EMPTY_TRASH) private readonly emptyTrashUseCase: EmptyTrash,
    @Inject(MusicPlaybackService) readonly playback: MusicPlaybackService,
  ) {
    const silo = (route.snapshot.data['silo'] as LibrarySilo | undefined) ?? 'photos';
    this.silo.set(silo);
    void this.reload();
  }

  async reload() {
    try {
      const data = await this.listLibrary.execute(this.silo(), this.trashMode());
      this.files.set(data.files);
      this.albums.set(data.albums);
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  isCurrent(file: MediaFile): boolean {
    return this.playback.current()?.id === file.id;
  }

  play(file: MediaFile) {
    if (this.isCurrent(file)) {
      this.playback.toggle();
      return;
    }
    void this.playback.playQueue(this.visible(), file.id);
  }

  onMusicImported(name: string) {
    this.error.set(null);
    this.ok.set(`Saved ${name} to the music library.`);
    void this.reload();
  }

  toggleTrash() {
    this.trashMode.update((value) => !value);
    this.error.set(null);
    this.ok.set(null);
    void this.reload();
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
  }

  async onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    await this.onFiles(event.dataTransfer?.files ?? null);
  }

  async onFiles(list: FileList | null) {
    if (!list?.length) {
      return;
    }
    this.error.set(null);
    this.ok.set(null);
    const incoming = Array.from(list);
    const accepted = incoming.filter((file) => fileMatchesSilo({ mime: file.type, media_kind: 'other' }, this.silo()));
    const rejected = incoming.length - accepted.length;
    try {
      for (const file of accepted) {
        await this.uploadMedia.execute(file, this.selectedAlbum() ?? undefined);
      }
      if (accepted.length) {
        this.ok.set(`Uploaded ${accepted.length} file${accepted.length === 1 ? '' : 's'}.`);
        await this.reload();
      }
      if (rejected) {
        this.error.set(`${rejected} file${rejected === 1 ? '' : 's'} belong in another section.`);
      }
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

  async trash(file: MediaFile) {
    try {
      await this.trashMedia.execute(file.id);
      this.ok.set(`Moved ${file.name} to this silo’s trash.`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async restore(file: MediaFile) {
    try {
      await this.restoreMedia.execute(file.id);
      this.ok.set(`Restored ${file.name}.`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async purge(file: MediaFile) {
    if (!confirm(`Permanently delete ${file.name}? This cannot be undone.`)) {
      return;
    }
    try {
      await this.purgeMedia.execute(file.id);
      this.ok.set(`Deleted ${file.name}.`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async emptyTrash() {
    if (!confirm(`Empty the ${this.copy().title.toLowerCase()} trash? This cannot be undone.`)) {
      return;
    }
    try {
      const result = await this.emptyTrashUseCase.execute(this.silo());
      this.ok.set(`Removed ${result.deleted} item${result.deleted === 1 ? '' : 's'}.`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  kindLabel(kind: MediaFile['media_kind']) {
    switch (kind) {
      case 'photo':
        return 'photo';
      case 'video':
        return 'video';
      case 'audio':
        return 'audio';
      default:
        return 'file';
    }
  }

  formatSize(size: number) {
    if (size < 1024) {
      return `${size} B`;
    }
    if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(1)} KB`;
    }
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  formatDate(value: string) {
    return new Date(value).toLocaleDateString();
  }
}
