import { Inject, Injectable } from '@angular/core';
import { AppRelease } from '../domain/models';
import { APP_RELEASE_REPOSITORY, AppReleaseRepository } from '../domain/ports';
import {
  DeleteAppRelease,
  InspectApk,
  ListAppReleases,
  PublishAppRelease,
  UpdateAppRelease,
} from './use-cases.tokens';

@Injectable()
export class ListAppReleasesService implements ListAppReleases {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(): Promise<AppRelease[]> {
    return this.releases.list();
  }
}

@Injectable()
export class InspectApkService implements InspectApk {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(apk: File): Promise<{ version_code: number; version_name: string }> {
    if (!apk || !apk.name.toLowerCase().endsWith('.apk')) {
      return Promise.reject(new Error('Select an Android APK file'));
    }
    return this.releases.inspect(apk);
  }
}

@Injectable()
export class PublishAppReleaseService implements PublishAppRelease {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(input: { changelog: string; apk: File }): Promise<AppRelease> {
    if (!input.apk || !input.apk.name.toLowerCase().endsWith('.apk')) {
      return Promise.reject(new Error('Select an Android APK file'));
    }
    return this.releases.publish({ changelog: input.changelog, apk: input.apk });
  }
}

@Injectable()
export class UpdateAppReleaseService implements UpdateAppRelease {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(id: string, input: { changelog?: string; apk?: File }): Promise<AppRelease> {
    if (input.apk && !input.apk.name.toLowerCase().endsWith('.apk')) {
      return Promise.reject(new Error('Select an Android APK file'));
    }
    if (input.changelog === undefined && !input.apk) {
      return Promise.reject(new Error('changelog or apk is required'));
    }
    return this.releases.update(id, input);
  }
}

@Injectable()
export class DeleteAppReleaseService implements DeleteAppRelease {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(id: string): Promise<void> {
    return this.releases.remove(id);
  }
}
