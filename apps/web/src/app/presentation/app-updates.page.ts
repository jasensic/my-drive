import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Toolbar } from 'primeng/toolbar';
import { LIST_APP_RELEASES, PUBLISH_APP_RELEASE } from '../application/use-cases.tokens';
import type { ListAppReleases, PublishAppRelease } from '../application/use-cases.tokens';
import { AppRelease } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-updates-page',
  imports: [FormsModule, Button, Card, InputNumber, InputText, Toolbar],
  template: `
    <p-toolbar>
      <ng-template #start><strong>Android app updates</strong></ng-template>
    </p-toolbar>

    <p class="hint">
      Publish a signed APK here. Phones on the LAN check this catalog after they connect and offer
      an in-app update when <code>versionCode</code> is higher than the installed app.
    </p>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (ok()) {
      <p class="ok">{{ ok() }}</p>
    }

    <p-card header="Publish APK">
      <div class="field">
        <label>versionCode</label>
        <p-inputnumber [(ngModel)]="versionCode" [min]="1" [showButtons]="true" />
      </div>
      <div class="field">
        <label>versionName</label>
        <input pInputText [(ngModel)]="versionName" placeholder="0.2.0" />
      </div>
      <div class="field">
        <label>Changelog</label>
        <textarea [(ngModel)]="changelog" rows="3"></textarea>
      </div>
      <div class="field">
        <input #picker type="file" accept=".apk,application/vnd.android.package-archive" hidden
          (change)="onFile(picker.files); picker.value = ''" />
        <p-button label="Choose APK" icon="pi pi-android" (onClick)="picker.click()" />
        <span>{{ apk?.name || 'No file selected' }}</span>
      </div>
      <p-button label="Publish" icon="pi pi-upload" (onClick)="publish()" [disabled]="!canPublish()" />
    </p-card>

    <div class="list">
      @for (release of releases(); track release.id) {
        <p-card [header]="'v' + release.version_name" [subheader]="'versionCode ' + release.version_code">
          <p>{{ release.changelog || 'No changelog' }}</p>
          <p class="meta">{{ formatSize(release.size) }} · {{ release.published_at }}</p>
        </p-card>
      }
    </div>
  `,
  styles: `
    .hint, .error, .ok, .list, p-card { margin: 1rem; }
    .hint { color: var(--p-text-muted-color); }
    .error { color: var(--p-red-500); }
    .ok { color: var(--p-green-600); }
    .field { display: flex; flex-direction: column; gap: 0.35rem; margin-bottom: 0.75rem; }
    .meta { color: var(--p-text-muted-color); font-size: 0.9rem; }
  `,
})
export class AppUpdatesPage {
  releases = signal<AppRelease[]>([]);
  error = signal<string | null>(null);
  ok = signal<string | null>(null);
  versionCode = 2;
  versionName = '';
  changelog = '';
  apk: File | null = null;

  constructor(
    @Inject(LIST_APP_RELEASES) private readonly listReleases: ListAppReleases,
    @Inject(PUBLISH_APP_RELEASE) private readonly publishRelease: PublishAppRelease,
  ) {
    void this.reload();
  }

  canPublish() {
    return this.versionCode > 0 && this.versionName.trim().length > 0 && this.apk !== null;
  }

  onFile(list: FileList | null) {
    this.apk = list?.item(0) ?? null;
  }

  async reload() {
    try {
      this.releases.set(await this.listReleases.execute());
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async publish() {
    if (!this.apk) {
      return;
    }
    this.error.set(null);
    this.ok.set(null);
    try {
      const published = await this.publishRelease.execute({
        versionCode: this.versionCode,
        versionName: this.versionName,
        changelog: this.changelog,
        apk: this.apk,
      });
      this.ok.set(`Published ${published.version_name} (${published.version_code})`);
      this.apk = null;
      this.versionCode = published.version_code + 1;
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
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
}
