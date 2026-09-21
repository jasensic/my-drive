import { Component, HostListener, Inject, ViewChild, computed, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Checkbox } from 'primeng/checkbox';
import { ContextMenu } from 'primeng/contextmenu';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import { fileMatchesSilo, filesInAlbum } from '../application/library.use-cases';
import {
  ASSIGN_FILE_ALBUM,
  CREATE_ALBUM,
  DELETE_ALBUM,
  EMPTY_TRASH,
  LIST_LIBRARY,
  PURGE_MEDIA,
  RENAME_ALBUM,
  RENAME_MEDIA,
  RESTORE_MEDIA,
  SHARE_MEDIA,
  TRASH_MEDIA,
  UPLOAD_MEDIA,
} from '../application/use-cases.tokens';
import type {
  AssignFileAlbum,
  CreateAlbum,
  DeleteAlbum,
  EmptyTrash,
  ListLibrary,
  PurgeMedia,
  RenameAlbum,
  RenameMedia,
  RestoreMedia,
  ShareMedia,
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
  imports: [
    FormsModule,
    RouterLink,
    Button,
    Card,
    Checkbox,
    ContextMenu,
    Dialog,
    Select,
    Tag,
    Toolbar,
    InputText,
  ],
  template: `
    <p-contextmenu #cm [model]="menuItems()" />
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
        @if (selectedCount()) {
          <p-button [label]="selectedCount() + ' selected'" icon="pi pi-times" [outlined]="true" (onClick)="clearSelection()" />
          <p-button label="Manage" icon="pi pi-ellipsis-h" (onClick)="openMenuForSelection($event)" />
        } @else if (!trashMode()) {
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
          [options]="albumFilterOptions()"
          [ngModel]="selectedAlbum()"
          (ngModelChange)="selectedAlbum.set($event)"
          optionLabel="label"
          optionValue="value"
          placeholder="All albums"
          [showClear]="true"
        />
        <input pInputText placeholder="New album" [(ngModel)]="newAlbum" (keydown.enter)="createAlbum()" />
        <p-button label="Create album" (onClick)="createAlbum()" [disabled]="!newAlbum.trim()" />
        @if (selectedAlbum()) {
          <p-button label="Rename album" [text]="true" icon="pi pi-pencil" (onClick)="openRenameAlbum()" />
          <p-button label="Delete album" [text]="true" severity="danger" icon="pi pi-trash" (onClick)="deleteSelectedAlbum()" />
        }
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
        <p-card
          [class.selected]="isSelected(file.id)"
          (click)="onCardClick($event, file)"
          (contextmenu)="onContextMenu($event, file)"
        >
          <div class="thumb">
            <p-checkbox
              class="pick"
              [binary]="true"
              [ngModel]="isSelected(file.id)"
              (onChange)="toggle(file.id); $event.originalEvent?.stopPropagation()"
              (click)="$event.stopPropagation()"
            />
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
            <a [routerLink]="['/player', file.id]" (click)="$event.stopPropagation()">{{ file.name }}</a>
            <p-tag [value]="kindLabel(file.media_kind)" />
          </div>
          <p class="size">{{ formatSize(file.size) }}</p>
          @if (trashMode()) {
            <p class="size">Deletes {{ file.purge_at ? formatDate(file.purge_at) : 'in 30 days' }}</p>
            <div class="actions">
              <p-button label="Restore" icon="pi pi-replay" [text]="true" (onClick)="restore(file); $event.stopPropagation()" />
              <p-button
                label="Delete forever"
                icon="pi pi-times"
                severity="danger"
                [text]="true"
                (onClick)="purge(file); $event.stopPropagation()"
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

    <p-dialog
      header="Rename"
      [(visible)]="renameOpen"
      [modal]="true"
      [style]="{ width: 'min(28rem, 100vw)' }"
      [breakpoints]="{ '640px': '100vw' }"
    >
      <input pInputText class="full" [(ngModel)]="renameValue" (keydown.enter)="confirmRename()" />
      <ng-template #footer>
        <p-button label="Cancel" [text]="true" (onClick)="renameOpen = false" />
        <p-button label="Save" (onClick)="confirmRename()" [disabled]="!renameValue.trim()" />
      </ng-template>
    </p-dialog>

    <p-dialog
      header="Move to album"
      [(visible)]="moveOpen"
      [modal]="true"
      [style]="{ width: 'min(28rem, 100vw)' }"
      [breakpoints]="{ '640px': '100vw' }"
    >
      <p-select
        class="full"
        [options]="moveAlbumOptions()"
        [(ngModel)]="moveAlbumId"
        optionLabel="label"
        optionValue="value"
        placeholder="No album"
        [showClear]="true"
      />
      <div class="inline-create">
        <input pInputText placeholder="Or create album" [(ngModel)]="moveNewAlbum" />
        <p-button label="Create" [outlined]="true" (onClick)="createAlbumFromMove()" [disabled]="!moveNewAlbum.trim()" />
      </div>
      <ng-template #footer>
        <p-button label="Cancel" [text]="true" (onClick)="moveOpen = false" />
        <p-button label="Move" (onClick)="confirmMove()" />
      </ng-template>
    </p-dialog>
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
    .thumb { height: 140px; display: grid; place-items: center; overflow: hidden; background: var(--p-surface-100); position: relative; }
    .thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; }
    .pick { position: absolute; top: 0.5rem; left: 0.5rem; z-index: 1; }
    .meta { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin: 0.5rem 0; }
    .size, .empty, .error, .ok { padding: 0 1rem; color: var(--p-text-muted-color); }
    .error { color: var(--p-red-500); }
    .ok { color: var(--p-green-600); }
    .actions { display: flex; flex-wrap: wrap; gap: 0.25rem; margin-top: 0.35rem; }
    :host ::ng-deep .selected.p-card { outline: 2px solid var(--p-primary-color); }
    .full { width: 100%; }
    .inline-create { display: flex; gap: 0.5rem; margin-top: 0.75rem; flex-wrap: wrap; }
  `,
})
export class LibraryPage {
  @ViewChild('cm') contextMenu?: ContextMenu;

  files = signal<MediaFile[]>([]);
  albums = signal<Album[]>([]);
  selectedAlbum = signal<string | null>(null);
  selectedIds = signal<Set<string>>(new Set());
  trashMode = signal(false);
  dragOver = signal(false);
  menuItems = signal<MenuItem[]>([]);
  newAlbum = '';
  error = signal<string | null>(null);
  ok = signal<string | null>(null);
  silo = signal<LibrarySilo>('photos');
  renameOpen = false;
  renameValue = '';
  renameAlbumId: string | null = null;
  moveOpen = false;
  moveAlbumId: string | null = null;
  moveNewAlbum = '';

  copy = computed(() => SILO_COPY[this.silo()]);
  albumOptions = computed(() => this.albums().map((a) => ({ label: a.name, value: a.id })));
  albumFilterOptions = computed(() => [{ label: 'All albums', value: null }, ...this.albumOptions()]);
  moveAlbumOptions = computed(() => this.albumOptions());
  visible = computed(() => filesInAlbum(this.files(), this.selectedAlbum()));
  selectedCount = computed(() => this.selectedIds().size);
  selectedFiles = computed(() => this.files().filter((file) => this.selectedIds().has(file.id)));

  constructor(
    route: ActivatedRoute,
    private readonly router: Router,
    @Inject(LIST_LIBRARY) private readonly listLibrary: ListLibrary,
    @Inject(UPLOAD_MEDIA) private readonly uploadMedia: UploadMedia,
    @Inject(CREATE_ALBUM) private readonly createAlbumUseCase: CreateAlbum,
    @Inject(RENAME_ALBUM) private readonly renameAlbumUseCase: RenameAlbum,
    @Inject(DELETE_ALBUM) private readonly deleteAlbumUseCase: DeleteAlbum,
    @Inject(ASSIGN_FILE_ALBUM) private readonly assignAlbum: AssignFileAlbum,
    @Inject(RENAME_MEDIA) private readonly renameMedia: RenameMedia,
    @Inject(SHARE_MEDIA) private readonly shareMedia: ShareMedia,
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

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.selectedCount()) {
      this.clearSelection();
    }
  }

  isSelected(id: string) {
    return this.selectedIds().has(id);
  }

  toggle(id: string) {
    const next = new Set(this.selectedIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selectedIds.set(next);
  }

  clearSelection() {
    this.selectedIds.set(new Set());
  }

  onCardClick(event: MouseEvent, file: MediaFile) {
    if ((event.target as HTMLElement | null)?.closest('a, button, .p-checkbox, input')) {
      return;
    }
    if (event.ctrlKey || event.metaKey || this.selectedCount()) {
      event.preventDefault();
      this.toggle(file.id);
      return;
    }
    void this.router.navigate(['/player', file.id]);
  }

  onContextMenu(event: MouseEvent, file: MediaFile) {
    event.preventDefault();
    if (!this.isSelected(file.id)) {
      this.selectedIds.set(new Set([file.id]));
    }
    this.menuItems.set(this.buildMenuItems());
    this.contextMenu?.show(event);
  }

  openMenuForSelection(event: Event) {
    this.menuItems.set(this.buildMenuItems());
    this.contextMenu?.show(event);
  }

  private buildMenuItems(): MenuItem[] {
    const count = this.selectedCount();
    const trash = this.trashMode();
    if (!count) {
      return [];
    }
    if (trash) {
      return [
        { label: 'Restore', icon: 'pi pi-replay', command: () => void this.bulkRestore() },
        { label: 'Delete forever', icon: 'pi pi-times', command: () => void this.bulkPurge() },
      ];
    }
    const items: MenuItem[] = [];
    if (count === 1) {
      items.push({ label: 'Rename', icon: 'pi pi-pencil', command: () => this.openRenameFile() });
    }
    items.push(
      { label: 'Move / album', icon: 'pi pi-folder', command: () => this.openMove() },
      { label: 'Share', icon: 'pi pi-share-alt', command: () => void this.shareSelected() },
      { label: 'Delete', icon: 'pi pi-trash', command: () => void this.bulkTrash() },
    );
    return items;
  }

  async reload() {
    try {
      const data = await this.listLibrary.execute(this.silo(), this.trashMode());
      this.files.set(data.files);
      this.albums.set(data.albums);
      const ids = new Set(data.files.map((file) => file.id));
      this.selectedIds.set(new Set([...this.selectedIds()].filter((id) => ids.has(id))));
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
    this.clearSelection();
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
      const album = await this.createAlbumUseCase.execute(this.newAlbum, this.silo());
      this.newAlbum = '';
      this.selectedAlbum.set(album.id);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  openRenameAlbum() {
    const album = this.albums().find((item) => item.id === this.selectedAlbum());
    if (!album) {
      return;
    }
    this.renameAlbumId = album.id;
    this.renameValue = album.name;
    this.renameOpen = true;
  }

  openRenameFile() {
    const file = this.selectedFiles()[0];
    if (!file) {
      return;
    }
    this.renameAlbumId = null;
    this.renameValue = file.name;
    this.renameOpen = true;
  }

  async confirmRename() {
    const name = this.renameValue.trim();
    if (!name) {
      return;
    }
    try {
      if (this.renameAlbumId) {
        await this.renameAlbumUseCase.execute(this.renameAlbumId, name);
      } else {
        const file = this.selectedFiles()[0];
        if (file) {
          await this.renameMedia.execute(file.id, name);
        }
      }
      this.renameOpen = false;
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async deleteSelectedAlbum() {
    const album = this.albums().find((item) => item.id === this.selectedAlbum());
    if (!album || !confirm(`Delete album “${album.name}”? Files stay in the library.`)) {
      return;
    }
    try {
      await this.deleteAlbumUseCase.execute(album.id);
      this.selectedAlbum.set(null);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  openMove() {
    const current = this.selectedFiles()[0]?.album_id ?? this.selectedAlbum();
    this.moveAlbumId = current;
    this.moveNewAlbum = '';
    this.moveOpen = true;
  }

  async createAlbumFromMove() {
    try {
      const album = await this.createAlbumUseCase.execute(this.moveNewAlbum, this.silo());
      this.moveNewAlbum = '';
      this.moveAlbumId = album.id;
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async confirmMove() {
    try {
      for (const file of this.selectedFiles()) {
        await this.assignAlbum.execute(file.id, this.moveAlbumId);
      }
      this.moveOpen = false;
      this.ok.set(`Updated ${this.selectedCount()} item${this.selectedCount() === 1 ? '' : 's'}.`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async shareSelected() {
    try {
      const payloads = await this.shareMedia.execute(this.selectedFiles());
      const nav = navigator as Navigator & {
        share?: (data: ShareData) => Promise<void>;
        canShare?: (data: ShareData) => boolean;
      };
      const files = payloads.map(
        (item) => new File([item.blob], item.name, { type: item.mime || item.blob.type || 'application/octet-stream' }),
      );
      const data: ShareData = files.length === 1 ? { files, title: files[0].name } : { files };
      if (nav.share && (!nav.canShare || nav.canShare(data))) {
        await nav.share(data);
        return;
      }
      for (const file of files) {
        const url = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = url;
        link.download = file.name;
        link.click();
        URL.revokeObjectURL(url);
      }
      this.ok.set(`Prepared ${files.length} file${files.length === 1 ? '' : 's'} to share.`);
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async bulkTrash() {
    try {
      for (const file of this.selectedFiles()) {
        await this.trashMedia.execute(file.id);
      }
      this.ok.set(`Moved ${this.selectedCount()} item${this.selectedCount() === 1 ? '' : 's'} to trash.`);
      this.clearSelection();
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async bulkRestore() {
    try {
      for (const file of this.selectedFiles()) {
        await this.restoreMedia.execute(file.id);
      }
      this.clearSelection();
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async bulkPurge() {
    if (!confirm('Permanently delete the selected items? This cannot be undone.')) {
      return;
    }
    try {
      for (const file of this.selectedFiles()) {
        await this.purgeMedia.execute(file.id);
      }
      this.clearSelection();
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
