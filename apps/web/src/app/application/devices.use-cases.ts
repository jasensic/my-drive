import { Inject, Injectable } from '@angular/core';
import { Device, SyncProfile, SyncRule } from '../domain/models';
import { DEVICE_REPOSITORY, DeviceRepository } from '../domain/ports';
import { ListDevices, LoadSyncProfile, RegisterDevice, SaveSyncProfile } from './use-cases.tokens';

@Injectable()
export class ListDevicesService implements ListDevices {
  constructor(@Inject(DEVICE_REPOSITORY) private readonly devices: DeviceRepository) {}
  execute(): Promise<Device[]> {
    return this.devices.list();
  }
}

@Injectable()
export class RegisterDeviceService implements RegisterDevice {
  constructor(@Inject(DEVICE_REPOSITORY) private readonly devices: DeviceRepository) {}
  execute(name: string): Promise<Device> {
    const trimmed = name.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Device name is required'));
    }
    return this.devices.register(trimmed);
  }
}

@Injectable()
export class LoadSyncProfileService implements LoadSyncProfile {
  constructor(@Inject(DEVICE_REPOSITORY) private readonly devices: DeviceRepository) {}
  execute(deviceId: string): Promise<SyncProfile> {
    return this.devices.getProfile(deviceId);
  }
}

@Injectable()
export class SaveSyncProfileService implements SaveSyncProfile {
  constructor(@Inject(DEVICE_REPOSITORY) private readonly devices: DeviceRepository) {}
  execute(deviceId: string, name: string, rules: SyncRule[]): Promise<SyncProfile> {
    return this.devices.saveProfile(deviceId, name, rules);
  }
}
