import { PublishAppReleaseService } from './app-releases.use-cases';

describe('PublishAppReleaseService', () => {
  it('rejects non-apk files before calling the repository', async () => {
    const repo = { publish: async () => { throw new Error('should not be called'); } };
    const svc = new PublishAppReleaseService(repo as never);
    await expect(
      svc.execute({
        changelog: '',
        apk: new File(['x'], 'notes.txt'),
      }),
    ).rejects.toThrow(/APK/i);
  });
});
