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
            <p-button label="Edit rules" (onClick)="edit(device)" />
          </p-card>
        }
      </div>

      @if (editing(); as profile) {
        <p-card header="Sync rules">
          <div class="stack-form">
            <div class="field">
              <label for="profile-name">Profile name</label>
              <input id="profile-name" pInputText [(ngModel)]="profileName" />
            </div>
            @for (rule of rules(); track rule.media_kind) {
              <div class="rule">
                <strong class="rule-kind">{{ rule.media_kind }}</strong>
                <label class="check-row"><p-checkbox [(ngModel)]="rule.include_all" [binary]="true" /> Include all</label>
                <label class="field">Max age (days)
                  <p-inputnumber [(ngModel)]="rule.max_age_days" [min]="0" [showButtons]="true" />
                </label>
                <label class="field">Max size (bytes)
                  <p-inputnumber [(ngModel)]="rule.max_size_bytes" [min]="0" [showButtons]="true" />
                </label>
              </div>
            }
            <p-button label="Save profile" (onClick)="save(profile.device_id)" />
          </div>
        </p-card>
      }
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
