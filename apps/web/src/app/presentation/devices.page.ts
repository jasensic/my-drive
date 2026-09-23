import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Checkbox } from 'primeng/checkbox';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import {
  LIST_DEVICES,
  LOAD_SYNC_PROFILE,
  REGISTER_DEVICE,
  SAVE_SYNC_PROFILE,
} from '../application/use-cases.tokens';
import type {
  ListDevices,
  LoadSyncProfile,
  RegisterDevice,
  SaveSyncProfile,
} from '../application/use-cases.tokens';
import { Device, MediaKind, SyncProfile, SyncRule } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-devices-page',
  imports: [FormsModule, Button, Card, Checkbox, InputNumber, InputText, LucideDynamicIcon],
  template: `
    <section class="page">
      <header class="page-head">
        <div class="page-intro">
          <h1 class="page-title">
            <svg lucideIcon="monitor-smartphone" [size]="22" aria-hidden="true" />
            Devices &amp; sync profiles
          </h1>
        </div>
        <div class="page-actions">
          <input pInputText placeholder="Phone A" [(ngModel)]="newName" />
          <p-button label="Register device" (onClick)="register()" />
        </div>
      </header>

      @if (error()) {
        <p class="banner error">{{ error() }}</p>
      }

      @if (!devices().length && !error()) {
        <div class="empty-state">
          <svg lucideIcon="monitor-smartphone" [size]="28" aria-hidden="true" />
          <p>No devices registered yet.</p>
        </div>
      }

      <div class="card-list">
        @for (device of devices(); track device.id) {
          <p-card [header]="device.name" [subheader]="device.last_sync_at ? 'Last sync ' + device.last_sync_at : 'Never synced'">
            @if (editing()?.device_id === device.id) {
              <div class="stack-form">
                <h2 class="section-title">Sync rules</h2>
                <div class="field">
                  <label for="profile-name">Profile name</label>
                  <input id="profile-name" pInputText [(ngModel)]="profileName" />
                </div>
                @for (rule of rules(); track rule.media_kind) {
                  <div class="rule">
                    <div class="rule-head">
                      <strong class="rule-kind">{{ kindLabel(rule.media_kind) }}</strong>
                      <label class="check-row">
                        <p-checkbox
                          [ngModel]="rule.include_all"
                          (ngModelChange)="patchRule(rule.media_kind, { include_all: $event })"
                          [binary]="true"
                        />
                        Include all
                      </label>
                    </div>
                    <div class="rule-fields">
                      <label class="field">Max age (days)
                        <p-inputnumber
                          [ngModel]="rule.max_age_days"
                          (ngModelChange)="patchRule(rule.media_kind, { max_age_days: $event })"
                          [min]="0"
                          [useGrouping]="false"
                          [showButtons]="true"
                          placeholder="No limit"
                        />
                      </label>
                      <label class="field">Max size (MB)
                        <p-inputnumber
                          [ngModel]="bytesToMb(rule.max_size_bytes)"
                          (ngModelChange)="patchMaxSizeMb(rule.media_kind, $event)"
                          [min]="0"
                          [useGrouping]="false"
                          [showButtons]="true"
                          placeholder="No limit"
                        />
                      </label>
                    </div>
                  </div>
                }
                <div class="actions">
                  <p-button label="Save profile" (onClick)="save(device.id)" />
                  <p-button label="Cancel" [text]="true" (onClick)="cancel()" />
                </div>
              </div>
            } @else {
              <p-button label="Edit rules" (onClick)="edit(device)" />
            }
          </p-card>
        }
      </div>
    </section>
  `,
})
export class DevicesPage {
  devices = signal<Device[]>([]);
  editing = signal<SyncProfile | null>(null);
  rules = signal<SyncRule[]>([]);
  profileName = '';
  newName = '';
  error = signal<string | null>(null);

  constructor(
    @Inject(LIST_DEVICES) private readonly listDevices: ListDevices,
    @Inject(REGISTER_DEVICE) private readonly registerDevice: RegisterDevice,
    @Inject(LOAD_SYNC_PROFILE) private readonly loadProfile: LoadSyncProfile,
    @Inject(SAVE_SYNC_PROFILE) private readonly saveProfile: SaveSyncProfile,
  ) {
    void this.reload();
  }

  async reload() {
    try {
      this.devices.set(await this.listDevices.execute());
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async register() {
    try {
      await this.registerDevice.execute(this.newName);
      this.newName = '';
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async edit(device: Device) {
    try {
      const profile = await this.loadProfile.execute(device.id);
      this.editing.set(profile);
      this.profileName = profile.name;
      this.rules.set(ensureKinds(profile.rules));
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  kindLabel(kind: MediaKind) {
    switch (kind) {
      case 'photo':
        return 'Photos';
      case 'video':
        return 'Videos';
      case 'audio':
        return 'Audio';
      default:
        return 'Files';
    }
  }

  bytesToMb(bytes: number | null) {
    if (bytes == null) {
      return null;
    }
    return bytes / MB;
  }

  patchMaxSizeMb(kind: MediaKind, mb: number | null) {
    this.patchRule(kind, { max_size_bytes: mb == null ? null : Math.round(mb * MB) });
  }

  patchRule(kind: MediaKind, patch: Partial<SyncRule>) {
    this.rules.update((current) =>
      current.map((rule) => (rule.media_kind === kind ? { ...rule, ...patch } : rule)),
    );
  }

  cancel() {
    this.editing.set(null);
    this.rules.set([]);
    this.profileName = '';
  }

  async save(deviceId: string) {
    try {
      await this.saveProfile.execute(deviceId, this.profileName, this.rules());
      this.cancel();
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }
}

const MB = 1024 * 1024;

function ensureKinds(rules: SyncRule[]): SyncRule[] {
  const kinds: MediaKind[] = ['photo', 'video', 'audio', 'other'];
  return kinds.map(
    (kind) =>
      rules.find((r) => r.media_kind === kind) ?? {
        media_kind: kind,
        max_age_days: kind === 'photo' ? 365 : null,
        max_size_bytes: kind === 'video' ? 10 * MB : null,
        include_all: kind === 'audio' || kind === 'other',
      },
  );
}
