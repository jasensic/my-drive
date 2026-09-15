import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { Checkbox } from 'primeng/checkbox';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Toolbar } from 'primeng/toolbar';
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
  imports: [FormsModule, Button, Card, Checkbox, InputNumber, InputText, Toolbar],
  template: `
    <p-toolbar>
      <ng-template #start><strong>Devices &amp; sync profiles</strong></ng-template>
      <ng-template #end>
        <input pInputText placeholder="Phone A" [(ngModel)]="newName" />
        <p-button label="Register device" (onClick)="register()" />
      </ng-template>
    </p-toolbar>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <div class="list">
      @for (device of devices(); track device.id) {
        <p-card [header]="device.name" [subheader]="device.last_sync_at ? 'Last sync ' + device.last_sync_at : 'Never synced'">
          <p-button label="Edit rules" (onClick)="edit(device)" />
        </p-card>
      }
    </div>

    @if (editing(); as profile) {
      <p-card header="Sync rules">
        <div class="field">
          <label>Profile name</label>
          <input pInputText [(ngModel)]="profileName" />
        </div>
        @for (rule of rules(); track rule.media_kind; let i = $index) {
          <div class="rule">
            <strong>{{ rule.media_kind }}</strong>
            <label><p-checkbox [(ngModel)]="rule.include_all" [binary]="true" /> Include all</label>
            <label>Max age (days)
              <p-inputnumber [(ngModel)]="rule.max_age_days" [min]="0" [showButtons]="true" />
            </label>
            <label>Max size (bytes)
              <p-inputnumber [(ngModel)]="rule.max_size_bytes" [min]="0" [showButtons]="true" />
            </label>
          </div>
        }
        <p-button label="Save profile" (onClick)="save(profile.device_id)" />
      </p-card>
    }
  `,
  styles: `
    .list, p-card { margin: 1rem; }
    .field, .rule { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; margin-bottom: 0.75rem; }
    .error { color: var(--p-red-500); padding: 0 1rem; }
    p-toolbar input { margin-right: 0.5rem; }
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

  async save(deviceId: string) {
    try {
      await this.saveProfile.execute(deviceId, this.profileName, this.rules());
      this.editing.set(null);
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    }
  }
}

function ensureKinds(rules: SyncRule[]): SyncRule[] {
  const kinds: MediaKind[] = ['photo', 'video', 'audio'];
  return kinds.map(
    (kind) =>
      rules.find((r) => r.media_kind === kind) ?? {
        media_kind: kind,
        max_age_days: kind === 'photo' ? 365 : null,
        max_size_bytes: kind === 'video' ? 10 * 1024 * 1024 : null,
        include_all: kind === 'audio',
      },
  );
}
