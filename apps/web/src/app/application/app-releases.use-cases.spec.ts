import { PublishAppReleaseService } from './app-releases.use-cases';

describe('PublishAppReleaseService', () => {
  it('rejects non-apk files before calling the repository', async () => {
    const repo = { publish: async () => { throw new Error('should not be called'); } };
    const svc = new PublishAppReleaseService(repo as never);
    await expect(
      svc.execute({
        versionCode: 2,
        versionName: '0.2.0',
        changelog: '',
        apk: new File(['x'], 'notes.txt'),
      }),
    ).rejects.toThrow(/APK/i);
  });

  it('rejects invalid version codes', async () => {
    const svc = new PublishAppReleaseService({ publish: async () => ({}) } as never);
    await expect(
      svc.execute({
        versionCode: 0,
        versionName: '0.2.0',
        changelog: '',
        apk: new File(['x'], 'app.apk'),
      }),
    ).rejects.toThrow(/version_code/);
  });
});
