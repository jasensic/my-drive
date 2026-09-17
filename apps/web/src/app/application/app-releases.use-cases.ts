import { Inject, Injectable } from '@angular/core';
import { AppRelease } from '../domain/models';
import { APP_RELEASE_REPOSITORY, AppReleaseRepository } from '../domain/ports';
import { ListAppReleases, PublishAppRelease } from './use-cases.tokens';

@Injectable()
export class ListAppReleasesService implements ListAppReleases {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(): Promise<AppRelease[]> {
    return this.releases.list();
  }
}

@Injectable()
export class PublishAppReleaseService implements PublishAppRelease {
  constructor(@Inject(APP_RELEASE_REPOSITORY) private readonly releases: AppReleaseRepository) {}
  execute(input: {
    versionCode: number;
    versionName: string;
    changelog: string;
    apk: File;
  }): Promise<AppRelease> {
    if (!Number.isInteger(input.versionCode) || input.versionCode <= 0) {
      return Promise.reject(new Error('version_code must be a positive integer'));
    }
    const versionName = input.versionName.trim();
    if (!versionName) {
      return Promise.reject(new Error('version_name is required'));
    }
    if (!input.apk || !input.apk.name.toLowerCase().endsWith('.apk')) {
      return Promise.reject(new Error('Select an Android APK file'));
    }
    return this.releases.publish({
      versionCode: input.versionCode,
      versionName,
      changelog: input.changelog,
      apk: input.apk,
    });
  }
}
