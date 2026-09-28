import { Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { matchLibraryFiles } from '../application/library.use-cases';
import { SEARCH_LIBRARY } from '../application/use-cases.tokens';
import type { SearchLibrary } from '../application/use-cases.tokens';
import { MediaFile, MediaKind, siloForKind } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-file-search',
  imports: [FormsModule, Dialog, InputText, LucideDynamicIcon],
  template: `
    <p-dialog
      header="Search files"
      [visible]="open()"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [dismissableMask]="true"
      styleClass="file-search-dialog"
      [style]="{ width: 'min(36rem, 100vw)' }"
      [breakpoints]="{ '640px': '100vw' }"
      (onShow)="focusQuery()"
    >
      <div class="file-search">
        <div class="file-search-field">
          <svg lucideIcon="search" aria-hidden="true" />
          <input
            #queryInput
            pInputText
            class="full"
            name="file-search"
            placeholder="Search by file name"
            [ngModel]="query()"
            (ngModelChange)="onQuery($event)"
            (keydown)="onInputKey($event)"
            role="combobox"
            aria-autocomplete="list"
            aria-controls="file-search-results"
            [attr.aria-expanded]="true"
            [attr.aria-activedescendant]="activeOptionId()"
            autocomplete="off"
          />
        </div>
        @if (error()) {
          <p class="banner error">{{ error() }}</p>
        } @else if (loading()) {
          <p class="hint">Loading library…</p>
        } @else if (!results().length) {
          <p class="hint">{{ query().trim() ? 'No matching files.' : 'No files in the library yet.' }}</p>
        } @else {
          <ul id="file-search-results" class="file-search-results" role="listbox">
            @for (file of results(); track file.id; let i = $index) {
              <li
                role="option"
                [id]="'file-search-' + file.id"
                [attr.aria-selected]="i === activeIndex()"
              >
                <button
                  type="button"
                  class="file-search-hit"
                  [class.active]="i === activeIndex()"
                  (click)="openFile(file)"
                  (mouseenter)="activeIndex.set(i)"
                >
                  <svg [lucideIcon]="kindIcon(file.media_kind)" aria-hidden="true" />
                  <span class="file-search-name">{{ file.name }}</span>
                  <span class="file-search-silo">{{ siloLabel(file.media_kind) }}</span>
                </button>
              </li>
            }
          </ul>
        }
      </div>
    </p-dialog>
  `,
})
export class FileSearchDialog implements OnInit, OnDestroy {
  @ViewChild('queryInput') queryInput?: ElementRef<HTMLInputElement>;

  open = signal(false);
  query = signal('');
  files = signal<MediaFile[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  activeIndex = signal(0);

  results = computed(() => matchLibraryFiles(this.files(), this.query()));
  activeOptionId = computed(() => {
    const file = this.results()[this.activeIndex()];
    return file ? `file-search-${file.id}` : null;
  });

  private readonly onHotkey = (event: KeyboardEvent) => this.onDocumentKey(event);

  constructor(
    @Inject(SEARCH_LIBRARY) private readonly searchLibrary: SearchLibrary,
    private readonly router: Router,
  ) {}

  ngOnInit() {
    document.addEventListener('keydown', this.onHotkey, true);
  }

  ngOnDestroy() {
    document.removeEventListener('keydown', this.onHotkey, true);
  }

  onDocumentKey(event: KeyboardEvent) {
    if (!this.isSearchHotkey(event)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    if (this.open()) {
      this.close();
    } else {
      void this.show();
    }
  }

  async show() {
    this.open.set(true);
    this.query.set('');
    this.activeIndex.set(0);
    this.error.set(null);
    this.loading.set(true);
    try {
      this.files.set(await this.searchLibrary.execute());
    } catch (err) {
      this.files.set([]);
      this.error.set(extractError(err));
    } finally {
      this.loading.set(false);
    }
  }

  close() {
    this.open.set(false);
  }

  onVisibleChange(visible: boolean) {
    this.open.set(visible);
    if (!visible) {
      this.query.set('');
      this.activeIndex.set(0);
    }
  }

  onQuery(value: string) {
    this.query.set(value);
    this.activeIndex.set(0);
  }

  onInputKey(event: KeyboardEvent) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.move(1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.move(-1);
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      const file = this.results()[this.activeIndex()];
      if (file) {
        this.openFile(file);
      }
    }
  }

  openFile(file: MediaFile) {
    this.close();
    void this.router.navigate(['/player', file.id]);
  }

  focusQuery() {
    setTimeout(() => this.queryInput?.nativeElement.focus(), 50);
  }

  kindIcon(kind: MediaKind): string {
    const silo = siloForKind(kind);
    if (silo === 'music') {
      return 'music';
    }
    if (silo === 'photos') {
      return 'images';
    }
    return 'file';
  }

  siloLabel(kind: MediaKind): string {
    const silo = siloForKind(kind);
    if (silo === 'music') {
      return 'Music';
    }
    if (silo === 'photos') {
      return 'Photos';
    }
    return 'Files';
  }

  private move(delta: number) {
    const count = this.results().length;
    if (!count) {
      return;
    }
    this.activeIndex.set((this.activeIndex() + delta + count) % count);
  }

  private isSearchHotkey(event: KeyboardEvent): boolean {
    return (event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'k';
  }
}
