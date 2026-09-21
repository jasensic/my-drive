import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import {
  DELETE_APP_RELEASE,
  INSPECT_APK,
  LIST_APP_RELEASES,
  PUBLISH_APP_RELEASE,
  UPDATE_APP_RELEASE,
} from '../application/use-cases.tokens';
import type {
  DeleteAppRelease,
  InspectApk,
  ListAppReleases,
  PublishAppRelease,
  UpdateAppRelease,
} from '../application/use-cases.tokens';
import { AppRelease } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-updates-page',
  imports: [FormsModule, Button, Card, LucideDynamicIcon],
  template: `
    <section class="page">
      <header class="page-head">
        <div class="page-intro">
          <h1 class="page-title">
            <svg lucideIcon="package" [size]="22" aria-hidden="true" />
            Android app updates
          </h1>
          <p class="page-lead">
            Attach an APK. The portal reads <code>versionCode</code> and <code>versionName</code> from the
            file (the same values Android installs). Phones update when that code is higher than the app
            they already have.
          </p>
        </div>
      </header>

      @if (error()) {
        <p class="banner error">{{ error() }}</p>
      }
      @if (ok()) {
        <p class="banner ok">{{ ok() }}</p>
      }

      <p-card header="Publish APK">
        <div class="stack-form">
          <div class="field">
            <label for="changelog">Changelog</label>
            <textarea id="changelog" [(ngModel)]="changelog" rows="3"></textarea>
          </div>
          <div class="page-actions">
            <input #picker type="file" accept=".apk,application/vnd.android.package-archive" hidden
              (change)="onFile(picker.files); picker.value = ''" />
            <p-button label="Choose APK" (onClick)="picker.click()">
              <ng-template #icon><svg lucideIcon="smartphone" aria-hidden="true" /></ng-template>
            </p-button>
            <span class="caption">{{ apk?.name || 'No file selected' }}</span>
          </div>
          @if (inspecting()) {
            <p class="caption">Reading version from APK…</p>
          } @else if (identity()) {
            <p class="caption">Detected v{{ identity()!.version_name }} (versionCode {{ identity()!.version_code }})</p>
          }
          <p-button label="Publish" (onClick)="publish()" [disabled]="!canPublish()">
            <ng-template #icon><svg lucideIcon="upload" aria-hidden="true" /></ng-template>
          </p-button>
        </div>
      </p-card>

      @if (!releases().length && !error()) {
        <div class="empty-state">
          <svg lucideIcon="package" [size]="28" aria-hidden="true" />
          <p>No app releases published yet.</p>
        </div>
      }

      <div class="card-list">
        @for (release of releases(); track release.id) {
          <p-card [header]="'v' + release.version_name" [subheader]="'versionCode ' + release.version_code">
            <div class="stack-form">
              <div class="field">
                <label [attr.for]="'changelog-' + release.id">Changelog</label>
                <textarea
                  [id]="'changelog-' + release.id"
                  [ngModel]="drafts()[release.id] ?? release.changelog"
                  (ngModelChange)="setDraft(release.id, $event)"
                  rows="3"
                ></textarea>
              </div>
              <p class="caption">{{ formatSize(release.size) }} · {{ release.published_at }}</p>
              <div class="actions">
                <p-button label="Save" [text]="true" (onClick)="save(release)">
                  <ng-template #icon><svg lucideIcon="save" aria-hidden="true" /></ng-template>
                </p-button>
                <input
                  #replace
                  type="file"
                  accept=".apk,application/vnd.android.package-archive"
                  hidden
                  (change)="replaceApk(release, replace.files); replace.value = ''"
                />
                <p-button label="Replace APK" [text]="true" (onClick)="replace.click()">
                  <ng-template #icon><svg lucideIcon="upload" aria-hidden="true" /></ng-template>
                </p-button>
                <p-button label="Delete" severity="danger" [text]="true" (onClick)="remove(release)">
                  <ng-template #icon><svg lucideIcon="trash-2" aria-hidden="true" /></ng-template>
                </p-button>
              </div>
            </div>
          </p-card>
        }
      </div>
    </section>
  `,
})
export class AppUpdatesPage {
  releases = signal<AppRelease[]>([]);
  error = signal<string | null>(null);
  ok = signal<string | null>(null);
  inspecting = signal(false);
  identity = signal<{ version_code: number; version_name: string } | null>(null);
  drafts = signal<Record<string, string>>({});
  changelog = '';
  apk: File | null = null;

  constructor(
    @Inject(LIST_APP_RELEASES) private readonly listReleases: ListAppReleases,
    @Inject(INSPECT_APK) private readonly inspectApk: InspectApk,
    @Inject(PUBLISH_APP_RELEASE) private readonly publishRelease: PublishAppRelease,
    @Inject(UPDATE_APP_RELEASE) private readonly updateRelease: UpdateAppRelease,
    @Inject(DELETE_APP_RELEASE) private readonly deleteRelease: DeleteAppRelease,
  ) {
    void this.reload();
  }

  canPublish() {
    return this.apk !== null && this.identity() !== null && !this.inspecting();
  }

  setDraft(id: string, value: string) {
    this.drafts.update((current) => ({ ...current, [id]: value }));
  }

  async onFile(list: FileList | null) {
    const file = list?.item(0) ?? null;
    this.apk = file;
    this.identity.set(null);
    this.error.set(null);
    if (!file) {
      return;
    }
    this.inspecting.set(true);
    try {
      this.identity.set(await this.inspectApk.execute(file));
    } catch (err) {
      this.apk = null;
      this.error.set(extractError(err));
    } finally {
      this.inspecting.set(false);
    }
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
        changelog: this.changelog,
        apk: this.apk,
      });
      this.ok.set(`Published ${published.version_name} (${published.version_code})`);
      this.apk = null;
      this.identity.set(null);
      this.changelog = '';
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async save(release: AppRelease) {
    this.error.set(null);
    this.ok.set(null);
    try {
      const changelog = this.drafts()[release.id] ?? release.changelog;
      await this.updateRelease.execute(release.id, { changelog });
      this.ok.set(`Updated ${release.version_name}`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async replaceApk(release: AppRelease, list: FileList | null) {
    const apk = list?.item(0);
    if (!apk) {
      return;
    }
    this.error.set(null);
    this.ok.set(null);
    try {
      const updated = await this.updateRelease.execute(release.id, {
        changelog: this.drafts()[release.id] ?? release.changelog,
        apk,
      });
      this.ok.set(`Replaced APK with ${updated.version_name} (${updated.version_code})`);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async remove(release: AppRelease) {
    if (!confirm(`Delete v${release.version_name} (versionCode ${release.version_code})?`)) {
      return;
    }
    this.error.set(null);
    this.ok.set(null);
    try {
      await this.deleteRelease.execute(release.id);
      this.ok.set(`Deleted ${release.version_name}`);
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
